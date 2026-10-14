package com.example.tts

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Reuses already synthesized MMS passages when seeking or restarting.
 * Audio files are stored under cacheDir, never backed up, bounded to 64 MiB.
 */
class SpeechChunkCache(context: Context) {
  private val dir = File(context.cacheDir, "nocturne-speech-chunks").also { it.mkdirs() }
  private val limit = 64L * 1024 * 1024

  fun key(text: String, speed: Float, trim: Boolean, engine: String = "mms-khm-v1"): String {
    val bytes = (engine + "|" + speed + "|" + trim + "|" + text)
      .toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes)
      .joinToString("") { "%02x".format(it) }
  }

  @Synchronized fun get(key: String): SpeechAudio? = runCatching {
    val file = File(dir, key + ".pcm")
    if (!file.isFile || file.length() < 12) return null
    DataInputStream(file.inputStream().buffered()).use { input ->
      require(input.readInt() == 0x4e4f5445)
      val rate = input.readInt()
      val size = input.readInt()
      require(rate in 8_000..48_000 && size in 1..3_000_000)
      require(file.length() == 12L + size * 4L)
      val data = FloatArray(size) { input.readFloat() }
      file.setLastModified(System.currentTimeMillis())
      SpeechAudio(data, rate)
    }
  }.getOrNull()

  @Synchronized fun put(key: String, audio: SpeechAudio) {
    if (audio.samples.size > 3_000_000) return
    val part = File(dir, key + ".part")
    val file = File(dir, key + ".pcm")
    try {
      DataOutputStream(part.outputStream().buffered()).use { output ->
        output.writeInt(0x4e4f5445)
        output.writeInt(audio.sampleRate)
        output.writeInt(audio.samples.size)
        audio.samples.forEach { output.writeFloat(it) }
      }
      if (file.exists()) file.delete()
      check(part.renameTo(file))
      prune()
    } finally {
      part.delete()
    }
  }

  private fun prune() {
    val files = dir.listFiles()?.filter { it.isFile && it.extension == "pcm" }
      ?.sortedByDescending { it.lastModified() } ?: return
    var bytes = 0L
    files.forEach { file ->
      bytes += file.length()
      if (bytes > limit) file.delete()
    }
  }
}

/** Trims excess silence at both ends but retains phonetic safety margins. */
fun trimSpeechSilence(audio: SpeechAudio, threshold: Float = 0.0015f,
  marginMs: Int = 65): SpeechAudio {
  if (audio.samples.isEmpty()) return audio
  val samples = audio.samples
  var first = 0
  var last = samples.lastIndex
  while (first < last && kotlin.math.abs(samples[first]) < threshold) first++
  while (last > first && kotlin.math.abs(samples[last]) < threshold) last--
  val margin = (audio.sampleRate * marginMs / 1000).coerceAtLeast(1)
  first = (first - margin).coerceAtLeast(0)
  last = (last + margin).coerceAtMost(samples.lastIndex)
  if (last <= first) return audio
  return SpeechAudio(samples.copyOfRange(first, last + 1), audio.sampleRate)
}
