package com.example.mnlists.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * MNlists palettes (SPEC §5).
 *
 * Light: white background, light green accent/buttons.
 * Dark:  midnight dusk background, dusk-blue accent/buttons.
 *
 * Accent colours are deliberately soft; on-colour tokens are dark ink so text
 * and icons keep AA contrast on top of the light accents.
 */

// ---- Light ----------------------------------------------------------------

val LightPrimary = Color(0xFF7CB342)
val LightOnPrimary = Color(0xFF14260A)
val LightPrimaryContainer = Color(0xFFDFF0C8)
val LightOnPrimaryContainer = Color(0xFF21360F)

val LightSecondary = Color(0xFF5C6B4C)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE1EAD5)
val LightOnSecondaryContainer = Color(0xFF1A2413)

val LightTertiary = Color(0xFF4E6B5A)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD9E9DE)
val LightOnTertiaryContainer = Color(0xFF10291B)

val LightBackground = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF191C16)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF191C16)
val LightSurfaceVariant = Color(0xFFF2F5EC)
val LightOnSurfaceVariant = Color(0xFF43483C)
val LightOutline = Color(0xFFC2C9B6)
val LightOutlineVariant = Color(0xFFE1E6D8)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFAFBF7)
val LightSurfaceContainer = Color(0xFFF5F7F0)
val LightSurfaceContainerHigh = Color(0xFFEFF2E9)
val LightSurfaceContainerHighest = Color(0xFFEAEDE3)

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)

// ---- Dark (midnight dusk, refined in Step 14) -----------------------------
//
// Deeper ink background + gently lifted blue-grey surfaces give the dark theme
// a calmer, more premium depth. All text/on-colour pairs stay above 4.5:1.

val DarkPrimary = Color(0xFFA8B7FF)
val DarkOnPrimary = Color(0xFF0B1433)
val DarkPrimaryContainer = Color(0xFF2E3B6B)
val DarkOnPrimaryContainer = Color(0xFFE4E8FF)

val DarkSecondary = Color(0xFFBCC6DB)
val DarkOnSecondary = Color(0xFF1B2331)
val DarkSecondaryContainer = Color(0xFF28324A)
val DarkOnSecondaryContainer = Color(0xFFE2E8F6)

val DarkTertiary = Color(0xFFAEC9E8)
val DarkOnTertiary = Color(0xFF0B1D30)
val DarkTertiaryContainer = Color(0xFF22384F)
val DarkOnTertiaryContainer = Color(0xFFD6E9FA)

val DarkBackground = Color(0xFF0A0D16)
val DarkOnBackground = Color(0xFFECEFF9)
val DarkSurface = Color(0xFF101424)
val DarkOnSurface = Color(0xFFECEFF9)
val DarkSurfaceVariant = Color(0xFF1C2235)
val DarkOnSurfaceVariant = Color(0xFFC6CCE0)
val DarkOutline = Color(0xFF3A425C)
val DarkOutlineVariant = Color(0xFF252C42)

val DarkSurfaceContainerLowest = Color(0xFF070910)
val DarkSurfaceContainerLow = Color(0xFF0D111E)
val DarkSurfaceContainer = Color(0xFF131827)
val DarkSurfaceContainerHigh = Color(0xFF1A2032)
val DarkSurfaceContainerHighest = Color(0xFF21283D)

val DarkError = Color(0xFFF4BAB6)
val DarkOnError = Color(0xFF5E1410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

// ---- Shared ---------------------------------------------------------------

val LightInverseSurface = Color(0xFF2E312B)
val LightInverseOnSurface = Color(0xFFF1F2EA)
val LightInversePrimary = Color(0xFF9BD06A)

val DarkInverseSurface = Color(0xFFECEFF9)
val DarkInverseOnSurface = Color(0xFF101424)
val DarkInversePrimary = Color(0xFF4A5AA0)
