package com.example.tts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeechEngine(val label: String) {
  AUTO("Automatic"),
  OMNIVOICE("OmniVoice ONNX · on-device"),
  MMS("Khmer MMS · offline"),
  EDGE("Edge Read Aloud · online"),
  ANDROID("Android TTS")
}

enum class SpeechVoice(val label: String, val code: String) {
  AUTO("Default", "auto"),
  MALE("Male · low", "male"),
  FEMALE("Female", "female"),
  ELDER("Older male · low", "elder"),
  SOFT("Soft female", "soft")
}

data class SpeechPreferences(
  val engine: SpeechEngine = SpeechEngine.AUTO,
  val voice: SpeechVoice = SpeechVoice.AUTO,
  val speed: Float = 1f,
  val backgroundPlayback: Boolean = false,
  val androidVoiceName: String = "",
  val mmsTrimSilence: Boolean = true,
  val mmsPauseMs: Int = 0,
  val mmsThreads: Int = 2,
  val edgeVoice: String = "km-KH-PisethNeural",
  val edgePitchHz: Int = 0
)

/** Persist only user preferences, never text or reference audio. */
class SpeechSettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("nocturne_speech", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(
    SpeechPreferences(
      engine = runCatching { SpeechEngine.valueOf(prefs.getString("engine", "AUTO") ?: "AUTO") }
        .getOrDefault(SpeechEngine.AUTO),
      voice = runCatching { SpeechVoice.valueOf(prefs.getString("voice", "AUTO") ?: "AUTO") }
        .getOrDefault(SpeechVoice.AUTO),
      speed = prefs.getFloat("speed", 1f).coerceIn(0.75f, 1.5f),
      backgroundPlayback = prefs.getBoolean("background", false),
      androidVoiceName = prefs.getString("android_voice", "") ?: "",
      mmsTrimSilence = prefs.getBoolean("mms_trim_silence", true),
      mmsPauseMs = prefs.getInt("mms_pause_ms", 0).coerceIn(0, 600),
      mmsThreads = prefs.getInt("mms_threads", 2).coerceIn(1, 4),
      edgeVoice = prefs.getString("edge_voice", "km-KH-PisethNeural")?.takeIf { it in EdgeTtsClient.KHMER_VOICES } ?: "km-KH-PisethNeural",
      edgePitchHz = prefs.getInt("edge_pitch_hz", 0).coerceIn(-40,40)
    )
  )
  val state = _state.asStateFlow()

  private fun update(value: SpeechPreferences) {
    prefs.edit().putString("engine", value.engine.name)
      .putString("voice", value.voice.name)
      .putFloat("speed", value.speed)
      .putBoolean("background", value.backgroundPlayback)
      .putString("android_voice", value.androidVoiceName)
      .putBoolean("mms_trim_silence", value.mmsTrimSilence)
      .putInt("mms_pause_ms", value.mmsPauseMs)
      .putInt("mms_threads", value.mmsThreads)
      .putString("edge_voice", value.edgeVoice)
      .putInt("edge_pitch_hz", value.edgePitchHz).apply()
    _state.value = value
  }

  fun engine(value: SpeechEngine) = update(_state.value.copy(engine = value))
  fun voice(value: SpeechVoice) = update(_state.value.copy(voice = value))
  fun speed(value: Float) = update(_state.value.copy(speed = value.coerceIn(0.75f, 1.5f)))
  fun background(value: Boolean) = update(_state.value.copy(backgroundPlayback = value))
  fun androidVoice(value: String) = update(_state.value.copy(androidVoiceName = value))
  fun mmsTrimSilence(value: Boolean) = update(_state.value.copy(mmsTrimSilence = value))
  fun mmsPauseMs(value: Int) = update(_state.value.copy(mmsPauseMs = value.coerceIn(0, 600)))
  fun mmsThreads(value: Int) = update(_state.value.copy(mmsThreads = value.coerceIn(1, 4)))
  fun edgeVoice(value: String) { if (value in EdgeTtsClient.KHMER_VOICES) update(_state.value.copy(edgeVoice = value)) }
  fun edgePitch(value: Int) = update(_state.value.copy(edgePitchHz = value.coerceIn(-40,40)))
}
