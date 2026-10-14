package com.example.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SpeechAudio(val samples: FloatArray, val sampleRate: Int)

/** Authenticated request to local OmniVoice PC. No external TTS APIs are used. */
class OmniVoiceClient {
  suspend fun health(settings: SpeechPreferences): Boolean = withContext(Dispatchers.IO) {
    val base = requireNotNull(validatedLocalServer(settings.serverUrl)) { "Use a private IPv4 server address" }
    require(settings.serverToken.length >= 16) { "Enter the server access token" }
    request(base + "/health", settings.serverToken, null).decodeToString().contains("\"ready\"")
  }

  suspend fun synthesize(text: String, settings: SpeechPreferences): SpeechAudio =
    withContext(Dispatchers.IO) {
      val base = requireNotNull(validatedLocalServer(settings.serverUrl)) { "Invalid local server address" }
      require(settings.serverToken.length >= 16) { "Missing server access token" }
      val json = JSONObject().put("text", text).put("voice", settings.voice.code)
        .put("speed", settings.speed).toString().toByteArray(Charsets.UTF_8)
      decodeWav(request(base + "/synthesize", settings.serverToken, json))
    }

  private fun request(url: String, token: String, body: ByteArray?): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 5_000
      readTimeout = 90_000
      instanceFollowRedirects = false // do not leak token to redirected host
      setRequestProperty("X-Nocturne-Token", token)
      if (body != null) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setFixedLengthStreamingMode(body.size)
      }
    }
    try {
      if (body != null) connection.outputStream.use { it.write(body) }
      val status = connection.responseCode
      if (status !in 200..299) error("OmniVoice server HTTP " + status)
      val maxSize = 20 * 1024 * 1024
      if (connection.contentLengthLong > maxSize) error("Speech response too large")
      val out = ByteArrayOutputStream()
      connection.inputStream.use { input ->
        val buffer = ByteArray(8192)
        while (true) {
          val len = input.read(buffer)
          if (len == -1) break
          if (out.size() + len > maxSize) error("Speech response too large")
          out.write(buffer, 0, len)
        }
      }
      return out.toByteArray()
    } finally {
      connection.disconnect()
    }
  }

  /** Parse RIFF WAV PCM16 mono, as produced by tools/omnivoice_server.py. */
  internal fun decodeWav(bytes: ByteArray): SpeechAudio {
    require(bytes.size >= 44) { "Invalid server audio" }
    fun word(offset: Int): Int = (bytes[offset].toInt() and 255) or
      ((bytes[offset + 1].toInt() and 255) shl 8)
    fun dword(offset: Int): Int = word(offset) or (word(offset + 2) shl 16)
    fun match(offset: Int, value: String): Boolean = bytes.copyOfRange(offset, offset + 4)
      .toString(Charsets.US_ASCII) == value
    require(match(0, "RIFF") && match(8, "WAVE")) { "Not a WAV file" }
    var offset = 12
    var channels = 0
    var rate = 0
    var bits = 0
    var format = 0
    var start = -1
    var length = 0
    while (offset + 8 <= bytes.size) {
      val size = dword(offset + 4)
      require(size >= 0 && size <= bytes.size - offset - 8) { "Malformed WAV chunk" }
      if (match(offset, "fmt ") && size >= 16) {
        format = word(offset + 8)
        channels = word(offset + 10)
        rate = dword(offset + 12)
        bits = word(offset + 22)
      } else if (match(offset, "data")) {
        start = offset + 8
        length = size
      }
      offset += 8 + size + (size and 1)
    }
    require(format == 1 && channels == 1 && bits == 16 && rate in 8000..48000) {
      "Unsupported server audio format"
    }
    require(start >= 0 && length > 0 && length % 2 == 0) { "Empty WAV audio" }
    val raw = ByteBuffer.wrap(bytes, start, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val samples = FloatArray(length / 2)
    for (i in samples.indices) samples[i] = raw.get(i) / 32768f
    return SpeechAudio(samples, rate)
  }
}
