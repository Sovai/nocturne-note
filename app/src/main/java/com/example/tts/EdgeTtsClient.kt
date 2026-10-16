package com.example.tts

import android.content.Context
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Optional no-key Edge Read Aloud client, not the official Azure Speech API.
 * Sends only requested text chunks, over TLS, when Edge is selected.
 * This unofficial endpoint can fail or change without notice.
 */
data class EdgeVoice(val name: String, val locale: String, val gender: String) {
  val displayName: String get() = name.substringAfter("$locale-").removeSuffix("Neural") + " · " + gender
}

class EdgeTtsClient(context: Context) {
  companion object {
    const val PISETH = "km-KH-PisethNeural"
    const val SREYMOM = "km-KH-SreymomNeural"
    // Local catalog works offline; the service's live catalog supplies all supported locales.
    val STARTER_VOICES = listOf(
      EdgeVoice(PISETH, "km-KH", "Male"), EdgeVoice(SREYMOM, "km-KH", "Female"),
      EdgeVoice("en-US-JennyNeural", "en-US", "Female"),
      EdgeVoice("en-US-GuyNeural", "en-US", "Male"),
      EdgeVoice("en-GB-SoniaNeural", "en-GB", "Female"),
      EdgeVoice("fr-FR-DeniseNeural", "fr-FR", "Female"),
      EdgeVoice("es-ES-ElviraNeural", "es-ES", "Female"),
      EdgeVoice("de-DE-KatjaNeural", "de-DE", "Female"),
      EdgeVoice("pt-BR-FranciscaNeural", "pt-BR", "Female"),
      EdgeVoice("it-IT-ElsaNeural", "it-IT", "Female"),
      EdgeVoice("ja-JP-NanamiNeural", "ja-JP", "Female"),
      EdgeVoice("ko-KR-SunHiNeural", "ko-KR", "Female"),
      EdgeVoice("zh-CN-XiaoxiaoNeural", "zh-CN", "Female"),
      EdgeVoice("zh-TW-HsiaoChenNeural", "zh-TW", "Female"),
      EdgeVoice("th-TH-PremwadeeNeural", "th-TH", "Female"),
      EdgeVoice("vi-VN-HoaiMyNeural", "vi-VN", "Female"),
      EdgeVoice("hi-IN-SwaraNeural", "hi-IN", "Female"),
      EdgeVoice("id-ID-GadisNeural", "id-ID", "Female"),
      EdgeVoice("ru-RU-SvetlanaNeural", "ru-RU", "Female"),
      EdgeVoice("ar-SA-ZariyahNeural", "ar-SA", "Female"),
      EdgeVoice("nl-NL-ColetteNeural", "nl-NL", "Female"),
      EdgeVoice("tr-TR-EmelNeural", "tr-TR", "Female")
    )
    fun isValidVoice(voice: String): Boolean =
      voice.matches(Regex("[a-z]{2,3}(?:-[A-Za-z]{2,4}){1,2}-[A-Za-z0-9]+Neural"))
    fun chooseVoice(voices: List<EdgeVoice>, locale: String, preferred: String): EdgeVoice? {
      val options = voices.filter { it.locale.equals(locale, ignoreCase = true) }
        .ifEmpty { voices.filter { it.locale.substringBefore('-') == locale.substringBefore('-') } }
      return options.firstOrNull { it.name == preferred } ?: options.firstOrNull()
    }
    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val VERSION = "2-143.0.3650.75"
    private const val BASE = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TOKEN"
    private const val FORMAT = "audio-24khz-48kbitrate-mono-mp3"
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun signature(unixSeconds: Long): String {
      // Edge's v2 handshake now requires CS64-SHA256, not legacy plain SHA256.
      // Keep upstream algorithm in EdgeV2Signature, covered by regression tests.
      return EdgeV2Signature.generate(unixSeconds, TOKEN)
    }

    fun ssml(text: String, voice: String, speed: Float, pitch: Int): String {
      require(isValidVoice(voice))
      require(speed in 0.75f..1.5f && pitch in -40..40)
      require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 3000)
      val escaped = buildString {
        text.forEach { ch ->
          append(when (ch) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> if (ch.code <= 8 || ch.code in 11..12 || ch.code in 14..31) " " else ch.toString()
          })
        }
      }
      val rate = ((speed - 1f) * 100f).toInt()
      val rateValue = if (rate >= 0) "+$rate%" else "$rate%"
      val pitchValue = if (pitch >= 0) "+${pitch}Hz" else "${pitch}Hz"
      val locale = voice.substringBeforeLast('-')
      return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" +
        "<voice name='$voice'><prosody pitch='$pitchValue' rate='$rateValue' volume='+0%'>" +
        escaped + "</prosody></voice></speak>"
    }

    fun audioPayload(bytes: ByteArray): ByteArray? {
      if (bytes.size < 4) return null
      val len = ((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
      if (len < 5 || 2 + len > bytes.size) return null
      val header = bytes.copyOfRange(2, 2 + len).toString(Charsets.US_ASCII)
      if (!Regex("""(?im)^Path:audio\s*$""").containsMatchIn(header)) return null
      // Edge frames have a CRLF delimiter after the counted headers. Do not
      // include those bytes in the MP3 stream.
      val dataStart = if (bytes.size >= 4 + len &&
        bytes[2 + len] == 13.toByte() && bytes[3 + len] == 10.toByte())
        4 + len else 2 + len
      val payload = bytes.copyOfRange(dataStart, bytes.size)
      if (payload.isNotEmpty() && !Regex("""(?im)^Content-Type:audio/mpeg\s*$""").containsMatchIn(header)) return null
      return payload
    }

    private fun timestamp(): String = SimpleDateFormat(
      "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun requestId() = UUID.randomUUID().toString().replace("-", "")
  }

  private val directory = File(context.cacheDir, "edge-read-aloud").also { it.mkdirs() }
  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()

  /** Fetch Microsoft's published locale/voice list; keep a bounded embedded fallback. */
  suspend fun listVoices(): List<EdgeVoice> = withContext(Dispatchers.IO) {
    runCatching {
      val uri = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list" +
        "?trustedclienttoken=$TOKEN&Sec-MS-GEC=${signature(System.currentTimeMillis()/1000)}&Sec-MS-GEC-Version=$VERSION"
      val request = Request.Builder().url(uri)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
        .header("Accept", "application/json").build()
      val response = client.newCall(request).execute()
      response.use {
        check(it.isSuccessful) { "Voice catalog HTTP ${it.code}" }
        val body = it.body?.string() ?: error("Empty voice catalog")
        val array = JSONArray(body)
        val found = (0 until array.length()).mapNotNull { i ->
          val voice = array.optJSONObject(i) ?: return@mapNotNull null
          val name = voice.optString("ShortName")
          val locale = voice.optString("Locale")
          if (!isValidVoice(name) || locale.isBlank()) null
          else EdgeVoice(name, locale, voice.optString("Gender", "Voice"))
        }
        check(found.size >= 20) { "Incomplete voice catalog" }
        found.distinctBy { it.name }.sortedWith(compareBy({ it.locale }, { it.name }))
      }
    }.getOrElse { STARTER_VOICES }
  }

  suspend fun synthesize(text: String, voice: String, speed: Float, pitch: Int): ByteArray {
    val markup = ssml(text, voice, speed, pitch)
    val hash = MessageDigest.getInstance("SHA-256").digest(
      "edge-v1|$voice|$speed|$pitch|$text".toByteArray(Charsets.UTF_8)
    ).joinToString("") { "%02x".format(it) }
    val file = File(directory, "$hash.mp3")
    withContext(Dispatchers.IO) {
      if (file.isFile && file.length() in 100L..MAX_BYTES.toLong()) {
        file.setLastModified(System.currentTimeMillis())
        file.readBytes()
      } else null
    }?.let { return it }
    val audio = withSpeechDeadline(65_000L, "Edge Read Aloud") { requestAudio(markup) }
    withContext(Dispatchers.IO) {
      runCatching {
        val temp = File(directory, "$hash.tmp")
        temp.writeBytes(audio)
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) temp.delete()
        var sum = 0L
        directory.listFiles()?.filter { it.extension == "mp3" }
          ?.sortedByDescending { it.lastModified() }?.forEach { f ->
            sum += f.length()
            if (sum > 48L * 1024 * 1024) f.delete()
          }
      }
    }
    return audio
  }

  private suspend fun requestAudio(markup: String): ByteArray =
    suspendCancellableCoroutine { continuation ->
      val request = Request.Builder()
        .url("$BASE&ConnectionId=${requestId()}&Sec-MS-GEC=${signature(System.currentTimeMillis()/1000)}&Sec-MS-GEC-Version=$VERSION")
        .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
        .header("Pragma", "no-cache")
        .header("Cache-Control", "no-cache")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Cookie", "muid=" + requestId().uppercase(Locale.US) + ";")
        .build()
      val audio = ByteArrayOutputStream()
      val finished = AtomicBoolean(false)
      fun complete(bytes: ByteArray? = null, error: Exception? = null) {
        if (!finished.compareAndSet(false, true) || !continuation.isActive) return
        if (error != null) continuation.resumeWithException(error)
        else if (bytes == null || bytes.size < 100)
          continuation.resumeWithException(IllegalStateException("Edge Read Aloud returned no audio"))
        else continuation.resume(bytes)
      }
      val socket = client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          val stamp = timestamp()
          val config = "X-Timestamp:$stamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
            """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"$FORMAT"}}}}"""
          val ssmlRequest = "X-RequestId:${requestId()}\r\nContent-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${stamp}Z\r\nPath:ssml\r\n\r\n$markup"
          if (!webSocket.send(config) || !webSocket.send(ssmlRequest)) {
            complete(error = IllegalStateException("Unable to send Edge Read Aloud request"))
            webSocket.cancel()
          }
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
          if (text.contains("Path:turn.end")) {
            complete(audio.toByteArray())
            webSocket.close(1000, null)
          }
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
          val payload = audioPayload(bytes.toByteArray()) ?: return
          if (payload.isNotEmpty()) {
            if (audio.size() + payload.size > MAX_BYTES) {
              complete(error = IllegalStateException("Edge Read Aloud audio exceeded size limit"))
              webSocket.cancel()
            } else audio.write(payload)
          }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          val reason = if (response?.code == 403 || response?.code == 429)
            "Microsoft rejected the Edge Read Aloud request (HTTP ${response.code}). " +
              "Your device connection may be restricted. A third-party website can " +
              "work through its server even when direct phone requests are blocked. " +
              "This is an unofficial service, not a guaranteed Azure free API."
          else "Edge Read Aloud connection failed: ${t.message ?: t.javaClass.simpleName}"
          complete(error = IllegalStateException(reason, t))
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          if (!finished.get()) complete(error = IllegalStateException("Edge Read Aloud disconnected before audio completed"))
        }
      })
      continuation.invokeOnCancellation { socket.cancel() }
    }
}
