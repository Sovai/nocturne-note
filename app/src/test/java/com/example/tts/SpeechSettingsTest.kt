package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSettingsTest {
  @Test fun privateLanUrlAllowed() {
    assertEquals("http://192.168.1.25:8765", validatedLocalServer("http://192.168.1.25:8765"))
    assertEquals("http://10.0.0.3:8080", validatedLocalServer("http://10.0.0.3:8080"))
    assertEquals("http://172.19.0.4:8765", validatedLocalServer("http://172.19.0.4:8765"))
  }
  @Test fun publicAndUntrustedEndpointsRejected() {
    listOf("https://example.com:8765", "http://8.8.8.8:8765",
      "http://evil.com:8765", "http://192.168.1.20:8765/redirect",
      "http://192.168.1.20:8765?a=1", "http://user:pass@10.0.0.1:8765",
      "http://192.168.1.20:8765#frag", "http://256.168.1.25:8765")
      .forEach { assertNull("Should reject $it", validatedLocalServer(it)) }
  }
  @Test fun needsLongTokenAndPrivateAddress() {
    assertFalse(SpeechPreferences(serverUrl = "http://10.0.0.5:8765").serverConfigured)
    assertTrue(SpeechPreferences(serverUrl = "http://10.0.0.5:8765",
      serverToken = "very-long-token-123456").serverConfigured)
  }
}
