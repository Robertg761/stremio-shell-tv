package com.stremioshell.host.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StremioColorScheme = darkColorScheme(
  primary = StremioPrimaryAccent,
  onPrimary = StremioPrimaryForeground,
  secondary = StremioSecondaryAccent,
  onSecondary = StremioPrimaryForeground,
  background = StremioPrimaryBackground,
  onBackground = StremioPrimaryForeground,
  surface = StremioSurface,
  onSurface = StremioPrimaryForeground,
  surfaceVariant = StremioOverlay,
  onSurfaceVariant = StremioPrimaryForeground,
  error = StremioDangerAccent,
  onError = StremioPrimaryForeground
)

@Composable
fun StremioComposeTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = StremioColorScheme,
    typography = StremioTypography,
    content = content
  )
}
