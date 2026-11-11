package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.ui.design.Ux

/** Calculs purs du guide (programme en cours / suivant, progression, ligne de l'heure) : testés sur la JVM. */
object EpgProgress {
    /** Part écoulée du programme [startMs]..[endMs] à l'instant [nowMs], bornée à 0..1. */
    fun fraction(startMs: Long, endMs: Long, nowMs: Long): Float =
        ((nowMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Minutes restantes, arrondies au-dessus (« 1 min » jusqu'à la dernière seconde), jamais négatives. */
    fun remainingMinutes(endMs: Long, nowMs: Long): Int = if (endMs <= nowMs) 0 else ((endMs - nowMs + 59_999) / 60_000).toInt()

    fun durationMinutes(startMs: Long, endMs: Long): Int = ((endMs - startMs).coerceAtLeast(0) / 60_000).toInt()

    /** Position de l'heure courante dans la fenêtre de la grille (0..1), ou null si elle est hors fenêtre. */
    fun nowLineFraction(nowMs: Long, windowStart: Long, windowMs: Long): Float? {
        if (windowMs <= 0) return null
        val f = (nowMs - windowStart).toFloat() / windowMs
        return if (f in 0f..1f) f else null
    }

    /** Programme en cours et suivant (le plus proche à venir) en un seul passage, sans exiger de liste triée. */
    fun currentAndNext(items: List<EpgEntity>, nowMs: Long): Pair<EpgEntity?, EpgEntity?> {
        var cur: EpgEntity? = null
        var next: EpgEntity? = null
        for (p in items) {
            if (p.startMs <= nowMs && p.endMs > nowMs) { if (cur == null || p.startMs > cur.startMs) cur = p }
            else if (p.startMs > nowMs && (next == null || p.startMs < next.startMs)) next = p
        }
        return cur to next
    }

    /** Les [count] prochains programmes (début après [nowMs]) par ordre chronologique. */
    fun upcoming(items: List<EpgEntity>, nowMs: Long, count: Int): List<EpgEntity> =
        items.filter { it.startMs > nowMs }.sortedBy { it.startMs }.take(count)
}

/** Période de l'horloge partagée : une barre de progression bouge au plus toutes les 30 s (invisible à l'écran). */
const val EPG_TICK_MS = 30_000L

/**
 * UNE horloge pour tout l'écran. L'état est passé tel quel (State) et lu seulement dans le dessin des barres
 * ([EpgProgressBar]) ou dans un petit texte dédié : un tic ne recompose jamais la liste.
 */
@Composable
fun rememberEpgClock(): State<Long> {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(EPG_TICK_MS); clock.longValue = System.currentTimeMillis() }
    }
    return clock
}

/** Barre de progression d'un programme : la lecture de l'horloge a lieu au DESSIN (aucune recomposition). */
@Composable
fun EpgProgressBar(startMs: Long, endMs: Long, clock: State<Long>, modifier: Modifier = Modifier, heightDp: Float = 4f, track: Color = Ux.Surface2) {
    val accent = Ux.Accent
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp(heightDp)).clip(RoundedCornerShape(50)).background(track)
            .drawBehind {
                val w = size.width * EpgProgress.fraction(startMs, endMs, clock.value)
                drawRect(accent, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
            },
    )
}
