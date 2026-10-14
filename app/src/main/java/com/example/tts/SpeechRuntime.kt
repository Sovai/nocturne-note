package com.example.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Application-scoped playback, independent from Activity/ViewModel destruction.
 * Background mode uses SpeechPlaybackService; otherwise leaving the foreground
 * stops reading. Focus loss pauses, never skips or invokes another TTS engine.
 */
object SpeechRuntime {
  private lateinit var app: Context
  lateinit var models: KhmerModelManager
    private set
  lateinit var settings: SpeechSettingsStore
    private set
  lateinit var controller: ReadAloudController
    private set

  private var audioManager: AudioManager? = null
  private var focusRequest: AudioFocusRequest? = null
  private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    when (change) {
      AudioManager.AUDIOFOCUS_LOSS,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        if (::controller.isInitialized) controller.pauseForFocus()
      }
    }
  }

  fun initialize(context: Context) {
    if (::controller.isInitialized) return
    app = context.applicationContext
    models = KhmerModelManager(app)
    settings = SpeechSettingsStore(app)
    controller = ReadAloudController(app, models, settings)
    audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }

  private fun acquireFocus(): Boolean {
    val manager = audioManager ?: return false
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(focusListener)
        .build()
      focusRequest = request
      manager.requestAudioFocus(request)
    } else {
      @Suppress("DEPRECATION")
      manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN)
    }
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  fun start(markdown: String, noteId: Long) {
    if (!acquireFocus()) return
    controller.start(markdown, noteId)
    syncForegroundService()
  }

  fun toggle() {
    if (!controller.state.value.playing && !acquireFocus()) return
    controller.toggle()
    syncForegroundService()
  }

  fun seek(index: Int) {
    if (!acquireFocus()) return
    controller.seek(index)
    syncForegroundService()
  }

  fun skip(delta: Int) = seek(controller.state.value.paragraph + delta)

  fun engine(engine: SpeechEngine) {
    if (engine == SpeechEngine.OMNIVOICE) return // never claim unsupported local playback
    settings.engine(engine)
    if (controller.state.value.active) controller.skip(0)
  }

  fun stop() {
    if (!::controller.isInitialized) return
    controller.stop()
    val manager = audioManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { manager?.abandonAudioFocusRequest(it) }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      manager?.abandonAudioFocus(focusListener)
    }
    app.stopService(Intent(app, SpeechPlaybackService::class.java))
  }

  fun background(enabled: Boolean) {
    settings.background(enabled)
    if (enabled) syncForegroundService()
    else app.stopService(Intent(app, SpeechPlaybackService::class.java))
  }

  fun onActivityBackgrounded() {
    if (!settings.state.value.backgroundPlayback) stop()
  }

  private fun syncForegroundService() {
    if (!settings.state.value.backgroundPlayback || !controller.state.value.active) return
    ContextCompat.startForegroundService(app,
      Intent(app, SpeechPlaybackService::class.java))
  }
}
