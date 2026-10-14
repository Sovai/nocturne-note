package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAudioProcessingTest {
  @Test fun removesLongQuietTailsButKeepsMargins() {
    val rate = 24_000
    val raw = FloatArray(rate * 2)
    for (i in 8000 until 12000) raw[i] = 0.15f
    val result = trimSpeechSilence(SpeechAudio(raw, rate))
    assertEquals(rate, result.sampleRate)
    assertTrue(result.samples.size < raw.size / 2)
    assertTrue(result.samples.size > 4000)
    assertTrue(result.samples.any { it == 0.15f })
  }

  @Test fun emptyAudioAndQuietAudioDoNotCrash() {
    assertTrue(trimSpeechSilence(SpeechAudio(floatArrayOf(), 24000)).samples.isEmpty())
    val zeros = FloatArray(512)
    assertEquals(512, trimSpeechSilence(SpeechAudio(zeros, 24000)).samples.size)
  }
}
