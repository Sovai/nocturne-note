package com.example.tts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeechEngine(val label: String) {
  EDGE("Edge Read Aloud"),
  ANDROID("Android TTS")
}

data class SpeechPreferences(
  val engine: SpeechEngine = SpeechEngine.EDGE,
  val speed: Float = 1f,
  val backgroundPlayback: Boolean = false,
  val androidVoiceName: String = "",
  val languageTag: String = "auto",
  val edgeVoice: String = "",
  val edgePitchHz: Int = 0
)

/** Previous AUTO/MMS/OMNIVOICE preferences migrate to Edge, with Android fallback. */
class SpeechSettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("nocturne_speech", Context.MODE_PRIVATE)
  private val savedEngine = prefs.getString("engine", "EDGE")
  private val _state = MutableStateFlow(
    SpeechPreferences(
      engine = if (savedEngine == SpeechEngine.ANDROID.name) SpeechEngine.ANDROID else SpeechEngine.EDGE,
      speed = prefs.getFloat("speed", 1f).coerceIn(0.75f, 1.5f),
      backgroundPlayback = prefs.getBoolean("background", false),
      androidVoiceName = prefs.getString("android_voice", "") ?: "",
      languageTag = (prefs.getString("language_tag", "auto") ?: "auto")
        .takeIf { it == "auto" || it.matches(Regex("[a-z]{2,3}(-[A-Za-z]{2,4}){1,2}")) } ?: "auto",
      edgeVoice = (prefs.getString("edge_voice", "") ?: "")
        .takeIf { EdgeTtsClient.isValidVoice(it) } ?: "",
      edgePitchHz = prefs.getInt("edge_pitch_hz", 0).coerceIn(-40, 40)
    )
  )
  val state = _state.asStateFlow()

  private fun update(value: SpeechPreferences) {
    prefs.edit().putString("engine", value.engine.name)
      .putFloat("speed", value.speed)
      .putBoolean("background", value.backgroundPlayback)
      .putString("android_voice", value.androidVoiceName)
      .putString("language_tag", value.languageTag)
      .putString("edge_voice", value.edgeVoice)
      .putInt("edge_pitch_hz", value.edgePitchHz).apply()
    _state.value = value
  }

  fun engine(value: SpeechEngine) = update(_state.value.copy(engine = value))
  fun speed(value: Float) = update(_state.value.copy(speed = value.coerceIn(0.75f, 1.5f)))
  fun background(value: Boolean) = update(_state.value.copy(backgroundPlayback = value))
  fun androidVoice(value: String) = update(_state.value.copy(androidVoiceName = value))
  fun language(value: String) {
    if (value == "auto" || value.matches(Regex("[a-z]{2,3}(-[A-Za-z]{2,4}){1,2}")))
      update(_state.value.copy(languageTag = value, edgeVoice = ""))
  }
  fun edgeVoice(value: String) {
    if (value.isBlank() || EdgeTtsClient.isValidVoice(value))
      update(_state.value.copy(edgeVoice = value))
  }
  fun edgePitch(value: Int) = update(_state.value.copy(edgePitchHz = value.coerceIn(-40, 40)))
}
