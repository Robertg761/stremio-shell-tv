package com.stremioshell.host.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColors = darkColorScheme(
  primary = Color(0xFF7B5BF5),
  onPrimary = Color.White,
  secondary = Color(0xFF4F9DDE),
  background = Color(0xFF0C0B1E),
  onBackground = Color(0xFFE6E4F0),
  surface = Color(0xFF161430),
  onSurface = Color(0xFFE6E4F0),
  surfaceVariant = Color(0xFF23204A),
  onSurfaceVariant = Color(0xFFB7B3CF),
  border = Color(0xFF7B5BF5),
)

@Composable
fun StremioTvTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = TvColors, content = content)
}
