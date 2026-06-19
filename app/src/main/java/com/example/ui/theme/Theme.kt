package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = HighDensityPrimary,
    secondary = HighDensitySecondary,
    tertiary = HighDensityTertiary,
    background = HighDensityBackgroundDark,
    surface = HighDensitySurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = HighDensityOnBackgroundDark,
    onSurface = HighDensityOnSurfaceDark,
    outline = HighDensityBorderDark,
    error = HighDensityAccentRoseDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = HighDensityPrimary,
    secondary = HighDensitySecondary,
    tertiary = HighDensityTertiary,
    background = HighDensityBackgroundLight,
    surface = HighDensitySurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = HighDensityOnBackgroundLight,
    onSurface = HighDensityOnSurfaceLight,
    outline = HighDensityBorderLight,
    error = HighDensityAccentRose
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  themeColor: String = "emerald",
  content: @Composable () -> Unit,
) {
  val primaryColor = when (themeColor) {
    "royal_blue" -> Color(0xFF2563EB)
    "quiet_purple" -> Color(0xFF7C3AED)
    "authentic_gold" -> Color(0xFFD97706)
    else -> HighDensityPrimary // Emerald 0xFF059669
  }
  
  val secondaryColor = when (themeColor) {
    "royal_blue" -> Color(0xFF4F46E5)
    "quiet_purple" -> Color(0xFF9333EA)
    "authentic_gold" -> Color(0xFFB45309)
    else -> HighDensitySecondary // Teal 0xFF0F766E
  }
  
  val tertiaryColor = when (themeColor) {
    "royal_blue" -> Color(0xFF06B6D4)
    "quiet_purple" -> Color(0xFFEC4899)
    "authentic_gold" -> Color(0xFFFBBF24)
    else -> HighDensityTertiary // Amber 0xFFF59E0B
  }

  val activeDarkScheme = darkColorScheme(
    primary = primaryColor,
    secondary = secondaryColor,
    tertiary = tertiaryColor,
    background = HighDensityBackgroundDark,
    surface = HighDensitySurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = HighDensityOnBackgroundDark,
    onSurface = HighDensityOnSurfaceDark,
    outline = HighDensityBorderDark,
    error = HighDensityAccentRoseDark
  )
  
  val activeLightScheme = lightColorScheme(
    primary = primaryColor,
    secondary = secondaryColor,
    tertiary = tertiaryColor,
    background = HighDensityBackgroundLight,
    surface = HighDensitySurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = HighDensityOnBackgroundLight,
    onSurface = HighDensityOnSurfaceLight,
    outline = HighDensityBorderLight,
    error = HighDensityAccentRose
  )

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> activeDarkScheme
      else -> activeLightScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
