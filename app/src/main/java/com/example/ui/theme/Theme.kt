package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val displayName: String) {
  LIGHT("Light"),
  DARK("Dark")
}

private val LightScheme = lightColorScheme(
  primary = InkTeal,
  onPrimary = Color.White,
  primaryContainer = InkTealContainer,
  onPrimaryContainer = Color(0xFF104958),
  secondary = MoonGold,
  onSecondary = Color.White,
  secondaryContainer = MoonGoldContainer,
  onSecondaryContainer = Color(0xFF5A441B),
  tertiary = InkIndigo,
  onTertiary = Color.White,
  background = PaperBackground,
  onBackground = PaperText,
  surface = PaperSurface,
  onSurface = PaperText,
  surfaceVariant = PaperContainer,
  onSurfaceVariant = PaperTextMuted,
  outline = PaperBorder,
  surfaceContainer = PaperContainer,
  surfaceContainerHigh = PaperElevated
)

private val DarkScheme = darkColorScheme(
  primary = MoonTeal,
  onPrimary = Color(0xFF072B36),
  primaryContainer = MoonTealContainer,
  onPrimaryContainer = Color(0xFFD5F4F5),
  secondary = MoonLightGold,
  onSecondary = Color(0xFF30250E),
  secondaryContainer = MoonGoldDarkContainer,
  onSecondaryContainer = Color(0xFFFFE9B5),
  tertiary = MoonBlue,
  onTertiary = Color(0xFF12314D),
  background = NightBackground,
  onBackground = NightText,
  surface = NightSurface,
  onSurface = NightText,
  surfaceVariant = NightContainer,
  onSurfaceVariant = NightTextMuted,
  outline = NightBorder,
  surfaceContainer = NightContainer,
  surfaceContainerHigh = NightElevated
)

@Composable
fun NocturneTheme(
  themeMode: AppThemeMode = AppThemeMode.LIGHT,
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (themeMode == AppThemeMode.DARK) DarkScheme else LightScheme,
    typography = Typography,
    content = content
  )
}
