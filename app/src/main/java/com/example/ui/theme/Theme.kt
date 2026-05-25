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
    primary = SandalPrimaryDark,
    onPrimary = SandalOnPrimaryDark,
    primaryContainer = SandalPrimaryContainerDark,
    onPrimaryContainer = SandalOnPrimaryContainerDark,
    secondary = SandalSecondaryDark,
    onSecondary = SandalSurfaceDark,
    secondaryContainer = SandalSecondaryContainerDark,
    onSecondaryContainer = SandalOnSecondaryContainerDark,
    surface = SandalSurfaceDark,
    onSurface = SandalTextDark,
    surfaceVariant = SandalSurfaceVariantDark,
    onSurfaceVariant = SandalOnSurfaceVariantDark,
    background = SandalBackgroundDark,
    onBackground = SandalTextDark,
    error = SandalErrorDark,
    errorContainer = SandalErrorContainerDark,
    onErrorContainer = SandalTextDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SandalPrimaryLight,
    onPrimary = SandalOnPrimaryLight,
    primaryContainer = SandalPrimaryContainerLight,
    onPrimaryContainer = SandalOnPrimaryContainerLight,
    secondary = SandalSecondaryLight,
    onSecondary = SandalSurfaceLight,
    secondaryContainer = SandalSecondaryContainerLight,
    onSecondaryContainer = SandalOnSecondaryContainerLight,
    surface = SandalSurfaceLight,
    onSurface = SandalTextLight,
    surfaceVariant = SandalSurfaceVariantLight,
    onSurfaceVariant = SandalOnSurfaceVariantLight,
    background = SandalBackgroundLight,
    onBackground = SandalTextLight,
    error = SandalErrorLight,
    errorContainer = SandalErrorContainerLight,
    onErrorContainer = SandalSurfaceLight
  )

private val NordColorScheme = darkColorScheme(
    primary = NordPrimary,
    onPrimary = NordOnPrimary,
    primaryContainer = NordPrimaryContainer,
    onPrimaryContainer = NordOnPrimaryContainer,
    secondary = NordSecondary,
    onSecondary = NordSurface,
    secondaryContainer = NordSecondaryContainer,
    onSecondaryContainer = NordOnSecondaryContainer,
    surface = NordSurface,
    onSurface = NordText,
    surfaceVariant = NordSurfaceVariant,
    onSurfaceVariant = NordOnSurfaceVariant,
    background = NordBackground,
    onBackground = NordText,
    error = NordError,
    errorContainer = NordErrorContainer,
    onErrorContainer = NordText
)

private val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    secondary = OceanSecondary,
    onSecondary = OceanSurface,
    secondaryContainer = OceanSecondaryContainer,
    onSecondaryContainer = OceanOnSecondaryContainer,
    surface = OceanSurface,
    onSurface = OceanText,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = OceanOnSurfaceVariant,
    background = OceanBackground,
    onBackground = OceanText,
    error = OceanError,
    errorContainer = OceanErrorContainer,
    onErrorContainer = OceanText
)

private val SunsetColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = SunsetOnPrimary,
    primaryContainer = SunsetPrimaryContainer,
    onPrimaryContainer = SunsetOnPrimaryContainer,
    secondary = SunsetSecondary,
    onSecondary = SunsetSurface,
    secondaryContainer = SunsetSecondaryContainer,
    onSecondaryContainer = SunsetOnSecondaryContainer,
    surface = SunsetSurface,
    onSurface = SunsetText,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetOnSurfaceVariant,
    background = SunsetBackground,
    onBackground = SunsetText,
    error = SunsetError,
    errorContainer = SunsetErrorContainer,
    onErrorContainer = SunsetText
)

@Composable
fun MyApplicationTheme(
  appTheme: com.example.AppThemeMode = com.example.AppThemeMode.SYSTEM,
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default so our custom Sandal theme shines
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      appTheme == com.example.AppThemeMode.NORD -> NordColorScheme
      appTheme == com.example.AppThemeMode.OCEAN -> OceanColorScheme
      appTheme == com.example.AppThemeMode.SUNSET -> SunsetColorScheme
      appTheme == com.example.AppThemeMode.LIGHT -> LightColorScheme
      appTheme == com.example.AppThemeMode.DARK -> DarkColorScheme
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
