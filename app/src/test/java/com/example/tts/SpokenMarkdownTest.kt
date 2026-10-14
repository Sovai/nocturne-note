package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenMarkdownTest {
  @Test fun stripsMarkdownButKeepsKhmer() {
    val paragraphs = SpokenMarkdown.paragraphs(
      "# រឿងព្រេង\n\n**សួស្តី** [មិត្តភក្តិ](https://example.com)\n"
    )
    assertEquals(listOf("រឿងព្រេង", "សួស្តី មិត្តភក្តិ"), paragraphs)
  }

  @Test fun doesNotReadUrlOrFormattingCharacters() {
    val paragraphs = SpokenMarkdown.paragraphs("Hello **world** https://example.com")
    assertEquals(listOf("Hello world"), paragraphs)
  }

  @Test fun detectsKhmerText() {
    assertTrue(SpokenMarkdown.containsKhmer("សួស្តី"))
    assertFalse(SpokenMarkdown.containsKhmer("Hello"))
  }

  @Test fun preservesKhmerCombiningClustersWhenChunking() {
    val original = "ក".repeat(259) + "កា" + "ក".repeat(300)
    val chunks = SpokenMarkdown.paragraphs(original)
    assertEquals(original, chunks.joinToString(""))
    assertTrue(chunks.all { it.length <= 260 || it.length == 261 })
    assertTrue(chunks.none { it.startsWith("ា") })
  }

  @Test fun chunksLongParagraphs() {
    val paragraphs = SpokenMarkdown.paragraphs("ក".repeat(600))
    assertEquals(3, paragraphs.size)
    assertTrue(paragraphs.all { it.length <= 260 })
  }
}
