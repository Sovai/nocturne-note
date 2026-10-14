package com.example.tts

import kotlinx.coroutines.withTimeoutOrNull

/** A synthesis deadline is a failure; cancellation of the reader remains cancellation. */
internal suspend fun <T : Any> withSpeechDeadline(
  timeoutMs: Long,
  engine: String,
  block: suspend () -> T
): T = withTimeoutOrNull(timeoutMs) { block() }
  ?: throw IllegalStateException("$engine generation timed out after ${timeoutMs / 1000} seconds. Playback stopped.")
