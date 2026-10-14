package com.example

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.NotoSerifKhmerFamily
import com.example.ui.theme.withKhmerFont
import com.example.util.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KhmerMarkdownRenderingTest {
  @Test
  fun khmerBoldAndItalicPreserveSpansAndChooseKhmerFont() {
    val rendered = MarkdownParser.parseInlineMarkdown(
      "សួស្តី **ខ្មែរ** និង *ភាសា*"
    ).withKhmerFont()

    assertEquals("សួស្តី ខ្មែរ និង ភាសា", rendered.text)
    val boldStart = rendered.text.indexOf("ខ្មែរ")
    val italicStart = rendered.text.indexOf("ភាសា")
    assertTrue(rendered.spanStyles.any {
      it.start <= boldStart && it.end >= boldStart + "ខ្មែរ".length &&
        it.item.fontWeight == FontWeight.Bold
    })
    assertTrue(rendered.spanStyles.any {
      it.start <= italicStart && it.end >= italicStart + "ភាសា".length &&
        it.item.fontStyle == FontStyle.Italic
    })
    assertTrue(rendered.spanStyles.any {
      it.start <= boldStart && it.end >= boldStart + "ខ្មែរ".length &&
        it.item.fontFamily == NotoSerifKhmerFamily &&
        it.item.fontSynthesis == FontSynthesis.All
    })
  }

  @Test
  fun combinedKhmerBoldItalicHasBothStylesAndNoStrayDelimiters() {
    val rendered = MarkdownParser.parseInlineMarkdown("ចំណងជើង ***អត្ថបទ***").withKhmerFont()
    assertEquals("ចំណងជើង អត្ថបទ", rendered.text)
    val start = rendered.text.indexOf("អត្ថបទ")
    assertTrue(rendered.spanStyles.any {
      it.start <= start && it.end >= start + "អត្ថបទ".length &&
        it.item.fontWeight == FontWeight.Bold &&
        it.item.fontStyle == FontStyle.Italic
    })
  }

  @Test
  fun mixedEnglishAndKhmerKeepsEnglishFont() {
    val rendered = MarkdownParser.parseInlineMarkdown("Hello **ខ្មែរ** world").withKhmerFont()
    assertEquals("Hello ខ្មែរ world", rendered.text)
    assertTrue(rendered.spanStyles.none {
      it.item.fontFamily == NotoSerifKhmerFamily && it.start < 6
    })
  }
}
