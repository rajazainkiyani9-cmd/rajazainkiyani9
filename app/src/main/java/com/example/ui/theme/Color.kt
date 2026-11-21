package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val AuraCoral = Color(0xFFFF3B6B)
val AuraMagenta = Color(0xFFC13584)
val AuraViolet = Color(0xFF833AB4)
val AuraAmber = Color(0xFFF77737)
val AuraGold = Color(0xFFFCAF45)
val AuraBlue = Color(0xFF0095F6)
val AuraVerifiedBlue = Color(0xFF3897F0)
val AuraLikeRed = Color(0xFFFF3040)

val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF4F4F6)
val LightOnSurface = Color(0xFF121212)
val LightOnSurfaceVariant = Color(0xFF737373)
val LightOutline = Color(0xFFDBDBDB)

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E22)
val DarkOnSurface = Color(0xFFFAFAFA)
val DarkOnSurfaceVariant = Color(0xFFA8A8A8)
val DarkOutline = Color(0xFF2C2C30)

val AuraStoryGradient =
  Brush.linearGradient(
    colors = listOf(AuraGold, AuraAmber, AuraCoral, AuraMagenta, AuraViolet)
  )

val AuraButtonGradient =
  Brush.horizontalGradient(
    colors = listOf(AuraCoral, AuraMagenta, AuraViolet)
  )
