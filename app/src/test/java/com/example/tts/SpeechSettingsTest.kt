package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSettingsTest {
  @Test fun defaultsAreOfflineAndBackgroundIsOptIn() {
    val state = SpeechPreferences()
    assertEquals(SpeechEngine.AUTO, state.engine)
    assertFalse(state.backgroundPlayback)
    assertEquals(1.0f, state.speed)
  }

  @Test fun voicePresetsHaveStableCodes() {
    assertEquals("auto", SpeechVoice.AUTO.code)
    assertEquals("elder", SpeechVoice.ELDER.code)
    assertTrue(SpeechEngine.OMNIVOICE.label.contains("on-device"))
  }
}
