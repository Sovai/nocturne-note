package com.example.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.regex.Pattern
import java.util.zip.InflaterInputStream

object PdfTextExtractor {

  /**
   * Extracts human-readable text from a PDF input stream.
   * Handles plain streams, FlateDecode streams, Tj and TJ operators, and octal/escape decoding.
   */
  fun extractText(inputStream: InputStream): String {
    return try {
      val bytes = inputStream.readBytes()
      val extracted = StringBuilder()

      // Look for stream ... endstream blocks in PDF binary
      val streamPattern = Pattern.compile("stream[\\r\\n]+([\\s\\S]*?)[\\r\\n]+endstream")
      val textContent = String(bytes, Charsets.ISO_8859_1)
      val matcher = streamPattern.matcher(textContent)

      var foundAnyStream = false
      while (matcher.find()) {
        foundAnyStream = true
        val streamDataRaw = matcher.group(1) ?: continue
        val streamBytes = streamDataRaw.toByteArray(Charsets.ISO_8859_1)

        // Attempt decompression (FlateDecode)
        val decompressedBytes = tryDecompress(streamBytes) ?: streamBytes
        val streamText = String(decompressedBytes, Charsets.ISO_8859_1)

        val pageText = extractTextFromStream(streamText)
        if (pageText.isNotBlank()) {
          if (extracted.isNotEmpty()) extracted.append("\n\n")
          extracted.append(pageText.trim())
        }
      }

      val result = extracted.toString().trim()
      if (result.isNotBlank()) {
        result
      } else {
        // Fallback: search for strings in raw text
        val rawMatches = extractTextFromStream(textContent)
        if (rawMatches.isNotBlank()) {
          rawMatches
        } else {
          "[Imported PDF document: No readable text stream found. The document may contain scanned image pages.]"
        }
      }
    } catch (e: Exception) {
      "[Unable to parse PDF content: ${e.localizedMessage ?: "Unknown error"}]"
    }
  }

  private fun tryDecompress(data: ByteArray): ByteArray? {
    return try {
      val inflater = InflaterInputStream(ByteArrayInputStream(data))
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(4096)
      var len: Int
      while (inflater.read(buffer).also { len = it } != -1) {
        out.write(buffer, 0, len)
      }
      out.toByteArray()
    } catch (_: Exception) {
      null
    }
  }

  private fun extractTextFromStream(streamContent: String): String {
    val result = StringBuilder()
    // Match BT (Begin Text) ... ET (End Text) blocks
    val btPattern = Pattern.compile("BT([\\s\\S]*?)ET")
    val btMatcher = btPattern.matcher(streamContent)

    while (btMatcher.find()) {
      val textBlock = btMatcher.group(1) ?: continue
      val lines = textBlock.split("\r\n", "\n", "\r")

      for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        // Check for (string) Tj or ' or "
        val tjMatcher = Pattern.compile("\\((.*?)\\)\\s*(?:Tj|'|\")").matcher(trimmed)
        while (tjMatcher.find()) {
          val rawString = tjMatcher.group(1) ?: ""
          result.append(unescapePdfString(rawString)).append(" ")
        }

        // Check for [(string1) ... (string2)] TJ array
        val tjArrayMatcher = Pattern.compile("\\[([^\\]]*)\\]\\s*TJ").matcher(trimmed)
        while (tjArrayMatcher.find()) {
          val arrayContent = tjArrayMatcher.group(1) ?: ""
          val itemMatcher = Pattern.compile("\\((.*?)\\)").matcher(arrayContent)
          while (itemMatcher.find()) {
            val part = itemMatcher.group(1) ?: ""
            result.append(unescapePdfString(part))
          }
          result.append(" ")
        }

        // Newline operator T* or Td with negative y
        if (trimmed == "T*" || trimmed.matches(Regex("-?\\d+\\.?\\d*\\s+-\\d+\\.?\\d*\\s+Td"))) {
          result.append("\n")
        }
      }
      result.append("\n")
    }

    return cleanFormatting(result.toString())
  }

  private fun unescapePdfString(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c == '\\' && i + 1 < s.length) {
        val next = s[i + 1]
        when (next) {
          'n' -> { sb.append('\n'); i += 2 }
          'r' -> { sb.append('\r'); i += 2 }
          't' -> { sb.append('\t'); i += 2 }
          'b' -> { sb.append('\b'); i += 2 }
          'f' -> { sb.append('\u000C'); i += 2 }
          '\\', '(', ')' -> { sb.append(next); i += 2 }
          in '0'..'7' -> {
            // Octal escape \ddd
            var octalStr = ""
            var j = i + 1
            while (j < s.length && j < i + 4 && s[j] in '0'..'7') {
              octalStr += s[j]
              j++
            }
            try {
              val code = octalStr.toInt(8)
              sb.append(code.toChar())
            } catch (_: Exception) {
              sb.append('?')
            }
            i = j
          }
          else -> { sb.append(next); i += 2 }
        }
      } else {
        sb.append(c)
        i++
      }
    }
    return sb.toString()
  }

  private fun cleanFormatting(text: String): String {
    return text.lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n")
  }
}
