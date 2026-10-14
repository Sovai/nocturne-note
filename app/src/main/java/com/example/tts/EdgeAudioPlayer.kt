package com.example.tts

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android's built-in MP3 decoder for Edge Read Aloud output.
 * No FFmpeg / downloaded codec or PCM conversion is required.
 */
class EdgeAudioPlayer : AutoCloseable {
  @Volatile private var player: MediaPlayer? = null
  @Volatile private var paused = false
  @Volatile private var finished = false

  suspend fun play(audio: ByteArray) = withContext(Dispatchers.Main.immediate) {
    require(audio.size in 100..(2*1024*1024)) { "Invalid Edge audio" }
    close()
    paused = false
    finished = false
    suspendCancellableCoroutine<Unit> { cont ->
      val media = MediaPlayer()
      player = media
      try {
        media.setAudioAttributes(AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        media.setDataSource(object : MediaDataSource() {
          override fun getSize(): Long = audio.size.toLong()
          override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0 || position >= audio.size) return -1
            val available = minOf(size, audio.size - position.toInt())
            System.arraycopy(audio, position.toInt(), buffer, offset, available)
            return available
          }
          override fun close() = Unit
        })
        media.setOnPreparedListener {
          if (cont.isActive && !paused) runCatching { it.start() }
            .onFailure { ex -> cont.resumeWithException(ex) }
        }
        media.setOnCompletionListener {
          finished = true
          if (cont.isActive) cont.resume(Unit)
        }
        media.setOnErrorListener { _, what, extra ->
          if (cont.isActive) cont.resumeWithException(
            IllegalStateException("MP3 playback failed: $what / $extra"))
          true
        }
        cont.invokeOnCancellation { close() }
        media.prepareAsync()
      } catch (e: Exception) {
        close()
        if (cont.isActive) cont.resumeWithException(e)
      }
    }
  }

  fun pause() {
    paused = true
    if (!finished) player?.let { runCatching { if (it.isPlaying) it.pause() } }
  }

  fun resume() {
    paused = false
    if (!finished) player?.let { runCatching { if (!it.isPlaying) it.start() } }
  }

  override fun close() {
    player?.let { runCatching { it.release() } }
    player = null
    paused = false
    finished = false
  }
}
