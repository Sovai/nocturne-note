package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

data class ReadAloudState(
  val active: Boolean = false,
  val playing: Boolean = false,
  val paused: Boolean = false,
  val loading: Boolean = false,
  val paragraph: Int = 0,
  val totalParagraphs: Int = 0,
  val engine: String = "",
  val speed: Float = 1.0f,
  val error: String? = null
)

object SpokenMarkdown {
  private val link = Regex("""!?\[([^\]]+)\]\([^)]+\)""")
  private val heading = Regex("""(?m)^\s{0,3}(?:#{1,6}\s+|>\s*|[-*+]\s+|\d+\.\s+)""")
  private val style = Regex("""(\*\*|__|~~|\*|_)""")
  private val tag = Regex("""<[^>]*>""")
  private val url = Regex("""https?://\S+""")
  fun paragraphs(markdown: String): List<String> {
    return markdown.replace(link, "$1").replace(heading, "").replace(style, "")
      .replace(tag, "").replace(url, "")
      .split(Regex("""\n\s*\n|\n"""))
      .map { it.trim().replace(Regex("""\s+"""), " ") }
      .filter { it.isNotBlank() && !it.startsWith("---") }
      .flatMap { it.chunked(260) }
  }
  fun containsKhmer(text: String): Boolean = text.any { it in '\u1780'..'\u17FF' }
}

/** Offline MMS for Khmer; the system's voice is used only when unavailable. */
class ReadAloudController(
  context: Context,
  private val modelManager: KhmerModelManager
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val ttsReady = CompletableDeferred<Boolean>()
  private val androidTts = TextToSpeech(context.applicationContext) { result ->
    if (!ttsReady.isCompleted) ttsReady.complete(result == TextToSpeech.SUCCESS)
  }
  private val _state = MutableStateFlow(ReadAloudState())
  val state = _state.asStateFlow()
  private var paragraphs = emptyList<String>()
  private var session: Job? = null
  @Volatile private var audioTrack: AudioTrack? = null

  fun start(markdown: String) {
    stop()
    paragraphs = SpokenMarkdown.paragraphs(markdown)
    if (paragraphs.isEmpty()) return
    _state.value = _state.value.copy(active = true, loading = true, totalParagraphs = paragraphs.size)
    playFrom(0)
  }

  fun toggle() {
    val current = _state.value
    if (!current.active) return
    if (current.playing) {
      if (current.engine == "Android TTS") {
        session?.cancel()
        androidTts.stop()
      } else {
        audioTrack?.pause()
      }
      _state.value = current.copy(playing = false, paused = true)
    } else if (current.paused) {
      if (current.engine == "Android TTS") playFrom(current.paragraph)
      else {
        audioTrack?.play()
        _state.value = current.copy(playing = true, paused = false)
      }
    }
  }

  fun skip(delta: Int) {
    if (paragraphs.isEmpty()) return
    val index = (_state.value.paragraph + delta).coerceIn(0, paragraphs.lastIndex)
    session?.cancel()
    androidTts.stop()
    audioTrack?.let { runCatching { it.pause(); it.flush(); it.release() } }
    audioTrack = null
    playFrom(index)
  }

  fun setSpeed(value: Float) {
    _state.value = _state.value.copy(speed = value.coerceIn(0.75f, 1.5f))
    if (_state.value.active) skip(0)
  }

  private fun playFrom(index: Int) {
    session?.cancel()
    val speed = _state.value.speed
    session = scope.launch {
      _state.value = _state.value.copy(active = true, loading = true, paused = false, paragraph = index, error = null)
      var offline: OfflineTts? = null
      try {
        for (i in index until paragraphs.size) {
          currentCoroutineContext().ensureActive()
          val text = paragraphs[i]
          _state.value = _state.value.copy(paragraph = i, loading = true)
          if (modelManager.isInstalled && SpokenMarkdown.containsKhmer(text)) {
            try {
              if (offline == null) offline = withContext(Dispatchers.IO) {
                OfflineTts(config = OfflineTtsConfig(
                  model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                      model = modelManager.modelPath,
                      tokens = modelManager.tokensPath
                    ),
                    numThreads = 2
                  )
                ))
              }
              val audio = withContext(Dispatchers.Default) { offline.generate(text = text, speed = speed) }
              currentCoroutineContext().ensureActive()
              _state.value = _state.value.copy(engine = "Khmer MMS", playing = true, loading = false)
              playSamples(audio.samples, audio.sampleRate)
            } catch (error: CancellationException) {
              throw error
            } catch (error: Throwable) {
              currentCoroutineContext().ensureActive()
              withContext(Dispatchers.IO) { runCatching { offline?.release() } }
              offline = null
              speakAndroid(text, speed)
            }
          } else speakAndroid(text, speed)
        }
        _state.value = _state.value.copy(active = false, playing = false, paused = false, loading = false)
      } catch (_: CancellationException) {
        // Interrupted by the user.
      } catch (error: Exception) {
        _state.value = _state.value.copy(playing = false, paused = false, loading = false,
          error = error.message ?: "Speech unavailable")
      } finally {
        withContext(Dispatchers.IO) { runCatching { offline?.release() } }
      }
    }
  }

  private suspend fun speakAndroid(text: String, speed: Float) {
    if (!ttsReady.await()) error("Android speech engine is unavailable")
    val locale = if (SpokenMarkdown.containsKhmer(text)) Locale.forLanguageTag("km-KH") else Locale.ENGLISH
    val languageSupport = androidTts.setLanguage(locale)
    if (languageSupport == TextToSpeech.LANG_MISSING_DATA || languageSupport == TextToSpeech.LANG_NOT_SUPPORTED) {
      error("Android does not have a voice for " + locale.displayLanguage)
    }
    androidTts.setSpeechRate(speed)
    _state.value = _state.value.copy(engine = "Android TTS", playing = true, paused = false, loading = false)
    suspendCancellableCoroutine<Unit> { continuation ->
      val id = "nocturne-" + System.nanoTime()
      androidTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit
        override fun onDone(utteranceId: String) {
          if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
        }
        @Deprecated("Android API compatibility")
        override fun onError(utteranceId: String) {
          if (utteranceId == id && continuation.isActive) {
            continuation.resumeWithException(IllegalStateException("Android speech failed"))
          }
        }
      })
      continuation.invokeOnCancellation { androidTts.stop() }
      if (androidTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Android speech failed"))
      }
    }
  }

  private suspend fun playSamples(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
    val minimum = AudioTrack.getMinBufferSize(sampleRate,
      AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    check(minimum > 0)
    val track = AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
      .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
        .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
      .setBufferSizeInBytes(maxOf(minimum, 16_384))
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
    audioTrack = track
    try {
      check(track.state == AudioTrack.STATE_INITIALIZED)
      track.play()
      var offset = 0
      while (offset < samples.size) {
        currentCoroutineContext().ensureActive()
        val written = track.write(samples, offset, minOf(4096, samples.size - offset),
          AudioTrack.WRITE_BLOCKING)
        check(written > 0) { "Audio output failed" }
        offset += written
      }
      while (track.playbackHeadPosition.toLong() < samples.size.toLong()) {
        currentCoroutineContext().ensureActive()
        delay(30)
      }
    } finally {
      if (audioTrack === track) audioTrack = null
      runCatching { track.pause(); track.flush(); track.release() }
    }
  }

  fun stop() {
    session?.cancel()
    session = null
    androidTts.stop()
    audioTrack?.let { runCatching { it.pause(); it.flush(); it.release() } }
    audioTrack = null
    paragraphs = emptyList()
    _state.value = ReadAloudState(speed = _state.value.speed)
  }

  fun close() {
    stop()
    androidTts.shutdown()
    scope.coroutineContext[Job]?.cancel()
  }
}
