package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentExporter {

  enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    PDF("pdf", "application/pdf", "PDF Document (.pdf)"),
    HTML("html", "text/html", "Web HTML (.html)"),
    MARKDOWN("md", "text/markdown", "Markdown (.md)"),
    PLAIN_TEXT("txt", "text/plain", "Plain Text (.txt)")
  }

  /**
   * Generates a styled multi-page PDF document to an OutputStream.
   */
  fun exportToPdf(title: String, content: String, outputStream: OutputStream) {
    val pdfDocument = PdfDocument()
    val pageWidth = 595 // A4 width in points
    val pageHeight = 842 // A4 height in points
    val margin = 48f
    val contentWidth = pageWidth - (margin * 2)

    val titlePaint = Paint().apply {
      color = Color.rgb(15, 23, 42)
      textSize = 20f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
    }

    val heading1Paint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      textSize = 16f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
    }

    val heading2Paint = Paint().apply {
      color = Color.rgb(51, 65, 85)
      textSize = 14f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
    }

    val bodyPaint = Paint().apply {
      color = Color.rgb(51, 65, 85)
      textSize = 11f
      typeface = Typeface.DEFAULT
      isAntiAlias = true
    }

    val codePaint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      textSize = 10f
      typeface = Typeface.MONOSPACE
      isAntiAlias = true
    }

    val metaPaint = Paint().apply {
      color = Color.rgb(148, 163, 184)
      textSize = 9f
      typeface = Typeface.DEFAULT
      isAntiAlias = true
    }

    val dividerPaint = Paint().apply {
      color = Color.rgb(226, 232, 240)
      strokeWidth = 1f
    }

    val codeBgPaint = Paint().apply {
      color = Color.rgb(241, 245, 249)
    }

    // Split raw lines and prepare wrapped text items
    data class RenderItem(
      val text: String,
      val paint: Paint,
      val isCode: Boolean = false,
      val isDivider: Boolean = false,
      val extraSpacing: Float = 4f
    )

    val items = mutableListOf<RenderItem>()

    // Title & Date Header
    items.add(RenderItem(title.ifBlank { "Untitled Document" }, titlePaint, extraSpacing = 6f))
    val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
    items.add(RenderItem("Exported from NocturneNote • $dateStr", metaPaint, extraSpacing = 16f))
    items.add(RenderItem("", dividerPaint, isDivider = true, extraSpacing = 16f))

    var inCodeBlock = false
    val lines = content.lines()

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.startsWith("```")) {
        inCodeBlock = !inCodeBlock
        continue
      }

      if (inCodeBlock) {
        val wrapped = wrapText(line, codePaint, contentWidth - 16f)
        wrapped.forEach { items.add(RenderItem(it, codePaint, isCode = true, extraSpacing = 2f)) }
        continue
      }

      when {
        trimmed.startsWith("# ") -> {
          val headingText = trimmed.removePrefix("# ").trim()
          wrapText(headingText, heading1Paint, contentWidth).forEach {
            items.add(RenderItem(it, heading1Paint, extraSpacing = 6f))
          }
        }
        trimmed.startsWith("## ") -> {
          val headingText = trimmed.removePrefix("## ").trim()
          wrapText(headingText, heading2Paint, contentWidth).forEach {
            items.add(RenderItem(it, heading2Paint, extraSpacing = 5f))
          }
        }
        trimmed.startsWith("### ") -> {
          val headingText = trimmed.removePrefix("### ").trim()
          wrapText(headingText, heading2Paint, contentWidth).forEach {
            items.add(RenderItem(it, heading2Paint, extraSpacing = 4f))
          }
        }
        trimmed == "---" || trimmed == "***" -> {
          items.add(RenderItem("", dividerPaint, isDivider = true, extraSpacing = 10f))
        }
        trimmed.isEmpty() -> {
          items.add(RenderItem("", bodyPaint, extraSpacing = 8f))
        }
        else -> {
          // Clean basic markdown symbols for print
          val cleanLine = line
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
          val wrapped = wrapText(cleanLine, bodyPaint, contentWidth)
          wrapped.forEach { items.add(RenderItem(it, bodyPaint, extraSpacing = 3f)) }
        }
      }
    }

    // Paginate and draw onto PdfDocument pages
    var pageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    var yPos = margin

    fun drawFooter(pNum: Int) {
      val footerY = pageHeight - margin + 20f
      canvas.drawLine(margin, pageHeight - margin + 8f, pageWidth - margin, pageHeight - margin + 8f, dividerPaint)
      canvas.drawText("NocturneNote", margin, footerY, metaPaint)
      val pageLabel = "Page $pNum"
      val labelWidth = metaPaint.measureText(pageLabel)
      canvas.drawText(pageLabel, pageWidth - margin - labelWidth, footerY, metaPaint)
    }

    for (item in items) {
      val itemHeight = when {
        item.isDivider -> 1f + item.extraSpacing
        item.text.isEmpty() -> item.extraSpacing
        else -> item.paint.textSize + item.extraSpacing
      }

      if (yPos + itemHeight > pageHeight - margin - 10f) {
        // Finish current page
        drawFooter(pageNumber)
        pdfDocument.finishPage(page)

        // Start next page
        pageNumber++
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas
        yPos = margin
      }

      when {
        item.isDivider -> {
          canvas.drawLine(margin, yPos, pageWidth - margin, yPos, dividerPaint)
          yPos += itemHeight
        }
        item.text.isEmpty() -> {
          yPos += itemHeight
        }
        item.isCode -> {
          canvas.drawRect(margin, yPos - item.paint.textSize + 2f, pageWidth - margin, yPos + 4f, codeBgPaint)
          canvas.drawText(item.text, margin + 8f, yPos, item.paint)
          yPos += itemHeight
        }
        else -> {
          canvas.drawText(item.text, margin, yPos, item.paint)
          yPos += itemHeight
        }
      }
    }

    drawFooter(pageNumber)
    pdfDocument.finishPage(page)

    pdfDocument.writeTo(outputStream)
    pdfDocument.close()
  }

  private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isEmpty()) return listOf("")
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = StringBuilder()

    for (word in words) {
      val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
      val width = paint.measureText(testLine)
      if (width <= maxWidth) {
        currentLine = StringBuilder(testLine)
      } else {
        if (currentLine.isNotEmpty()) {
          lines.add(currentLine.toString())
        }
        currentLine = StringBuilder(word)
      }
    }
    if (currentLine.isNotEmpty()) {
      lines.add(currentLine.toString())
    }
    return if (lines.isEmpty()) listOf(text) else lines
  }

  /**
   * Generates a modern standalone HTML file with clean responsive typography and dark/light modes.
   */
  fun generateHtml(title: String, content: String): String {
    val escapedTitle = escapeHtml(title.ifBlank { "NocturneNote Document" })
    val bodyHtml = MarkdownParser.markdownToHtml(content)
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>$escapedTitle</title>
  <style>
    :root {
      --bg: #0b0f19;
      --surface: #111827;
      --text: #f1f5f9;
      --text-muted: #94a3b8;
      --accent: #38bdf8;
      --border: #1f293d;
      --code-bg: #1e293b;
      --code-text: #7dd3fc;
      --quote-bg: rgba(56, 189, 248, 0.08);
    }
    @media (prefers-color-scheme: light) {
      :root {
        --bg: #f8fafc;
        --surface: #ffffff;
        --text: #0f172a;
        --text-muted: #64748b;
        --accent: #0284c7;
        --border: #e2e8f0;
        --code-bg: #f1f5f9;
        --code-text: #0369a1;
        --quote-bg: rgba(2, 132, 199, 0.06);
      }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      line-height: 1.7;
      padding: 2.5rem 1.25rem;
      max-width: 760px;
      margin: 0 auto;
    }
    header {
      border-bottom: 1px solid var(--border);
      padding-bottom: 1.5rem;
      margin-bottom: 2rem;
    }
    h1.doc-title {
      font-size: 2.25rem;
      font-weight: 700;
      color: var(--text);
      line-height: 1.25;
      margin-bottom: 0.5rem;
    }
    .doc-meta {
      font-size: 0.875rem;
      color: var(--text-muted);
    }
    h1, h2, h3, h4, h5, h6 {
      color: var(--text);
      margin-top: 1.75rem;
      margin-bottom: 0.75rem;
      font-weight: 600;
    }
    h1 { font-size: 1.75rem; }
    h2 { font-size: 1.4rem; }
    h3 { font-size: 1.2rem; }
    p { margin-bottom: 1.2rem; font-size: 1.05rem; }
    blockquote {
      background: var(--quote-bg);
      border-left: 4px solid var(--accent);
      padding: 0.75rem 1.25rem;
      margin: 1.25rem 0;
      border-radius: 0 8px 8px 0;
      font-style: italic;
    }
    code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      background: var(--code-bg);
      color: var(--code-text);
      padding: 0.2rem 0.4rem;
      border-radius: 4px;
      font-size: 0.9em;
    }
    pre {
      background: var(--code-bg);
      padding: 1.2rem;
      border-radius: 8px;
      overflow-x: auto;
      margin: 1.25rem 0;
      border: 1px solid var(--border);
    }
    pre code { background: none; padding: 0; color: var(--text); }
    ul, ol { padding-left: 1.75rem; margin-bottom: 1.2rem; }
    li { margin-bottom: 0.35rem; }
    hr {
      border: 0;
      height: 1px;
      background: var(--border);
      margin: 2rem 0;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      margin: 1.5rem 0;
    }
    th, td {
      border: 1px solid var(--border);
      padding: 0.6rem 0.9rem;
      text-align: left;
    }
    th { background: var(--surface); font-weight: 600; }
    a { color: var(--accent); text-decoration: none; }
    a:hover { text-decoration: underline; }
    .task-item { list-style-type: none; margin-left: -1rem; }
    footer {
      margin-top: 3rem;
      border-top: 1px solid var(--border);
      padding-top: 1.5rem;
      font-size: 0.85rem;
      color: var(--text-muted);
      text-align: center;
    }
    @media print {
      body { background: white; color: black; padding: 0; }
      pre, blockquote { border-color: #ccc; }
    }
  </style>
</head>
<body>
  <header>
    <h1 class="doc-title">$escapedTitle</h1>
    <div class="doc-meta">Created with NocturneNote • $dateStr</div>
  </header>
  <main>
    $bodyHtml
  </main>
  <footer>
    Exported from NocturneNote — Focused Distraction-Free Editor
  </footer>
</body>
</html>
""".trimIndent()
  }

  fun exportToHtml(title: String, content: String, outputStream: OutputStream) {
    val html = generateHtml(title, content)
    outputStream.write(html.toByteArray(Charsets.UTF_8))
  }

  fun exportToText(content: String, outputStream: OutputStream) {
    outputStream.write(content.toByteArray(Charsets.UTF_8))
  }

  /**
   * Saves document to a cache file and creates an ACTION_SEND Intent with FileProvider Uri.
   */
  fun createShareIntent(context: Context, title: String, content: String, format: ExportFormat): Intent {
    val exportDir = File(context.cacheDir, "exports")
    if (!exportDir.exists()) exportDir.mkdirs()

    val safeTitle = (title.ifBlank { "NocturneNote" }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(exportDir, "$safeTitle.${format.extension}")

    FileOutputStream(file).use { out ->
      when (format) {
        ExportFormat.PDF -> exportToPdf(title, content, out)
        ExportFormat.HTML -> exportToHtml(title, content, out)
        ExportFormat.MARKDOWN, ExportFormat.PLAIN_TEXT -> exportToText(content, out)
      }
    }

    val uri: Uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file
    )

    return Intent(Intent.ACTION_SEND).apply {
      type = format.mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, if (format == ExportFormat.PLAIN_TEXT) content else title)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  private fun escapeHtml(s: String): String {
    return s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}
