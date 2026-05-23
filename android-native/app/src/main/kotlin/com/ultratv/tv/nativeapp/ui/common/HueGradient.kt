package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.ui.graphics.Color

/**
 * Shared hue → Color helpers used by ChannelLogo, ContinueWatchingTile and
 * Live's preview pane. Previously each call site shipped a near-identical
 * copy of these maths with subtle variations — single source of truth now.
 *
 * Approximates the prototype's `oklch(L% C h)` colours with HSL, which is
 * good enough for the decorative gradients we render.
 */
object HueGradient {

    /** Hue 0..360, lightness 0..1, saturation 0..1 → ARGB Color. */
    fun hsl(hue: Int, sat: Float = 0.55f, light: Float = 0.40f): Color {
        val h = ((hue % 360) + 360) % 360
        val c = (1f - kotlin.math.abs(2 * light - 1f)) * sat
        val hp = h / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = light - c / 2f
        fun b(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return Color(0xFF000000.toInt() or (b(r1) shl 16) or (b(g1) shl 8) or b(b1))
    }

    /** Light / dark gradient stops keyed by a seed (channel name hash, etc). */
    fun pair(seed: Int): Pair<Color, Color> {
        val hue = ((seed % 360) + 360) % 360
        return hsl(hue, 0.55f, 0.45f) to hsl(hue, 0.55f, 0.18f)
    }
}
