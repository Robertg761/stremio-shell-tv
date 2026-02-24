package com.stremioshell.host.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val StremioTypography = Typography(
  displaySmall = TextStyle(
    fontSize = 18.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold
  ),
  headlineMedium = TextStyle(
    fontSize = 14.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.SemiBold
  ),
  titleLarge = TextStyle(
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium
  ),
  titleMedium = TextStyle(
    fontSize = 12.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Medium
  ),
  bodyLarge = TextStyle(
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Normal
  ),
  bodyMedium = TextStyle(
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Normal
  ),
  bodySmall = TextStyle(
    fontSize = 9.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.Normal
  )
)
