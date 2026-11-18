package com.example.mnlists.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Soft, modern rounding (SPEC §5: ~12–16dp, never pill-shaped).
 * Buttons/rows use `small`/`medium`, cards and sheets `large`.
 */
val MnlistsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
