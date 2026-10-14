package com.example.tts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

enum class SpeechEngine(val label: String) {
  AUTO("Automatic"),
  OMNIVOICE("OmniVoice (PC / Wi-Fi)"),
  MMS("Khmer MMS (offline)"),
  ANDROID("Android TTS")
}

enum class SpeechVoice(val label: String, val code: String) {
  AUTO("Automatic", "auto"),
  MALE("Male · low", "male"),
  FEMALE("Female", "female"),
  ELDER("Older male · low", "elder"),
  SOFT("Soft female", "soft")
}

data class SpeechPreferences(
  val engine: SpeechEngine = SpeechEngine.AUTO,
  val voice: SpeechVoice = SpeechVoice.AUTO,
  val speed: Float = 1f,
  val serverUrl: String = "",
  val serverToken: String = "",
  val serverStatus: String = ""
) {
  val serverConfigured: Boolean
    get() = validatedLocalServer(serverUrl) != null && serverToken.length >= 16
}

/** Require a private IPv4 literal. Never send notes to public internet or arbitrary DNS. */
fun validatedLocalServer(value: String): String? {
  val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
  if (uri.scheme != "http" || uri.userInfo != null || uri.query != null ||
      uri.fragment != null || uri.path?.trim('/')?.isNotEmpty() == true) return null
  val host = uri.host ?: return null
  if (!host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return null
  val bytes = host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { v -> v in 0..255 } }
  if (bytes.size != 4) return null
  val local = bytes[0] == 10 || (bytes[0] == 172 && bytes[1] in 16..31) ||
    (bytes[0] == 192 && bytes[1] == 168) || bytes[0] == 127
  if (!local || uri.port !in 1..65535) return null
  return "http://" + host + ":" + uri.port
}

class SpeechSettingsStore(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("nocturne_speech", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(read())
  val state = _state.asStateFlow()

  private fun read() = SpeechPreferences(
    engine = runCatching { SpeechEngine.valueOf(prefs.getString("engine", "AUTO")!!) }
      .getOrDefault(SpeechEngine.AUTO),
    voice = runCatching { SpeechVoice.valueOf(prefs.getString("voice", "AUTO")!!) }
      .getOrDefault(SpeechVoice.AUTO),
    speed = prefs.getFloat("speed", 1f).coerceIn(0.75f, 1.5f),
    serverUrl = prefs.getString("server", "") ?: "",
    serverToken = prefs.getString("token", "") ?: ""
  )

  fun engine(engine: SpeechEngine) = update(_state.value.copy(engine = engine))
  fun voice(voice: SpeechVoice) = update(_state.value.copy(voice = voice))
  fun speed(speed: Float) = update(_state.value.copy(speed = speed.coerceIn(0.75f, 1.5f)))
  fun server(url: String) = update(_state.value.copy(serverUrl = url.trim(), serverStatus = ""))
  fun token(token: String) = update(_state.value.copy(serverToken = token.trim(), serverStatus = ""))
  fun status(message: String) { _state.value = _state.value.copy(serverStatus = message) }

  private fun update(next: SpeechPreferences) {
    prefs.edit()
      .putString("engine", next.engine.name)
      .putString("voice", next.voice.name)
      .putFloat("speed", next.speed)
      .putString("server", next.serverUrl)
      .putString("token", next.serverToken)
      .apply()
    _state.value = next
  }
}
