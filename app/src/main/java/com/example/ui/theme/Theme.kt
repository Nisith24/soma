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

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default so our custom Sandal theme shines
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
