package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.example.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import androidx.compose.ui.unit.sp

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp
  ),
  headlineLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 32.sp
  ),
  headlineMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp
  ),
  headlineSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.2.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp
  ),
  bodySmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp
  ),
  labelMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp
  ),
  labelSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp
  )
)

// The bundled Noto Serif Khmer is a variable font (wght axis). Register actual
// variation settings for both weights rather than mapping the default 400 face
// to "Bold", which makes Markdown bold appear identical to normal text.
// On Android 7.x, use the regular face only so Compose can synthesize bold.
@OptIn(ExperimentalTextApi::class)
val NotoSerifKhmerFamily = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
  FontFamily(
    Font(
      resId = R.font.noto_serif_khmer,
      weight = FontWeight.Normal,
      style = FontStyle.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
      resId = R.font.noto_serif_khmer,
      weight = FontWeight.Bold,
      style = FontStyle.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
  )
} else {
  FontFamily(Font(R.font.noto_serif_khmer, FontWeight.Normal))
}

private val HappyMonkeyFamily = FontFamily(
  Font(R.font.happy_monkey, FontWeight.Normal)
)

enum class EditorFontChoice(val label: String, val fontFamily: FontFamily) {
  SANS("Sans", FontFamily.SansSerif),
  SERIF("Serif", FontFamily.Serif),
  MONO("Mono", FontFamily.Monospace),
  SYSTEM("System", FontFamily.Default),
  HANDWRITING("Script", FontFamily.Cursive),
  HAPPY_MONKEY("Happy Monkey", HappyMonkeyFamily)
}
