package com.example.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure editing operations. Offsets are UTF-16, matching Compose's TextRange.
 * A collapsed selection edits at the caret; a range edits the selected text.
 */
object MarkdownEditorActions {
  fun wrap(
    field: TextFieldValue,
    prefix: String,
    suffix: String = "",
    placeholder: String = ""
  ): TextFieldValue {
    val start = field.selection.min.coerceIn(0, field.text.length)
    val end = field.selection.max.coerceIn(0, field.text.length)
    val inner = field.text.substring(start, end).ifEmpty { placeholder }
    val replacement = prefix + inner + suffix
    val innerStart = start + prefix.length
    return TextFieldValue(
      text = field.text.replaceRange(start, end, replacement),
      selection = TextRange(innerStart, innerStart + inner.length)
    )
  }

  /** Prefix the line containing the caret, or each line intersected by a selection. */
  fun prefixLines(
    field: TextFieldValue,
    prefix: (Int) -> String,
    placeholder: String = ""
  ): TextFieldValue {
    val text = field.text
    val start = field.selection.min.coerceIn(0, text.length)
    val end = field.selection.max.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    // Do not format the next line when the selection ends precisely at its start.
    val lastSelectedIndex = if (end > start && text[end - 1] == '\n') end - 1 else end
    val lineEnd = text.indexOf('\n', lastSelectedIndex).let { if (it < 0) text.length else it }
    val original = text.substring(lineStart, lineEnd)
    val emptyCaretLine = start == end && original.isEmpty()
    val replaced = original.split('\n').mapIndexed { index, line ->
      prefix(index) + if (emptyCaretLine) placeholder else line
    }.joinToString("\n")
    val newText = text.replaceRange(lineStart, lineEnd, replaced)
    val newSelection = when {
      emptyCaretLine -> {
        val from = lineStart + prefix(0).length
        TextRange(from, from + placeholder.length)
      }
      start == end -> TextRange((start + prefix(0).length).coerceAtMost(newText.length))
      else -> TextRange(lineStart, lineStart + replaced.length)
    }
    return TextFieldValue(text = newText, selection = newSelection)
  }

  /** Insert a block at the selection/caret, preserving surrounding paragraphs. */
  fun insertBlock(field: TextFieldValue, block: String): TextFieldValue {
    val start = field.selection.min.coerceIn(0, field.text.length)
    val end = field.selection.max.coerceIn(0, field.text.length)
    val before = field.text.substring(0, start)
    val after = field.text.substring(end)
    val lead = if (before.isNotEmpty() && !before.endsWith("\n")) "\n" else ""
    val trail = if (after.isNotEmpty() && !after.startsWith("\n")) "\n" else ""
    val insertion = lead + block + trail
    return TextFieldValue(
      text = before + insertion + after,
      selection = TextRange(start + insertion.length)
    )
  }
}
