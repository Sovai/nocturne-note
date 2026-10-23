package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSettingsTest {
  @Test fun defaultsToEdgeAndLocalFallback() {
    val state = SpeechPreferences()
    assertEquals(SpeechEngine.EDGE, state.engine)
    assertEquals("auto", state.languageTag)
    assertEquals("", state.edgeVoice)
    assertFalse(state.backgroundPlayback)
    assertEquals(1.0f, state.speed)
  }

  @Test fun onlyTwoSupportedEnginesRemain() {
    assertEquals(listOf(SpeechEngine.EDGE, SpeechEngine.ANDROID),
      SpeechEngine.entries.toList())
    assertTrue(SpeechEngine.EDGE.label.contains("Edge"))
  }
}
