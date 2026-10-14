package com.example.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Styling only; the original characters and IME/selection offsets are preserved.
private fun Char.isKhmer(): Boolean =
  this in '\u1780'..'\u17FF' || this in '\u19E0'..'\u19FF'

fun AnnotatedString.withKhmerFont(): AnnotatedString = buildAnnotatedString {
  append(this@withKhmerFont)
  val content = this@withKhmerFont.text
  var start = -1

  for (i in content.indices) {
    val khmer = content[i].isKhmer()
    val joiner = (content[i] == '\u200C' || content[i] == '\u200D') && start >= 0
    if (khmer || joiner) {
      if (start < 0) start = i
    } else if (start >= 0) {
      addStyle(SpanStyle(fontFamily = NotoSerifKhmerFamily), start, i)
      start = -1
    }
  }
  if (start >= 0) addStyle(SpanStyle(fontFamily = NotoSerifKhmerFamily), start, content.length)
}

fun String.withKhmerFont(): AnnotatedString = AnnotatedString(this).withKhmerFont()

object KhmerFontVisualTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText =
    TransformedText(text.withKhmerFont(), OffsetMapping.Identity)
}
