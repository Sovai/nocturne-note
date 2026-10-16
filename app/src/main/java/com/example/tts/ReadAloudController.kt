package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

data class ReadAloudState(
  val active: Boolean = false,
  val playing: Boolean = false,
  val paused: Boolean = false,
  val loading: Boolean = false,
  val generationStage: String = "",
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

/** Edge online speech with automatic, per-session Android fallback. */
class ReadAloudController(
  context: Context,
  private val preferences: SpeechSettingsStore
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val ttsReady = CompletableDeferred<Boolean>()
  private val androidTts = TextToSpeech(context.applicationContext) { result ->
    if (!ttsReady.isCompleted) ttsReady.complete(result == TextToSpeech.SUCCESS)
  }
  private val resumePrefs = context.applicationContext.getSharedPreferences(
    "nocturne_reader_position", Context.MODE_PRIVATE)
  private val edgeClient = EdgeTtsClient(context.applicationContext)
  private var edgePlayer: EdgeAudioPlayer? = null
  private val _state = MutableStateFlow(ReadAloudState(speed = preferences.state.value.speed))
  val state = _state.asStateFlow()
  private val _availableAndroidVoices = MutableStateFlow<List<String>>(emptyList())
  val availableAndroidVoices = _availableAndroidVoices.asStateFlow()
  private val _availableAndroidLanguages = MutableStateFlow<List<String>>(emptyList())
  val availableAndroidLanguages = _availableAndroidLanguages.asStateFlow()
  private val _availableEdgeVoices = MutableStateFlow(EdgeTtsClient.STARTER_VOICES)
  val availableEdgeVoices = _availableEdgeVoices.asStateFlow()

  init {
    scope.launch {
      if (ttsReady.await()) {
        val voices = androidTts.voices.orEmpty()
        _availableAndroidVoices.value = voices
          .sortedWith(compareBy({ it.locale.displayName }, { it.name })).map { it.name }
        _availableAndroidLanguages.value = voices.map { it.locale.toLanguageTag() }
          .filter { it != "und" }.distinct().sorted()
      }
    }
    scope.launch(Dispatchers.IO) {
      _availableEdgeVoices.value = edgeClient.listVoices()
    }
  }

  private var paragraphs = emptyList<String>()
  private var sourceMarkdown = ""
  private var noteId = 0L
  private var contentSignature = 0
  private var session: Job? = null
  private var playbackGeneration = 0L

  fun start(markdown: String, currentNoteId: Long = 0L) {
    stop()
    noteId = currentNoteId
    contentSignature = markdown.hashCode()
    sourceMarkdown = markdown
    paragraphs = SpokenMarkdown.paragraphs(markdown)
    if (paragraphs.isEmpty()) return
    val saved = if (noteId > 0 && resumePrefs.getInt("hash_$noteId", 0) == contentSignature)
      resumePrefs.getInt("chunk_$noteId", 0) else 0
    val index = saved.coerceIn(0, paragraphs.lastIndex)
    _state.value = _state.value.copy(active = true, loading = true, paragraph = index,
      totalParagraphs = paragraphs.size, error = null, chunks = paragraphs.toList(), noteId = noteId)
    playFrom(index)
  }

  fun seek(index: Int) {
    if (paragraphs.isEmpty() || !_state.value.active) return
    session?.cancel()
    androidTts.stop()
    edgePlayer?.close()
    playFrom(index.coerceIn(0, paragraphs.lastIndex))
  }

  fun restartForNewEngine() {
    if (!_state.value.active) return
    seek(_state.value.paragraph)
  }

  fun pauseForFocus() {
    if (_state.value.playing) toggle()
  }

  fun toggle() {
    val current = _state.value
    if (!current.active) return
    if (current.playing) {
      if (current.engine.startsWith("Edge")) edgePlayer?.pause()
      else {
        session?.cancel()
        androidTts.stop()
      }
      _state.value = current.copy(playing = false, paused = true)
    } else if (current.paused) {
      if (current.engine.startsWith("Edge")) {
        edgePlayer?.resume()
        _state.value = current.copy(playing = true, paused = false)
      } else playFrom(current.paragraph)
    }
  }

  fun skip(delta: Int) = seek(_state.value.paragraph + delta)

  fun setSpeed(value: Float) {
    val speed = value.coerceIn(0.75f, 1.5f)
    preferences.speed(speed)
    _state.value = _state.value.copy(speed = speed)
    if (_state.value.active) seek(_state.value.paragraph)
  }

  private fun playFrom(index: Int) {
    session?.cancel()
    edgePlayer?.close()
    val player = EdgeAudioPlayer()
    edgePlayer = player
    val generation = ++playbackGeneration
    val settings = preferences.state.value
    session = scope.launch {
      var edgeUnavailable = false
      try {
        for (i in index until paragraphs.size) {
          if (noteId > 0) resumePrefs.edit().putInt("hash_$noteId", contentSignature)
            .putInt("chunk_$noteId", i).apply()
          _state.value = _state.value.copy(paragraph = i, playing = false,
            paused = false, loading = true, error = null, generationStage = "")
          val text = paragraphs[i]
          val locale = SpeechLanguage.resolve(text, settings.languageTag)
          val voice = EdgeTtsClient.chooseVoice(_availableEdgeVoices.value, locale, settings.edgeVoice)
          if (settings.engine == SpeechEngine.EDGE && !edgeUnavailable && voice != null) {
            try {
              val audio = edgeClient.synthesize(text, voice.name, settings.speed, settings.edgePitchHz)
              _state.value = _state.value.copy(engine = "Edge Read Aloud", playing = true,
                loading = false, paused = false)
              player.play(audio)
              continue
            } catch (cancel: CancellationException) {
              throw cancel
            } catch (_: Exception) {
              // Edge endpoints are unofficial and can return 403/429 or disconnect.
              // Stop retrying this session and finish the note with local Android TTS.
              edgeUnavailable = true
              player.close()
            }
          }
          speakAndroid(text, locale, settings.speed,
            fallback = settings.engine == SpeechEngine.EDGE)
        }
        if (noteId > 0) resumePrefs.edit().remove("hash_$noteId")
          .remove("chunk_$noteId").apply()
        _state.value = ReadAloudState(speed = preferences.state.value.speed)
      } catch (_: CancellationException) {
        // Skip, pause or stop cancels the active speech operation.
      } catch (e: Exception) {
        if (generation == playbackGeneration)
          _state.value = _state.value.copy(active = false, playing = false,
            paused = false, loading = false, error = e.message ?: "Speech unavailable")
      } finally {
        player.close()
        if (generation == playbackGeneration) edgePlayer = null
      }
    }
  }

  private suspend fun speakAndroid(text: String, languageTag: String, speed: Float, fallback: Boolean) {
    if (!ttsReady.await()) error("Android TTS is unavailable")
    val locale = Locale.forLanguageTag(languageTag)
    val requested = preferences.state.value.androidVoiceName
    val preferred = androidTts.voices?.firstOrNull { it.name == requested &&
      it.locale.language == locale.language }
    val availability = androidTts.setLanguage(locale)
    if (availability == TextToSpeech.LANG_MISSING_DATA ||
      availability == TextToSpeech.LANG_NOT_SUPPORTED) {
      error("Install an Android TTS voice for " + locale.displayName +
        " or choose another read-aloud language")
    }
    if (preferred != null) androidTts.voice = preferred
    androidTts.setSpeechRate(speed)
    _state.value = _state.value.copy(
      engine = if (fallback) "Android TTS · fallback" else "Android TTS",
      playing = true, paused = false, loading = false)
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
      if (androidTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR)
        if (cont.isActive) cont.resumeWithException(IllegalStateException("Android speech failed"))
    }
  }

  fun stop() {
    playbackGeneration++
    session?.cancel()
    session = null
    androidTts.stop()
    edgePlayer?.close()
    edgePlayer = null
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
