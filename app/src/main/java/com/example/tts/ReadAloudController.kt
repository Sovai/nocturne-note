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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

data class SpeechAudio(val samples: FloatArray, val sampleRate: Int)

data class ReadAloudState(
  val active: Boolean = false,
  val playing: Boolean = false,
  val paused: Boolean = false,
  val loading: Boolean = false,
  val paragraph: Int = 0,
  val totalParagraphs: Int = 0,
  val engine: String = "",
  val speed: Float = 1.0f,
  val error: String? = null,
  val chunks: List<String> = emptyList(),
  val noteId: Long = 0L
)

object SpokenMarkdown {
  private val link = Regex("""!?\[([^\]]+)\]\([^)]+\)""")
  private val heading = Regex("""(?m)^\s{0,3}(?:#{1,6}\s+|>\s*|[-*+]\s+|\d+\.\s+)""")
  private val style = Regex("""(\*\*|__|~~|\*|_)""")
  private val tag = Regex("""<[^>]*>""")
  private val url = Regex("""https?://\S+""")
  fun paragraphs(markdown: String, maxChars: Int = 260): List<String> {
    return markdown.replace(link, "$1").replace(heading, "").replace(style, "")
      .replace(tag, "").replace(url, "")
      .split(Regex("""\n\s*\n"""))
      .map { it.trim().replace(Regex("""\s*\n\s*"""), " ").replace(Regex("""\s+"""), " ") }
      .filter { it.isNotBlank() && !it.startsWith("---") }
      .flatMap { safeChunks(it, maxChars) }
  }

  /**
   * Java BreakIterator can split before a Khmer vowel or after COENG, so
   * choose chunk boundaries with an explicit combining-mark check.
   * No chunk should start with a dependent sign or half a surrogate pair.
   */
  private fun isSafeSpeechBoundary(text: String, index: Int): Boolean {
    if (index <= 0 || index >= text.length) return true
    if (Character.isLowSurrogate(text[index])) return false
    val next = Character.codePointAt(text, index)
    val previous = Character.codePointBefore(text, index)
    val category = Character.getType(next)
    if (category == Character.NON_SPACING_MARK.toInt() ||
      category == Character.COMBINING_SPACING_MARK.toInt() ||
      category == Character.ENCLOSING_MARK.toInt()) return false
    // Khmer COENG binds the following consonant into the preceding syllable.
    if (previous == 0x17D2 || previous == 0x200D || previous == 0x200C) return false
    if (next == 0x200D || next == 0x200C) return false
    return true
  }

  private fun safeChunks(text: String, maxLength: Int): List<String> {
    if (text.length <= maxLength) return listOf(text)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
      var end = (start + maxLength).coerceAtMost(text.length)
      // Never put a vowel, diacritic, subscript consonant or UTF-16
      // surrogate continuation at the beginning of the next chunk.
      while (end > start && !isSafeSpeechBoundary(text, end)) end--
      // A single unusually long combining sequence may exceed the limit;
      // keeping the syllable intact takes priority over chunk length.
      if (end == start) {
        end = start + 1
        while (end < text.length && !isSafeSpeechBoundary(text, end)) end++
      }
      if (end < text.length) {
        // Prefer punctuation or a word gap in the latter half of a chunk.
        var preferred = -1
        for (i in (start + maxLength / 2).coerceAtMost(end) until end) {
          if ((text[i] == ' ' || text[i] == '។' || text[i] == '៕') &&
            isSafeSpeechBoundary(text, i + 1)) preferred = i + 1
        }
        if (preferred > start) end = preferred
      }
      text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
      start = end
    }
    return result
  }

  fun containsKhmer(text: String): Boolean = text.any { it in '\u1780'..'\u17FF' }
}

/** Prefers a configured OmniVoice PC, then local MMS, then Android fallback. */
class ReadAloudController(
  context: Context,
  private val modelManager: KhmerModelManager,
  private val omniModels: OmniVoiceModelManager,
  private val preferences: SpeechSettingsStore
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val ttsReady = CompletableDeferred<Boolean>()
  private val androidTts = TextToSpeech(context.applicationContext) { result ->
    if (!ttsReady.isCompleted) ttsReady.complete(result == TextToSpeech.SUCCESS)
  }
  private val resumePrefs = context.applicationContext.getSharedPreferences("nocturne_reader_position", Context.MODE_PRIVATE)
  private val audioCache = SpeechChunkCache(context.applicationContext)
  private val edgeClient = EdgeTtsClient(context.applicationContext)
  private var edgePlayer: EdgeAudioPlayer? = null
  private val _state = MutableStateFlow(ReadAloudState(speed = preferences.state.value.speed))
  val state = _state.asStateFlow()
  private val _availableAndroidVoices = MutableStateFlow<List<String>>(emptyList())
  val availableAndroidVoices = _availableAndroidVoices.asStateFlow()

  init {
    scope.launch {
      if (ttsReady.await()) {
        _availableAndroidVoices.value = androidTts.voices.orEmpty()
          .filter { it.locale.language == "km" || it.locale.language == "en" }
          .sortedWith(compareBy({ it.locale.language != "km" }, { it.name }))
          .map { it.name }
      }
    }
  }
  private var paragraphs = emptyList<String>()
  private var sourceMarkdown = ""
  private var noteId = 0L
  private var contentSignature = 0
  private var stoppedByUser = false
  private var session: Job? = null
  private var playbackGeneration = 0L
  @Volatile private var track: AudioTrack? = null
  private var trackRate = 0
  private var writtenSamples = 0L

  fun start(markdown: String, currentNoteId: Long = 0L) {
    stop()
    noteId = currentNoteId
    contentSignature = markdown.hashCode()
    sourceMarkdown = markdown
    paragraphs = SpokenMarkdown.paragraphs(markdown,
      if (preferences.state.value.engine == SpeechEngine.OMNIVOICE) 80 else 260)
    if (paragraphs.isEmpty()) return
    val saved = if (noteId > 0 && resumePrefs.getInt("hash_$noteId", 0) == contentSignature)
      resumePrefs.getInt("chunk_$noteId", 0) else 0
    val start = saved.coerceIn(0, paragraphs.lastIndex)
    _state.value = _state.value.copy(active = true, loading = true,
      paragraph = start, totalParagraphs = paragraphs.size, error = null,
      chunks = paragraphs.toList(), noteId = noteId)
    playFrom(start)
  }

  fun seek(index: Int) {
    if (paragraphs.isEmpty() || !_state.value.active) return
    session?.cancel()
    androidTts.stop()
    edgePlayer?.close()
    closeTrack()
    playFrom(index.coerceIn(0, paragraphs.lastIndex))
  }

  fun restartForNewEngine() {
    if (!_state.value.active) return
    val old = _state.value
    val original = sourceMarkdown
    val currentNoteId = noteId
    val fraction = old.paragraph.toDouble() / maxOf(1, old.totalParagraphs - 1)
    stop()
    start(original, currentNoteId)
    if (paragraphs.size > 1) seek((fraction * (paragraphs.size - 1)).toInt())
  }

  fun pauseForFocus() {
    if (_state.value.playing) toggle()
  }


  fun toggle() {
    val current = _state.value
    if (!current.active) return
    if (current.playing) {
      if (current.engine.startsWith("Edge Read Aloud")) {
        edgePlayer?.pause()
      } else if (current.engine == "Android TTS") {
        session?.cancel()
        androidTts.stop()
      } else track?.pause()
      _state.value = current.copy(playing = false, paused = true)
    } else if (current.paused) {
      if (current.engine.startsWith("Edge Read Aloud")) {
        edgePlayer?.resume()
        _state.value = current.copy(playing = true, paused = false)
      } else if (current.engine == "Android TTS") playFrom(current.paragraph)
      else {
        track?.play()
        _state.value = current.copy(playing = true, paused = false)
      }
    }
  }

  fun skip(delta: Int) = seek(_state.value.paragraph + delta)

  fun setSpeed(value: Float) {
    val speed = value.coerceIn(0.75f, 1.5f)
    preferences.speed(speed)
    _state.value = _state.value.copy(speed = speed)
    if (_state.value.active) skip(0)
  }

  private data class Generated(val samples: SpeechAudio?, val engine: String, val mp3: ByteArray? = null)
  private fun preferMms(settings: SpeechPreferences, text: String): Boolean =
    (settings.engine == SpeechEngine.MMS ||
      settings.engine == SpeechEngine.AUTO) &&
      modelManager.isInstalled && SpokenMarkdown.containsKhmer(text)

  private fun playFrom(index: Int) {
    session?.cancel()
    edgePlayer?.close()
    val currentEdgePlayer = EdgeAudioPlayer()
    edgePlayer = currentEdgePlayer
    val generation = ++playbackGeneration
    val settings = preferences.state.value
    session = scope.launch {
      _state.value = _state.value.copy(active = true, loading = true,
        playing = false, paused = false, paragraph = index, error = null)
      var offline: OfflineTts? = null
      var omni: OmniVoiceRemoteClient? = null
      var pending: kotlinx.coroutines.Deferred<Result<Generated>>? = null

      suspend fun render(chunk: String): Generated {
        if (settings.engine == SpeechEngine.EDGE) {
          val audio = edgeClient.synthesize(chunk, settings.edgeVoice,
            settings.speed, settings.edgePitchHz)
          currentCoroutineContext().ensureActive()
          return Generated(null, "Edge Read Aloud · online", mp3 = audio)
        }
        if (settings.engine == SpeechEngine.OMNIVOICE) {
          check(omniModels.state.value.installed) {
            "Download the OmniVoice ONNX model in Settings first."
          }
          if (android.os.Build.VERSION.SDK_INT < 26) {
            error("OmniVoice requires Android 8 or newer.")
          }
          if (omni == null) omni = OmniVoiceRemoteClient(appContext)
          val engine = requireNotNull(omni)
          val key = audioCache.key(chunk, settings.speed, false, "omnivoice-onnx-int4-v1")
          withContext(Dispatchers.IO) { audioCache.get(key) }?.let {
            return Generated(it, "OmniVoice ONNX · cached")
          }
          val audio = engine.synthesize(chunk)
          currentCoroutineContext().ensureActive()
          withContext(Dispatchers.IO) { runCatching { audioCache.put(key, audio) } }
          return Generated(audio, "OmniVoice ONNX · local")
        }
        if (preferMms(settings, chunk)) {
          val key = audioCache.key(chunk, settings.speed, settings.mmsTrimSilence)
          withContext(Dispatchers.IO) { audioCache.get(key) }?.let {
            return Generated(it, "Khmer MMS · cached")
          }
          try {
            if (offline == null) offline = withContext(Dispatchers.IO) {
              OfflineTts(config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                  vits = OfflineTtsVitsModelConfig(
                    model = modelManager.modelPath, tokens = modelManager.tokensPath),
                  numThreads = settings.mmsThreads
                )
              ))
            }
            val model = requireNotNull(offline)
            val audio = withContext(Dispatchers.Default) {
              model.generate(text = chunk, speed = settings.speed)
            }
            currentCoroutineContext().ensureActive()
            val speech = SpeechAudio(audio.samples, audio.sampleRate)
            val processed = if (settings.mmsTrimSilence) trimSpeechSilence(speech) else speech
            withContext(Dispatchers.IO) { runCatching { audioCache.put(key, processed) } }
            return Generated(processed, "Khmer MMS")
          } catch (e: CancellationException) { throw e }
          catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable + Dispatchers.IO) { runCatching { offline?.release() } }
            offline = null
            // Do not silently change voice engines on an MMS error.
            if (settings.engine == SpeechEngine.MMS) {
              throw IllegalStateException("MMS synthesis failed: " +
                (e.message ?: e.javaClass.simpleName), e)
            }
          }
        }
        return Generated(null, "Android TTS")
      }

      try {
        // Start the next generation while the current PCM audio is playing.
        // MMS used to synthesize only AFTER playback ended, causing long silence.
        for (i in index until paragraphs.size) {
          currentCoroutineContext().ensureActive()
          if (noteId > 0) resumePrefs.edit().putInt("hash_$noteId", contentSignature)
            .putInt("chunk_$noteId", i).apply()
          _state.value = _state.value.copy(paragraph = i, loading = i == index, error = null)
          val current = if (pending != null) {
            pending.await().getOrThrow().also { pending = null }
          } else withContext(Dispatchers.IO) { render(paragraphs[i]) }

          if (i < paragraphs.lastIndex) {
            pending = async(Dispatchers.IO) { runCatching { render(paragraphs[i + 1]) } }
          }
          if (current.mp3 != null) {
            while (_state.value.paused) delay(35)
            _state.value = _state.value.copy(engine = current.engine, playing = true,
              loading = false, paused = false)
            currentEdgePlayer.play(current.mp3)
          } else if (current.samples != null) {
            while (_state.value.paused) delay(35)
            _state.value = _state.value.copy(engine = current.engine, playing = true,
              loading = false, paused = false)
            playSamples(current.samples)
            if (current.engine.startsWith("Khmer MMS") && i < paragraphs.lastIndex && preferences.state.value.mmsPauseMs > 0) {
              var remaining = preferences.state.value.mmsPauseMs.toLong()
              while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                if (_state.value.paused) { delay(35); continue }
                val step = minOf(remaining, 20L)
                delay(step)
                remaining -= step
              }
            }
          } else {
            // Native Android TTS cannot prefetch PCM and does its own queue.
            pending?.cancel()
            pending = null
            speakAndroid(paragraphs[i], settings.speed)
          }
        }
        if (noteId > 0) resumePrefs.edit().remove("hash_$noteId")
          .remove("chunk_$noteId").apply()
        _state.value = ReadAloudState(speed = preferences.state.value.speed)
      } catch (_: CancellationException) {
        // User navigated, paused Android TTS, skipped, or stopped.
      } catch (e: Exception) {
        _state.value = _state.value.copy(active = false, playing = false,
          paused = false, loading = false, error = e.message ?: "Speech unavailable")
      } finally {
        // Wait until an in-flight JNI synthesis call has exited before releasing
        // its native model. Cancellation alone cannot interrupt generate().
        withContext(NonCancellable) {
          pending?.cancelAndJoin()
          withContext(Dispatchers.IO) {
            runCatching { offline?.release() }
            runCatching { omni?.close() }
          }
        }
        currentEdgePlayer.close()
        if (generation == playbackGeneration) {
          edgePlayer = null
          closeTrack()
        }
      }
    }
  }

  private suspend fun speakAndroid(text: String, speed: Float) {
    if (!ttsReady.await()) error("Android speech engine is unavailable")
    val locale = if (SpokenMarkdown.containsKhmer(text))
      Locale.forLanguageTag("km-KH") else Locale.ENGLISH
    val preferredVoice = preferences.state.value.androidVoiceName
    val preferred = androidTts.voices?.firstOrNull {
      it.name == preferredVoice && it.locale.language == locale.language
    }
    if (preferred != null) androidTts.voice = preferred
    val availability = if (preferred == null) androidTts.setLanguage(locale)
      else TextToSpeech.LANG_AVAILABLE
    if (availability == TextToSpeech.LANG_MISSING_DATA ||
        availability == TextToSpeech.LANG_NOT_SUPPORTED) {
      error("No Android voice installed for " + locale.displayLanguage)
    }
    androidTts.setSpeechRate(speed)
    _state.value = _state.value.copy(engine = "Android TTS", playing = true,
      paused = false, loading = false)
    suspendCancellableCoroutine<Unit> { cont ->
      val id = "nocturne-" + System.nanoTime()
      androidTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit
        override fun onDone(utteranceId: String) {
          if (utteranceId == id && cont.isActive) cont.resume(Unit)
        }
        @Deprecated("Android compatibility")
        override fun onError(utteranceId: String) {
          if (utteranceId == id && cont.isActive)
            cont.resumeWithException(IllegalStateException("Android speech failed"))
        }
      })
      cont.invokeOnCancellation { androidTts.stop() }
      if (androidTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
        if (cont.isActive) cont.resumeWithException(IllegalStateException("Android speech failed"))
      }
    }
  }

  private suspend fun playSamples(audio: SpeechAudio) = withContext(Dispatchers.IO) {
    val output = track?.takeIf { trackRate == audio.sampleRate } ?: run {
      closeTrack()
      val minimum = AudioTrack.getMinBufferSize(audio.sampleRate,
        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
      check(minimum > 0)
      AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
          .setSampleRate(audio.sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(maxOf(minimum, 16_384))
        .setTransferMode(AudioTrack.MODE_STREAM).build().also {
          check(it.state == AudioTrack.STATE_INITIALIZED)
          track = it
          trackRate = audio.sampleRate
          writtenSamples = 0
          it.play()
          if (_state.value.engine.startsWith("OmniVoice")) {
            runCatching {
              it.playbackParams = android.media.PlaybackParams()
                .setSpeed(preferences.state.value.speed)
                .setPitch(1f)
            }
          }
        }
    }
    if (_state.value.engine.startsWith("OmniVoice")) {
      runCatching {
        output.playbackParams = android.media.PlaybackParams()
          .setSpeed(preferences.state.value.speed)
          .setPitch(1f)
      }
    }
    var offset = 0
    while (offset < audio.samples.size) {
      currentCoroutineContext().ensureActive()
      while (_state.value.paused) delay(35)
      // Nonblocking writes let pause/skip terminate promptly without a deadlock.
      val written = output.write(audio.samples, offset, minOf(4096, audio.samples.size - offset),
        AudioTrack.WRITE_NON_BLOCKING)
      check(written >= 0) { "Audio output error " + written }
      if (written == 0) delay(8) else {
        offset += written
        writtenSamples += written
      }
    }
    while (output.playbackHeadPosition.toLong() < writtenSamples) {
      currentCoroutineContext().ensureActive()
      delay(20)
    }
  }

  private fun closeTrack() {
    track?.let { runCatching { it.pause(); it.flush(); it.release() } }
    track = null
    trackRate = 0
    writtenSamples = 0
  }

  fun stop() {
    playbackGeneration++
    session?.cancel()
    session = null
    androidTts.stop()
    edgePlayer.close()
    closeTrack()
    paragraphs = emptyList()
    sourceMarkdown = ""
    _state.value = ReadAloudState(speed = preferences.state.value.speed)
  }

  fun close() {
    stop()
    androidTts.shutdown()
    scope.coroutineContext[Job]?.cancel()
  }
}
