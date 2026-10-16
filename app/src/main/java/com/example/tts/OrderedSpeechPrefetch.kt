package com.example.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Keep the current passage and [lookahead] future passages rendering concurrently.
 * Playback always consumes in order; no work is launched for the whole document.
 *
 * The child Deferreds contain Result so a failed prefetched passage does not
 * cancel its parent reading session before the failure is consumed in order.
 */
internal class OrderedSpeechPrefetch(
  private val scope: CoroutineScope,
  private val total: Int,
  private val lookahead: Int = 3,
  concurrency: Int = 2,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val synthesize: suspend (Int) -> ByteArray
) {
  init {
    require(total >= 0)
    require(lookahead in 1..5)
    require(concurrency in 1..3)
  }

  private val permits = Semaphore(concurrency)
  private val inFlight = mutableMapOf<Int, Deferred<Result<ByteArray>>>()

  private fun schedule(index: Int) {
    if (index !in 0 until total || inFlight.containsKey(index)) return
    inFlight[index] = scope.async(dispatcher) {
      try {
        Result.success(permits.withPermit { synthesize(index) })
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        Result.failure(failure)
      }
    }
  }

  fun prime(index: Int) {
    if (index !in 0 until total) return
    for (next in index..minOf(total - 1, index + lookahead)) schedule(next)
  }

  /** Removes consumed audio from the in-memory queue and schedules another ahead. */
  suspend fun take(index: Int): ByteArray {
    check(index in 0 until total)
    if (!inFlight.containsKey(index)) prime(index)
    val task = checkNotNull(inFlight.remove(index))
    val audio = task.await().getOrThrow()
    schedule(index + lookahead + 1)
    return audio
  }

  /** True when audio generation has ended (success or failure), without waiting. */
  fun isReady(index: Int): Boolean = inFlight[index]?.isCompleted == true

  fun cancel() {
    inFlight.values.forEach { it.cancel() }
    inFlight.clear()
  }
}
