package com.example.mnlists.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Accessibility guard for SPEC §5/§12: every text colour used on the main
 * surfaces must keep WCAG AA contrast (4.5:1) in both palettes. This also
 * catches a future palette tweak that quietly makes a label unreadable.
 */
class ThemeContrastTest {

    @Test
    fun lightScheme_textPairsMeetAaContrast() = assertAccessible(LightColors)

    @Test
    fun darkScheme_textPairsMeetAaContrast() = assertAccessible(DarkColors)

    private fun assertAccessible(scheme: ColorScheme) {
        val pairs = listOf(
            "onBackground/background" to (scheme.onBackground to scheme.background),
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onSurface/surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
            "onSurfaceVariant/surfaceContainer" to
                (scheme.onSurfaceVariant to scheme.surfaceContainer),
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer/primaryContainer" to
                (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to
                (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
            "onTertiaryContainer/tertiaryContainer" to
                (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "error/surfaceContainer" to (scheme.error to scheme.surfaceContainer),
        )

        pairs.forEach { (name, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue(
                "$name has contrast ${"%.2f".format(ratio)}:1 (needs 4.5:1)",
                ratio >= MIN_CONTRAST,
            )
        }
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2125 * linearize(color.red) +
            0.7154 * linearize(color.green) +
            0.0721 * linearize(color.blue)
    }

    private companion object {
        const val MIN_CONTRAST = 4.5
    }
}
