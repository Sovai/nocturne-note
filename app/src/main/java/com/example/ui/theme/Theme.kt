package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val displayName: String) {
  DARK_NOCTURNE("Nocturne OLED"),
  DARK_SLATE("Midnight Slate"),
  LIGHT_SOLAR("Solar Paper"),
  SYSTEM("System Default")
}

val NocturneDarkColorScheme = darkColorScheme(
  primary = CyanAccent,
  onPrimary = Color(0xFF030712),
  primaryContainer = Color(0xFF0C4A6E),
  onPrimaryContainer = Color(0xFFBAE6FD),
  secondary = IndigoAccent,
  onSecondary = Color(0xFF030712),
  secondaryContainer = Color(0xFF312E81),
  onSecondaryContainer = Color(0xFFE0E7FF),
  tertiary = EmeraldAccent,
  onTertiary = Color(0xFF030712),
  background = NocturneBlack,
  onBackground = NocturneTextPrimary,
  surface = NocturneDarkSurface,
  onSurface = NocturneTextPrimary,
  surfaceVariant = NocturneDarkSurfaceVariant,
  onSurfaceVariant = NocturneTextSecondary,
  outline = NocturneDarkBorder,
  surfaceContainer = NocturneDarkCard,
  surfaceContainerHigh = Color(0xFF1E2B42)
)

val SlateDarkColorScheme = darkColorScheme(
  primary = IndigoAccent,
  onPrimary = Color(0xFF0F172A),
  primaryContainer = Color(0xFF3730A3),
  onPrimaryContainer = Color(0xFFE0E7FF),
  secondary = CyanAccent,
  onSecondary = Color(0xFF0F172A),
  secondaryContainer = Color(0xFF075985),
  onSecondaryContainer = Color(0xFFBAE6FD),
  tertiary = VioletAccent,
  onTertiary = Color(0xFF0F172A),
  background = Color(0xFF0B1120),
  onBackground = Color(0xFFF1F5F9),
  surface = Color(0xFF111827),
  onSurface = Color(0xFFF1F5F9),
  surfaceVariant = Color(0xFF1F2937),
  onSurfaceVariant = Color(0xFF9CA3AF),
  outline = Color(0xFF374151),
  surfaceContainer = Color(0xFF1E293B),
  surfaceContainerHigh = Color(0xFF334155)
)

val SolarLightColorScheme = lightColorScheme(
  primary = CyanAccentMuted,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0F2FE),
  onPrimaryContainer = Color(0xFF0369A1),
  secondary = Color(0xFF4F46E5),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFEEF2FF),
  onSecondaryContainer = Color(0xFF3730A3),
  tertiary = Color(0xFF059669),
  onTertiary = Color.White,
  background = SolarWhite,
  onBackground = SolarTextPrimary,
  surface = SolarSurface,
  onSurface = SolarTextPrimary,
  surfaceVariant = SolarSurfaceVariant,
  onSurfaceVariant = SolarTextSecondary,
  outline = SolarBorder,
  surfaceContainer = Color(0xFFF8FAFC),
  surfaceContainerHigh = Color(0xFFE2E8F0)
)

@Composable
fun NocturneTheme(
  themeMode: AppThemeMode = AppThemeMode.LIGHT_SOLAR,
  content: @Composable () -> Unit
) {
  val isDark = when (themeMode) {
    AppThemeMode.DARK_NOCTURNE, AppThemeMode.DARK_SLATE -> true
    AppThemeMode.LIGHT_SOLAR -> false
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val colorScheme = when (themeMode) {
    AppThemeMode.DARK_NOCTURNE -> NocturneDarkColorScheme
    AppThemeMode.DARK_SLATE -> SlateDarkColorScheme
    AppThemeMode.LIGHT_SOLAR -> SolarLightColorScheme
    AppThemeMode.SYSTEM -> if (isDark) NocturneDarkColorScheme else SolarLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
