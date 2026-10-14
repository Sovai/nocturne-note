package com.example.tts

import android.app.ActivityManager
import android.content.Context
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/**
 * Heavy OmniVoice JNI/ONNX runs in :omnivoice, outside note-editor process.
 * A native crash or low-memory kill can then fail the request safely.
 * Binder messages are tiny; PCM passes through app-private cache files.
 */
class OmniVoiceWorkerService : Service() {
  companion object {
    const val MSG_SYNTHESIZE = 1
    const val MSG_AUDIO = 2
    const val MSG_ERROR = 3
    const val MSG_PROGRESS = 4
    const val KEY_TEXT = "text"
    const val KEY_FILE = "file"
    const val KEY_ERROR = "error"
    const val KEY_PROGRESS = "progress"
    private const val TAG = "NocturneOmniVoice"
    const val DIRECTORY = "omnivoice-generated"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val serializeInference = Mutex()
  private var engine: OmniVoiceOnDeviceEngine? = null

  private val incoming by lazy {
    Messenger(object : Handler(Looper.getMainLooper()) {
      override fun handleMessage(message: Message) {
        if (message.what != MSG_SYNTHESIZE) {
          super.handleMessage(message)
          return
        }
        val text = message.data.getString(KEY_TEXT, "")
        val receiver = message.replyTo
        val request = message.arg1
        if (receiver == null || text.isBlank() || text.length > 280) {
          reply(receiver, request, MSG_ERROR, KEY_ERROR, "Invalid synthesis request")
          return
        }
        scope.launch {
          serializeInference.withLock {
            try {
              Log.i(TAG, "OmniVoice request $request: preparing local model")
              fun progress(stage: String) {
                Log.i(TAG, "OmniVoice request $request: $stage")
                reply(receiver, request, MSG_PROGRESS, KEY_PROGRESS, stage)
              }
              progress("Preparing OmniVoice model")
              if (engine == null) {
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                check(!memory.lowMemory && memory.availMem >= 1_400_000_000L) {
                  "Not enough available RAM for OmniVoice. Close other apps before reading."
                }
                val dir = File(filesDir, "tts/omnivoice-onnx-int4")
                check(dir.isDirectory) { "The downloaded OmniVoice model was not found" }
                engine = OmniVoiceOnDeviceEngine(dir, threads = 2)
              }
              val audio = requireNotNull(engine).synthesize(text, onProgress = ::progress)
              check(audio.sampleRate == 24_000 && audio.samples.isNotEmpty())
              check(audio.samples.size < 3_000_000) { "Generated audio is too long" }
              val outputDir = File(cacheDir, DIRECTORY).also { it.mkdirs() }
              val filename = UUID.randomUUID().toString() + ".pcm"
              val tmp = File(outputDir, filename + ".part")
              val output = File(outputDir, filename)
              try {
                DataOutputStream(tmp.outputStream().buffered()).use { stream ->
                  stream.writeInt(0x4e4f5445)
                  stream.writeInt(audio.sampleRate)
                  stream.writeInt(audio.samples.size)
                  audio.samples.forEach { stream.writeFloat(it) }
                }
                check(tmp.renameTo(output)) { "Could not write generated speech" }
              } finally {
                tmp.delete()
              }
              reply(receiver, request, MSG_AUDIO, KEY_FILE, filename)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              Log.e(TAG, "OmniVoice failed for request $request", e)
              reply(receiver, request, MSG_ERROR, KEY_ERROR,
                "${e.javaClass.simpleName}: ${e.message.orEmpty().take(250)}")
            }
          }
        }
      }
    })
  }

  private fun reply(receiver: Messenger?, id: Int, code: Int, key: String, value: String) {
    if (receiver == null) return
    runCatching {
      receiver.send(Message.obtain(null, code).apply {
        arg1 = id
        data = Bundle().apply { putString(key, value) }
      })
    }.onFailure { Log.w(TAG, "Client unavailable", it) }
  }

  override fun onBind(intent: Intent?): IBinder = incoming.binder

  override fun onDestroy() {
    scope.cancel()
    // This process only runs speech inference. Unbinding reclaims all its JNI
    // allocations without blocking the UI on an expensive native destructor.
    Process.killProcess(Process.myPid())
    super.onDestroy()
  }
}
