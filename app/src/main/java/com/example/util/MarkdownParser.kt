package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

sealed class MarkdownBlock {
  data class Heading(val level: Int, val text: String) : MarkdownBlock()
  data class Paragraph(val text: String) : MarkdownBlock()
  data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
  data class Blockquote(val text: String) : MarkdownBlock()
  data class BulletItem(val level: Int, val text: String) : MarkdownBlock()
  data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
  data class TaskItem(val isChecked: Boolean, val text: String) : MarkdownBlock()
  data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
  object Divider : MarkdownBlock()
}

object MarkdownParser {

  /**
   * Converts markdown text into structured blocks for Compose rendering.
   */
  fun parseBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
      val line = lines[i]
      val trimmed = line.trim()

      when {
        trimmed.isEmpty() -> {
          i++
        }

        // Code block ```
        trimmed.startsWith("```") -> {
          val lang = trimmed.removePrefix("```").trim()
          val codeLines = mutableListOf<String>()
          i++
          while (i < lines.size && !lines[i].trim().startsWith("```")) {
            codeLines.add(lines[i])
            i++
          }
          if (i < lines.size && lines[i].trim().startsWith("```")) {
            i++ // consume closing ```
          }
          blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
        }

        // Headings
        trimmed.startsWith("#") -> {
          var level = 0
          while (level < trimmed.length && trimmed[level] == '#') {
            level++
          }
          if (level <= 6 && level < trimmed.length && trimmed[level] == ' ') {
            val text = trimmed.substring(level + 1).trim()
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
          } else {
            blocks.add(MarkdownBlock.Paragraph(line))
            i++
          }
        }

        // Divider
        trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
          blocks.add(MarkdownBlock.Divider)
          i++
        }

        // Blockquote
        trimmed.startsWith(">") -> {
          val quoteLines = mutableListOf<String>()
          while (i < lines.size && lines[i].trim().startsWith(">")) {
            quoteLines.add(lines[i].trim().removePrefix(">").trim())
            i++
          }
          blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
        }

        // Task Item: - [ ] or - [x]
        trimmed.matches(Regex("^[-*]\\s+\\[[ xX]\\]\\s+.*")) -> {
          val isChecked = trimmed.contains("[x]", ignoreCase = true)
          val text = trimmed.replace(Regex("^[-*]\\s+\\[[ xX]\\]\\s+"), "")
          blocks.add(MarkdownBlock.TaskItem(isChecked, text))
          i++
        }

        // Unordered List: - or *
        trimmed.matches(Regex("^[-*•]\\s+.*")) -> {
          val indent = line.takeWhile { it == ' ' }.length / 2
          val text = trimmed.replace(Regex("^[-*•]\\s+"), "")
          blocks.add(MarkdownBlock.BulletItem(indent, text))
          i++
        }

        // Ordered List: 1.
        trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
          val match = Regex("^(\\d+\\.)\\s+(.*)").find(trimmed)
          if (match != null) {
            val num = match.groupValues[1]
            val text = match.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, text))
          } else {
            blocks.add(MarkdownBlock.Paragraph(line))
          }
          i++
        }

        // Table detection: line contains |
        trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|[\\s-:|]+\\|$")) -> {
          val headerCells = trimmed.split("|").filter { it.isNotBlank() }.map { it.trim() }
          i += 2 // skip header and separator
          val rows = mutableListOf<List<String>>()
          while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
            val rowCells = lines[i].trim().split("|").filter { it.isNotBlank() }.map { it.trim() }
            rows.add(rowCells)
            i++
          }
          blocks.add(MarkdownBlock.Table(headerCells, rows))
        }

        // Default Paragraph
        else -> {
          val pLines = mutableListOf<String>()
          while (i < lines.size && lines[i].trim().isNotEmpty() &&
            !lines[i].trim().startsWith("#") &&
            !lines[i].trim().startsWith("```") &&
            !lines[i].trim().startsWith(">") &&
            !lines[i].trim().startsWith("|") &&
            !lines[i].trim().matches(Regex("^[-*•]\\s+.*")) &&
            !lines[i].trim().matches(Regex("^\\d+\\.\\s+.*"))
          ) {
            pLines.add(lines[i])
            i++
          }
          blocks.add(MarkdownBlock.Paragraph(pLines.joinToString("\n")))
        }
      }
    }

    return blocks
  }

  /**
   * Builds an AnnotatedString from markdown inline formatting:
   * **bold**, *italic*, ~~strikethrough~~, `code`, and [link](url).
   */
  fun parseInlineMarkdown(
    text: String,
    codeBgColor: Color = Color(0x3338BDF8),
    codeTextColor: Color = Color(0xFF38BDF8),
    linkColor: Color = Color(0xFF38BDF8)
  ): AnnotatedString {
    return buildAnnotatedString {
      var currentIndex = 0
      val length = text.length

      while (currentIndex < length) {
        val remaining = text.substring(currentIndex)

        // Combined bold + italic: applying both toolbar actions creates ***text***.
        // Parse the triple delimiter first or the bold rule leaves stray asterisks.
        if (remaining.startsWith("***")) {
          val endCombined = remaining.indexOf("***", startIndex = 3)
          if (endCombined != -1) {
            val combinedContent = remaining.substring(3, endCombined)
            val startPos = this.length
            append(combinedContent)
            addStyle(
              SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
              startPos,
              this.length
            )
            currentIndex += endCombined + 3
            continue
          }
        }

        // Bold: **text**
        if (remaining.startsWith("**")) {
          val endBold = remaining.indexOf("**", startIndex = 2)
          if (endBold != -1) {
            val boldContent = remaining.substring(2, endBold)
            val startPos = this.length
            append(boldContent)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), startPos, this.length)
            currentIndex += endBold + 2
            continue
          }
        }

        // Strikethrough: ~~text~~
        if (remaining.startsWith("~~")) {
          val endStrike = remaining.indexOf("~~", startIndex = 2)
          if (endStrike != -1) {
            val strikeContent = remaining.substring(2, endStrike)
            val startPos = this.length
            append(strikeContent)
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), startPos, this.length)
            currentIndex += endStrike + 2
            continue
          }
        }

        // Underline: <u>text</u> or <ins>text</ins>
        if (remaining.startsWith("<u>", ignoreCase = true)) {
          val endU = remaining.indexOf("</u>", startIndex = 3, ignoreCase = true)
          if (endU != -1) {
            val uContent = remaining.substring(3, endU)
            val startPos = this.length
            append(uContent)
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), startPos, this.length)
            currentIndex += endU + 4
            continue
          }
        }
        if (remaining.startsWith("<ins>", ignoreCase = true)) {
          val endIns = remaining.indexOf("</ins>", startIndex = 5, ignoreCase = true)
          if (endIns != -1) {
            val insContent = remaining.substring(5, endIns)
            val startPos = this.length
            append(insContent)
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), startPos, this.length)
            currentIndex += endIns + 6
            continue
          }
        }

        // Inline Code: `code`
        if (remaining.startsWith("`")) {
          val endCode = remaining.indexOf("`", startIndex = 1)
          if (endCode != -1) {
            val codeContent = remaining.substring(1, endCode)
            val startPos = this.length
            append(" $codeContent ")
            addStyle(
              SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBgColor,
                color = codeTextColor,
                fontWeight = FontWeight.Medium
              ),
              startPos,
              this.length
            )
            currentIndex += endCode + 1
            continue
          }
        }

        // Italic: *text* or _text_
        if (remaining.startsWith("*") && !remaining.startsWith("**")) {
          val endItalic = remaining.indexOf("*", startIndex = 1)
          if (endItalic != -1) {
            val italicContent = remaining.substring(1, endItalic)
            val startPos = this.length
            append(italicContent)
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), startPos, this.length)
            currentIndex += endItalic + 1
            continue
          }
        }

        // Link: [text](url)
        if (remaining.startsWith("[")) {
          val closeBracket = remaining.indexOf("]")
          if (closeBracket != -1 && closeBracket + 1 < remaining.length && remaining[closeBracket + 1] == '(') {
            val closeParen = remaining.indexOf(")", startIndex = closeBracket + 2)
            if (closeParen != -1) {
              val linkText = remaining.substring(1, closeBracket)
              val startPos = this.length
              append(linkText)
              addStyle(
                SpanStyle(
                  color = linkColor,
                  textDecoration = TextDecoration.Underline,
                  fontWeight = FontWeight.Medium
                ),
                startPos,
                this.length
              )
              currentIndex += closeParen + 1
              continue
            }
          }
        }

        // Normal character
        append(text[currentIndex])
        currentIndex++
      }
    }
  }

  /**
   * Transforms markdown text to clean HTML.
   */
  fun markdownToHtml(content: String): String {
    val blocks = parseBlocks(content)
    val sb = StringBuilder()

    for (block in blocks) {
      when (block) {
        is MarkdownBlock.Heading -> {
          val tag = "h${block.level}"
          sb.append("<$tag>").append(escapeHtml(block.text)).append("</$tag>\n")
        }
        is MarkdownBlock.Paragraph -> {
          sb.append("<p>").append(formatInlineHtml(block.text)).append("</p>\n")
        }
        is MarkdownBlock.CodeBlock -> {
          val langClass = if (block.language.isNotBlank()) " class=\"language-${escapeHtml(block.language)}\"" else ""
          sb.append("<pre><code$langClass>").append(escapeHtml(block.code)).append("</code></pre>\n")
        }
        is MarkdownBlock.Blockquote -> {
          sb.append("<blockquote><p>").append(formatInlineHtml(block.text)).append("</p></blockquote>\n")
        }
        is MarkdownBlock.BulletItem -> {
          sb.append("<ul><li>").append(formatInlineHtml(block.text)).append("</li></ul>\n")
        }
        is MarkdownBlock.NumberedItem -> {
          sb.append("<ol><li>").append(formatInlineHtml(block.text)).append("</li></ol>\n")
        }
        is MarkdownBlock.TaskItem -> {
          val checkedAttr = if (block.isChecked) " checked" else ""
          sb.append("<div class=\"task-item\"><input type=\"checkbox\" disabled$checkedAttr> ")
            .append(formatInlineHtml(block.text)).append("</div>\n")
        }
        is MarkdownBlock.Table -> {
          sb.append("<table>\n<thead><tr>")
          block.headers.forEach { sb.append("<th>").append(escapeHtml(it)).append("</th>") }
          sb.append("</tr></thead>\n<tbody>\n")
          block.rows.forEach { row ->
            sb.append("<tr>")
            row.forEach { cell -> sb.append("<td>").append(escapeHtml(cell)).append("</td>") }
            sb.append("</tr>\n")
          }
          sb.append("</tbody></table>\n")
        }
        MarkdownBlock.Divider -> {
          sb.append("<hr>\n")
        }
      }
    }

    return sb.toString()
  }

  private fun formatInlineHtml(text: String): String {
    var s = escapeHtml(text)
    // Bold
    s = s.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
    // Italic
    s = s.replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
    // Strike
    s = s.replace(Regex("~~(.*?)~~"), "<del>$1</del>")
    // Inline code
    s = s.replace(Regex("`([^`]+)`"), "<code>$1</code>")
    // Underline
    s = s.replace(Regex("&lt;u&gt;(.*?)&lt;/u&gt;", RegexOption.IGNORE_CASE), "<u>$1</u>")
    s = s.replace(Regex("&lt;ins&gt;(.*?)&lt;/ins&gt;", RegexOption.IGNORE_CASE), "<u>$1</u>")
    // Links
    s = s.replace(Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)"), "<a href=\"$2\" target=\"_blank\">$1</a>")
    return s
  }

  private fun escapeHtml(s: String): String {
    return s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}
