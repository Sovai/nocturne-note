package com.example.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsClientTest {
  @Test fun khmerVoicesRemainExplicitAndNoKeyIsNeeded() {
    assertEquals(listOf("km-KH-PisethNeural", "km-KH-SreymomNeural"),
      EdgeTtsClient.KHMER_VOICES)
    val token = EdgeTtsClient.signature(1791970000L)
    assertTrue("Expected CS64-SHA256 Edge v2 signature: $token",
      Regex("[0-9A-F]{16}-[0-9A-F]{64}").matches(token))
    assertEquals(81, token.length)
    assertFalse("Old SHA256-only signature is no longer accepted", token.matches(Regex("[A-F0-9]{64}")))
    assertEquals(EdgeTtsClient.signature(1791970000L),
      EdgeTtsClient.signature(1791970080L))
  }

  @Test fun requestEscapesMarkupAndHonorsRatePitch() {
    val markup = EdgeTtsClient.ssml("ភាសាខ្មែរ & <test>", EdgeTtsClient.PISETH, 1.25f, -10)
    assertTrue(markup.contains("ភាសាខ្មែរ"))
    assertTrue(markup.contains("&amp; &lt;test&gt;"))
    assertTrue(markup.contains("rate='+25%'"))
    assertTrue(markup.contains("pitch='-10Hz'"))
    assertFalse(markup.contains("ភាសាខ្មែរ & <test>"))
  }

  @Test fun rejectsInvalidVoiceAndExcessiveText() {
    assertTrue(runCatching {
      EdgeTtsClient.ssml("test", "en-US-JennyNeural", 1f, 0)
    }.isFailure)
    assertTrue(runCatching {
      EdgeTtsClient.ssml("a".repeat(4000), EdgeTtsClient.PISETH, 1f, 0)
    }.isFailure)
  }

  @Test fun parsesBinaryAudioFrameWithoutIncludingHeaders() {
    val header = "Path:audio\r\nContent-Type:audio/mpeg\r\n"
      .toByteArray(Charsets.US_ASCII)
    val sound = byteArrayOf(1, 3, 5, 7, 9)
    val bytes = byteArrayOf((header.size ushr 8).toByte(),
      (header.size and 255).toByte()) + header + sound
    assertArrayEquals(sound, EdgeTtsClient.audioPayload(bytes))
    // Modern Edge binary frames contain CRLF between header bytes and audio.
    val withDelimiter = byteArrayOf((header.size ushr 8).toByte(),
      (header.size and 255).toByte()) + header + byteArrayOf(13, 10) + sound
    assertArrayEquals(sound, EdgeTtsClient.audioPayload(withDelimiter))
    assertNull(EdgeTtsClient.audioPayload(byteArrayOf(0, 2, 1)))
  }
}
