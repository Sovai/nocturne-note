package com.example.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGenerationDeadlineTest {
  @Test fun stalledWorkerReturnsAnErrorInsteadOfSilentReaderCancellation() = runTest {
    val failure = runCatching {
      withSpeechDeadline(180_000, "OmniVoice") { awaitCancellation() }
    }.exceptionOrNull()
    assertTrue(failure is IllegalStateException)
    assertTrue(failure !is CancellationException)
    assertTrue(failure?.message.orEmpty().contains("180 seconds"))
  }

  @Test fun completedSpeechReturnsNormally() = runTest {
    assertEquals("audio", withSpeechDeadline(65_000, "Edge Read Aloud") { "audio" })
  }

  @Test fun stoppingOrSeekingStillCancelsWithoutShowingTimeoutError() = runTest {
    val started = CompletableDeferred<Unit>()
    val task = async {
      withSpeechDeadline(180_000, "OmniVoice") {
        started.complete(Unit)
        awaitCancellation()
      }
    }
    started.await()
    task.cancel()
    assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
  }
}
