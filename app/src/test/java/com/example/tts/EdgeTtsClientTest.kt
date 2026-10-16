package com.example.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsClientTest {
  @Test fun catalogCoversManyLocalesWithoutHardcodingKhmerOnly() {
    val locales = EdgeTtsClient.STARTER_VOICES.map { it.locale }
    assertTrue(listOf("km-KH", "en-US", "fr-FR", "ja-JP", "th-TH").all { it in locales })
    assertEquals("km-KH-PisethNeural", EdgeTtsClient.PISETH)
    assertTrue(EdgeTtsClient.isValidVoice("en-US-JennyNeural"))
    assertFalse(EdgeTtsClient.isValidVoice("bad <name>"))
    val token = EdgeTtsClient.signature(1791970000L)
    assertTrue(Regex("[0-9A-F]{16}-[0-9A-F]{64}").matches(token))
    assertEquals(81, token.length)
    assertEquals(EdgeTtsClient.signature(1791970000L),
      EdgeTtsClient.signature(1791970080L))
  }

  @Test fun ssmlUsesVoiceLocaleAndEscapesText() {
    val markup = EdgeTtsClient.ssml("ភាសាខ្មែរ & <test>", EdgeTtsClient.PISETH, 1.25f, -10)
    assertTrue(markup.contains("xml:lang='km-KH'"))
    assertTrue(markup.contains("&amp; &lt;test&gt;"))
    assertTrue(markup.contains("rate='+25%'"))
    assertTrue(markup.contains("pitch='-10Hz'"))
    val english = EdgeTtsClient.ssml("Hello", "en-US-JennyNeural", 1f, 0)
    assertTrue(english.contains("xml:lang='en-US'"))
    assertTrue(english.contains("Hello"))
  }

  @Test fun rejectsInvalidMarkupAndLongText() {
    assertTrue(runCatching { EdgeTtsClient.ssml("text", "evil'><voice", 1f, 0) }.isFailure)
    assertTrue(runCatching { EdgeTtsClient.ssml("a".repeat(4000), EdgeTtsClient.PISETH, 1f, 0) }.isFailure)
  }

  @Test fun voiceSelectionKeepsLocaleAndPreferredVoice() {
    val voices = EdgeTtsClient.STARTER_VOICES
    assertEquals(EdgeTtsClient.PISETH,
      EdgeTtsClient.chooseVoice(voices, "km-KH", EdgeTtsClient.PISETH)?.name)
    assertEquals("en-US-JennyNeural",
      EdgeTtsClient.chooseVoice(voices, "en-US", "")?.name)
    assertNull(EdgeTtsClient.chooseVoice(voices, "ga-IE", ""))
  }

  @Test fun parsesBinaryAudioFrames() {
    val header = "Path:audio\r\nContent-Type:audio/mpeg\r\n"
      .toByteArray(Charsets.US_ASCII)
    val sound = byteArrayOf(1, 3, 5, 7, 9)
    val bytes = byteArrayOf((header.size ushr 8).toByte(),
      (header.size and 255).toByte()) + header + sound
    assertArrayEquals(sound, EdgeTtsClient.audioPayload(bytes))
    val withDelimiter = byteArrayOf((header.size ushr 8).toByte(),
      (header.size and 255).toByte()) + header + byteArrayOf(13, 10) + sound
    assertArrayEquals(sound, EdgeTtsClient.audioPayload(withDelimiter))
    assertNull(EdgeTtsClient.audioPayload(byteArrayOf(0, 2, 1)))
  }
}
