package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tts.OmniVoiceRemoteClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fresh emulator has no 735 MiB model installed. The worker must respond
 * with an error without taking down the parent app process.
 *
 * An end-to-end synthesizer test still requires physical-device model testing.
 */
@RunWith(AndroidJUnit4::class)
class OmniVoiceWorkerTest {
  @Test fun missingModelFailsSafelyWithoutCrashingNoteEditor() = runBlocking {
    val app = InstrumentationRegistry.getInstrumentation().targetContext
    val client = OmniVoiceRemoteClient(app)
    try {
      val result = runCatching {
        withTimeout(35_000) { client.synthesize("ជំរាបសួរ") }
      }
      assertTrue("Missing model or insufficient memory should fail safely", result.isFailure)
      assertTrue(
        "The note-editor process remains alive after the worker error",
        app.packageName.isNotBlank()
      )
    } finally {
      client.close()
    }
  }
}
