package com.example.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditorActionsTest {
  @Test fun wrapsSelectedTextInsteadOfAppending() {
    val field = TextFieldValue("hello world again", TextRange(6, 11))
    val result = MarkdownEditorActions.wrap(field, "**", "**", "bold text")
    assertEquals("hello **world** again", result.text)
    assertEquals(TextRange(8, 13), result.selection)
  }

  @Test fun wrapsAtCaretAndSelectsPlaceholder() {
    val result = MarkdownEditorActions.wrap(
      TextFieldValue("Hello end", TextRange(6)),
      "*", "*", "italic text"
    )
    assertEquals("Hello *italic text*end", result.text)
    assertEquals("italic text", result.text.substring(result.selection.min, result.selection.max))
  }

  @Test fun numbersEverySelectedLineWithoutChangingLaterText() {
    val result = MarkdownEditorActions.prefixLines(
      TextFieldValue("alpha\nbeta\ngamma\ntail", TextRange(0, 16)),
      { index -> (index + 1).toString() + ". " }
    )
    assertEquals("1. alpha\n2. beta\n3. gamma\ntail", result.text)
  }

  @Test fun doesNotFormatNextLineWhenSelectionEndsAtItsStart() {
    val result = MarkdownEditorActions.prefixLines(
      TextFieldValue("one\ntwo", TextRange(0, 4)),
      { "- " }
    )
    assertEquals("- one\ntwo", result.text)
  }

  @Test fun formatsLineAtCursorNotAtEndOfDocument() {
    val result = MarkdownEditorActions.prefixLines(
      TextFieldValue("first\nsecond\nthird", TextRange(9)),
      { "# " }
    )
    assertEquals("first\n# second\nthird", result.text)
    assertEquals(TextRange(11), result.selection)
  }

  @Test fun insertsTableOrDividerAtCaretNotAtEnd() {
    val result = MarkdownEditorActions.insertBlock(
      TextFieldValue("first\nlast", TextRange(6)),
      "| A | B |"
    )
    assertEquals("first\n| A | B |\nlast", result.text)
  }

  @Test fun includesKhmerSelectionInFormatting() {
    val content = "ចំណងជើង អត្ថបទ សាកល្បង"
    val start = content.indexOf("អត្ថបទ")
    val result = MarkdownEditorActions.wrap(
      TextFieldValue(content, TextRange(start, start + "អត្ថបទ".length)),
      "~~", "~~"
    )
    assertEquals("ចំណងជើង ~~អត្ថបទ~~ សាកល្បង", result.text)
  }
}
