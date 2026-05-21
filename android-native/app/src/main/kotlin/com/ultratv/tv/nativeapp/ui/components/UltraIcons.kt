package com.ultratv.tv.nativeapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor

// ─── Ultra TV nav icons (24×24 stroke style, matches the design's lucide-ish set).
// Drawn directly on Canvas so we don't pull in a vector graphics library.

enum class UltraIcon {
    Home, Live, Film, Series, Guide, Search, Multi, Gear, Play, Plus, Info, Down,
    Up, Right, Left, Back, Heart, Check, Sound, Captions, Settings, Remote, Pip,
    Stats, Lock, Tv, Calendar, Star, Record, Folder
}

@Composable
fun UltraIcon(
    icon: UltraIcon,
    size: Dp = 22.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Float = 1.6f,
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = size.toPx() / 24f
        val stroke = Stroke(width = strokeWidth * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), stroke.width, cap = StrokeCap.Round)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f) {
            val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x * s, y * s, (x + w) * s, (y + h) * s, r * s, r * s)) }
            drawPath(path, color, style = stroke)
        }
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) {
            if (fill) drawCircle(color, r * s, Offset(cx * s, cy * s))
            else drawCircle(color, r * s, Offset(cx * s, cy * s), style = stroke)
        }
        fun path(build: Path.() -> Unit, fill: Boolean = false) {
            val p = Path().apply(build)
            if (fill) drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Fill)
            else drawPath(p, color, style = stroke)
        }
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        when (icon) {
            UltraIcon.Home -> path({
                moveTo(3 * s, 11 * s); lineTo(12 * s, 4 * s); lineTo(21 * s, 11 * s)
                lineTo(21 * s, 20 * s); lineTo(15 * s, 20 * s); lineTo(15 * s, 14 * s)
                lineTo(9 * s, 14 * s); lineTo(9 * s, 20 * s); lineTo(3 * s, 20 * s); close()
            })
            UltraIcon.Live, UltraIcon.Tv -> { rect(2f, 6f, 20f, 13f, 2f); line(8f, 21f, 16f, 21f); line(6f, 3f, 12f, 6f); line(12f, 6f, 18f, 3f) }
            UltraIcon.Film -> { rect(3f, 3f, 18f, 18f, 2f); line(3f, 8f, 21f, 8f); line(3f, 16f, 21f, 16f); line(8f, 3f, 8f, 21f); line(16f, 3f, 16f, 21f) }
            UltraIcon.Series -> { rect(3f, 5f, 18f, 14f, 2f); line(8f, 21f, 16f, 21f); line(12f, 2f, 12f, 5f) }
            UltraIcon.Guide, UltraIcon.Calendar -> { rect(3f, 4f, 18f, 16f, 2f); line(3f, 9f, 21f, 9f); line(9f, 4f, 9f, 20f); line(15f, 4f, 15f, 20f) }
            UltraIcon.Search -> { circle(11f, 11f, 7f); line(16.5f, 16.5f, 20f, 20f) }
            UltraIcon.Multi -> { rect(3f, 3f, 8f, 8f, 1.4f); rect(13f, 3f, 8f, 8f, 1.4f); rect(3f, 13f, 8f, 8f, 1.4f); rect(13f, 13f, 8f, 8f, 1.4f) }
            UltraIcon.Gear, UltraIcon.Settings -> { circle(12f, 12f, 3f); circle(12f, 12f, 8f) }
            UltraIcon.Play -> path({ moveTo(7 * s, 4 * s); lineTo(7 * s, 20 * s); lineTo(20 * s, 12 * s); close() }, fill = true)
            UltraIcon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            UltraIcon.Info -> { circle(12f, 12f, 9f); line(12f, 11f, 12f, 17f); circle(12f, 8f, 0.6f, fill = true) }
            UltraIcon.Down -> { line(6f, 9f, 12f, 15f); line(12f, 15f, 18f, 9f) }
            UltraIcon.Up -> { line(6f, 15f, 12f, 9f); line(12f, 9f, 18f, 15f) }
            UltraIcon.Right -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
            UltraIcon.Left -> { line(15f, 6f, 9f, 12f); line(9f, 12f, 15f, 18f) }
            UltraIcon.Back -> { line(9f, 14f, 4f, 9f); line(4f, 9f, 9f, 4f); line(4f, 9f, 15f, 9f); path({ moveTo(15f * s, 9f * s); cubicTo(20f * s, 9f * s, 20f * s, 19f * s, 15f * s, 19f * s); lineTo(11f * s, 19f * s) }) }
            UltraIcon.Heart, UltraIcon.Star -> path({
                moveTo(12 * s, 21 * s)
                cubicTo(2 * s, 14 * s, 4 * s, 5 * s, 12 * s, 8 * s)
                cubicTo(20 * s, 5 * s, 22 * s, 14 * s, 12 * s, 21 * s); close()
            })
            UltraIcon.Check -> { line(5f, 12f, 9f, 16f); line(9f, 16f, 19f, 6f) }
            UltraIcon.Sound -> path({
                moveTo(11 * s, 5 * s); lineTo(6 * s, 9 * s); lineTo(2 * s, 9 * s)
                lineTo(2 * s, 15 * s); lineTo(6 * s, 15 * s); lineTo(11 * s, 19 * s); close()
            })
            UltraIcon.Captions -> { rect(2f, 5f, 20f, 14f, 2f); line(6f, 11f, 10f, 11f); line(6f, 15f, 12f, 15f); line(14f, 11f, 18f, 11f); line(14f, 15f, 16f, 15f) }
            UltraIcon.Remote -> { rect(7f, 2f, 10f, 20f, 3f); circle(12f, 6f, 0.6f, fill = true); line(9f, 11f, 15f, 11f); line(10f, 17f, 14f, 17f) }
            UltraIcon.Pip -> { rect(3f, 5f, 18f, 14f, 2f); rect(12f, 11f, 6f, 5f, 1f) }
            UltraIcon.Stats -> { line(4f, 20f, 4f, 10f); line(10f, 20f, 10f, 4f); line(16f, 20f, 16f, 13f); line(2f, 20f, 22f, 20f) }
            UltraIcon.Lock -> { rect(4f, 11f, 16f, 10f, 2f); path({ moveTo(8 * s, 11 * s); cubicTo(8 * s, 5 * s, 16 * s, 5 * s, 16 * s, 11 * s) }) }
            UltraIcon.Record -> { circle(12f, 12f, 5f, fill = true); circle(12f, 12f, 9f) }
            UltraIcon.Folder -> path({
                moveTo(3 * s, 7 * s); lineTo(10 * s, 7 * s); lineTo(12 * s, 5 * s); lineTo(20 * s, 5 * s)
                lineTo(20 * s, 19 * s); lineTo(3 * s, 19 * s); close()
            })
        }
    }
}

