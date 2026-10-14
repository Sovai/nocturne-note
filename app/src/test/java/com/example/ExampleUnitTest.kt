package com.example

import com.example.data.model.NoteEntity
import com.example.data.model.NoteVersionEntity
import com.example.util.DocumentExporter
import com.example.util.MarkdownBlock
import com.example.util.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testNoteEntityWordCountCalculation() {
    val note = NoteEntity(
      title = "Test Note",
      content = "The quick brown fox jumps over the lazy dog"
    )
    val (words, chars) = note.computeCounts()
    assertEquals(9, words)
    assertEquals(43, chars)
  }

  @Test
  fun testMarkdownBlockParsing() {
    val markdown = """
# Heading 1
Some introductory paragraph.

> A wise quote

```kotlin
val a = 1
```

- [x] Done item
- [ ] Todo item
- Bullet item
1. Numbered item
""".trimIndent()

    val blocks = MarkdownParser.parseBlocks(markdown)
    assertTrue(blocks.any { it is MarkdownBlock.Heading && it.level == 1 && it.text == "Heading 1" })
    assertTrue(blocks.any { it is MarkdownBlock.Blockquote && it.text.contains("A wise quote") })
    assertTrue(blocks.any { it is MarkdownBlock.CodeBlock && it.language == "kotlin" })
    assertTrue(blocks.any { it is MarkdownBlock.TaskItem && it.isChecked })
    assertTrue(blocks.any { it is MarkdownBlock.TaskItem && !it.isChecked })
    assertTrue(blocks.any { it is MarkdownBlock.BulletItem && it.text == "Bullet item" })
    assertTrue(blocks.any { it is MarkdownBlock.NumberedItem && it.text == "Numbered item" })
  }

  @Test
  fun testUnderlineAndInlineFormatting() {
    val annotated = MarkdownParser.parseInlineMarkdown("Normal text <u>underlined text</u> and **bold**")
    assertNotNull(annotated)
    assertTrue(annotated.text.contains("underlined text"))
    assertTrue(annotated.text.contains("bold"))

    val html = DocumentExporter.generateHtml("Test Underline", "<u>underlined text</u>")
    assertTrue(html.contains("<u>underlined text</u>"))
  }

  @Test
  fun testMarkdownToHtmlExport() {
    val content = "# Hello World\n**Bold text** and `code`."
    val html = DocumentExporter.generateHtml("My Doc", content)
    assertTrue(html.contains("<title>My Doc</title>"))
    assertTrue(html.contains("<h1>Hello World</h1>"))
    assertTrue(html.contains("<strong>Bold text</strong>"))
    assertTrue(html.contains("<code>code</code>"))
    assertTrue(html.contains("NocturneNote"))
  }

  @Test
  fun testNoteVersionEntityCreation() {
    val version = NoteVersionEntity(
      noteId = 1L,
      title = "Version 1",
      content = "Sample content for testing autosave snapshot",
      wordCount = 6,
      charCount = 44,
      reason = "Autosave"
    )
    assertEquals(1L, version.noteId)
    assertEquals("Version 1", version.title)
    assertEquals("Autosave", version.reason)
  }
}
