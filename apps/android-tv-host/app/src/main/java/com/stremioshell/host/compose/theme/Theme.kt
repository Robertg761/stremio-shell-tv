package com.stremioshell.host.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StremioColorScheme = darkColorScheme(
  primary = StremioGreen,
  onPrimary = StremioOnSurface,
  secondary = StremioFocus,
  background = StremioGreenDark,
  surface = StremioSurface,
  onSurface = StremioOnSurface
)

@Composable
fun StremioComposeTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = StremioColorScheme,
    typography = StremioTypography,
    content = content
  )
}
