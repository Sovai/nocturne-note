package com.example.tts

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for the separate :omnivoice process. Crash/low-memory kills of native
 * ONNX code are reported to the reader instead of terminating the note editor.
 */
class OmniVoiceRemoteClient(context: Context) : AutoCloseable {
  private val app = context.applicationContext
  private val nextId = AtomicInteger()
  private val pending = ConcurrentHashMap<Int, CompletableDeferred<SpeechAudio>>()
  private val progress = ConcurrentHashMap<Int, (String) -> Unit>()
  private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val ready = CompletableDeferred<Messenger>()
  @Volatile private var bound = false
  @Volatile private var closed = false

  private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
    override fun handleMessage(message: Message) {
      if (message.what == OmniVoiceWorkerService.MSG_PROGRESS) {
        progress[message.arg1]?.invoke(message.data.getString(OmniVoiceWorkerService.KEY_PROGRESS, ""))
        return
      }
      val deferred = pending.remove(message.arg1) ?: return
      progress.remove(message.arg1)
      when (message.what) {
        OmniVoiceWorkerService.MSG_AUDIO -> {
          val name = message.data.getString(OmniVoiceWorkerService.KEY_FILE, "")
          readerScope.launch {
            runCatching { readOutput(name) }
              .onSuccess { deferred.complete(it) }
              .onFailure { deferred.completeExceptionally(it) }
          }
        }
        OmniVoiceWorkerService.MSG_ERROR -> {
          deferred.completeExceptionally(IllegalStateException(
            message.data.getString(OmniVoiceWorkerService.KEY_ERROR)
              ?: "OmniVoice synthesis failed"))
        }
        else -> deferred.completeExceptionally(IllegalStateException("Invalid OmniVoice reply"))
      }
    }
  })

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
      if (!ready.isCompleted) ready.complete(Messenger(binder))
    }
    override fun onServiceDisconnected(name: ComponentName) {
      failAll(describeWorkerExit())
    }
    override fun onBindingDied(name: ComponentName) {
      failAll("OmniVoice process binding was lost")
    }
    override fun onNullBinding(name: ComponentName) {
      failAll("OmniVoice process could not start")
    }
  }

  private fun describeWorkerExit(): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return "OmniVoice engine process stopped. The note editor is still running."
    }
    val exit = runCatching {
      val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      manager.getHistoricalProcessExitReasons(app.packageName, 0, 10)
        .firstOrNull { info ->
          info.processName.endsWith(":omnivoice") &&
            System.currentTimeMillis() - info.timestamp in 0L..120_000L
        }
    }.getOrNull()
    return when (exit?.reason) {
      ApplicationExitInfo.REASON_LOW_MEMORY ->
        "OmniVoice ran out of available RAM. Close other apps and try a shorter passage."
      ApplicationExitInfo.REASON_CRASH_NATIVE ->
        "OmniVoice native engine crashed. Check Android logcat for NocturneOmniVoice."
      ApplicationExitInfo.REASON_ANR ->
        "OmniVoice engine became unresponsive."
      ApplicationExitInfo.REASON_CRASH ->
        "OmniVoice worker crashed. Check Android logcat for NocturneOmniVoice."
      else ->
        "OmniVoice engine exited unexpectedly. Check Android logcat for NocturneOmniVoice."
    }
  }

  private fun failAll(error: String) {
    val exception = IllegalStateException(error)
    if (!ready.isCompleted) ready.completeExceptionally(exception)
    pending.values.forEach { it.completeExceptionally(exception) }
    pending.clear()
    progress.clear()
  }

  private fun readOutput(name: String): SpeechAudio {
    require(name.matches(Regex("[a-f0-9-]{36}[.]pcm"))) {
      "Unsafe OmniVoice PCM filename"
    }
    val file = File(app.cacheDir, OmniVoiceWorkerService.DIRECTORY + "/" + name)
    try {
      check(file.isFile && file.length() in 16L..12_000_012L) {
        "Generated OmniVoice audio file missing"
      }
      DataInputStream(file.inputStream().buffered()).use { stream ->
        check(stream.readInt() == 0x4e4f5445)
        val sampleRate = stream.readInt()
        val size = stream.readInt()
        check(sampleRate in 8000..48000 && size in 1..3_000_000)
        check(file.length() == 12L + size * 4L)
        return SpeechAudio(FloatArray(size) { stream.readFloat() }, sampleRate)
      }
    } finally {
      file.delete()
    }
  }

  suspend fun synthesize(text: String, onProgress: (String) -> Unit = {}): SpeechAudio {
    require(text.isNotBlank() && text.length <= 280) { "Text segment too long for OmniVoice" }
    check(!closed) { "OmniVoice session is closed" }
    // Connection and response handlers execute on Main; closing can occur on IO.
    return withContext(Dispatchers.Main.immediate) {
      if (!bound) {
        bound = app.bindService(
          Intent(app, OmniVoiceWorkerService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "Unable to start offline OmniVoice process" }
      }
      onProgress("Starting OmniVoice")
      val remote = withSpeechDeadline(20_000, "OmniVoice startup") { ready.await() }
      val id = nextId.incrementAndGet()
      val result = CompletableDeferred<SpeechAudio>()
      pending[id] = result
      progress[id] = onProgress
      try {
        remote.send(Message.obtain(null, OmniVoiceWorkerService.MSG_SYNTHESIZE).apply {
          arg1 = id
          replyTo = replies
          data = Bundle().apply { putString(OmniVoiceWorkerService.KEY_TEXT, text) }
        })
        // Low-end devices may take substantially longer than playback to
        // generate audio. Time out instead of leaving the UI spinner forever.
        withSpeechDeadline(180_000, "OmniVoice") { result.await() }
      } catch (e: RemoteException) {
        throw IllegalStateException("Offline OmniVoice process crashed while starting", e)
      } finally {
        pending.remove(id)
        progress.remove(id)
      }
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    failAll("OmniVoice playback stopped")
    readerScope.cancel()
    if (bound) {
      bound = false
      runCatching { app.unbindService(connection) }
    }
  }
}
