package com.example.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedSpeechPrefetchTest {
  @Test fun rendersThreeAheadAndConsumesOnlyInOrder() = runTest {
    val started = mutableListOf<Int>()
    val queue = OrderedSpeechPrefetch(
      scope = this, total = 7, lookahead = 3, concurrency = 2,
      dispatcher = StandardTestDispatcher(testScheduler)
    ) { index ->
      started.add(index)
      byteArrayOf(index.toByte())
    }
    queue.prime(0)
    runCurrent()
    assertEquals(listOf(0, 1, 2, 3), started)

    assertEquals(0, queue.take(0).single().toInt())
    runCurrent()
    assertEquals(listOf(0, 1, 2, 3, 4), started)
    for (index in 1 until 7) {
      assertEquals(index, queue.take(index).single().toInt())
      runCurrent()
    }
    queue.cancel()
    assertEquals((0 until 7).toList(), started)
  }

  @Test fun neverRunsMoreThanTwoSynthesisRequestsAtOnce() = runTest {
    var active = 0
    var maximum = 0
    val release = CompletableDeferred<Unit>()
    val queue = OrderedSpeechPrefetch(
      scope = this, total = 20, lookahead = 3, concurrency = 2,
      dispatcher = StandardTestDispatcher(testScheduler)
    ) {
      active++
      maximum = maxOf(maximum, active)
      try {
        release.await()
        byteArrayOf(1)
      } finally {
        active--
      }
    }
    queue.prime(0)
    runCurrent()
    assertEquals(2, active)
    assertEquals(2, maximum)
    queue.cancel()
    runCurrent()
    assertEquals(0, active)
  }

  @Test fun failureInLaterPrefetchDoesNotCancelReaderBeforeConsumption() = runTest {
    val queue = OrderedSpeechPrefetch(
      scope = this, total = 4, lookahead = 3,
      dispatcher = StandardTestDispatcher(testScheduler)
    ) { index ->
      if (index == 2) error("Edge HTTP 403")
      byteArrayOf(index.toByte())
    }
    queue.prime(0)
    runCurrent()
    assertEquals(0, queue.take(0).single().toInt())
    assertEquals(1, queue.take(1).single().toInt())
    val failure = runCatching { queue.take(2) }.exceptionOrNull()
    assertTrue(failure is IllegalStateException)
    assertTrue(currentCoroutineContext().isActive)
    queue.cancel()
  }

  @Test fun onlySchedulesRemainingItemsNearEndOfNote() = runTest {
    val started = mutableListOf<Int>()
    val queue = OrderedSpeechPrefetch(
      scope = this, total = 5, lookahead = 3,
      dispatcher = StandardTestDispatcher(testScheduler)
    ) { index ->
      started += index
      byteArrayOf(index.toByte())
    }
    queue.prime(4)
    runCurrent()
    assertEquals(listOf(4), started)
    assertEquals(4, queue.take(4).single().toInt())
    queue.cancel()
  }
}
