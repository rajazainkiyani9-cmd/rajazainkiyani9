package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraDarkColorScheme =
  darkColorScheme(
    primary = AuraBlue,
    onPrimary = Color.White,
    secondary = AuraCoral,
    onSecondary = Color.White,
    tertiary = AuraViolet,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outlineVariant = DarkOutline,
  )

private val AuraLightColorScheme =
  lightColorScheme(
    primary = AuraBlue,
    onPrimary = Color.White,
    secondary = AuraCoral,
    onSecondary = Color.White,
    tertiary = AuraViolet,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outlineVariant = LightOutline,
  )

@Composable
fun AuraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) AuraDarkColorScheme else AuraLightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = AuraTypography, content = content)
}
