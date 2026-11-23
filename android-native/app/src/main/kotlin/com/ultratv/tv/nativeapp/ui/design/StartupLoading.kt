package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design

/**
 * Points de chargement (plan pur) : trois points qui s'allument tour à tour, un cycle de [CYCLE_MS]. Immobiles (même
 * opacité) quand les animations sont réduites ou coupées.
 */
object LoadingDots {
    const val COUNT = 3
    const val CYCLE_MS = 1200
    const val MIN_ALPHA = 0.3f
    private const val STILL_ALPHA = 0.6f

    /** Opacité du point [i] à l'instant [tMs] : sommet (1) à i × cycle / 3, repos ([MIN_ALPHA]) à un tiers de cycle. */
    fun alpha(i: Int, tMs: Long, animate: Boolean): Float {
        if (!animate) return STILL_ALPHA
        val offset = i.toLong() * CYCLE_MS / COUNT
        val p = (((tMs - offset) % CYCLE_MS + CYCLE_MS) % CYCLE_MS).toFloat() / CYCLE_MS
        val dist = minOf(p, 1f - p)
        val k = (1f - dist * COUNT).coerceIn(0f, 1f)
        return MIN_ALPHA + (1f - MIN_ALPHA) * k
    }

    /** Animation seulement au niveau complet et si le système ne coupe pas les animations (échelle de l'animateur à 0). */
    fun animate(level: Animations, systemAnimatorScale: Float): Boolean =
        level.level == AnimationLevel.NORMAL && systemAnimatorScale > 0f
}

const val STARTUP_LOADING_TAG = "startup-loading"

/**
 * Écran de chargement affiché juste après l'écran système, tant que l'appli prépare l'accueil (préférences, profils,
 * catalogue) : le nom « ULTRA TV » dans la typographie de la marque et trois points. Pas de logo (l'écran système vient
 * de le montrer), aucune durée minimale : il disparaît dès que l'accueil est prêt. Le temps n'est lu que dans le dessin
 * des points (aucune recomposition par image).
 */
@Composable
fun StartupLoadingScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val level = LocalAnimations.current
    val animate = remember(level) {
        val scale = runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        LoadingDots.animate(level, scale)
    }
    val label = LocalDs.current.loadingLabel
    Box(
        modifier.fillMaxSize().background(Ux.Bg).testTag(STARTUP_LOADING_TAG).semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.design)) {
            // Nom de la marque, toujours de gauche à droite (même en arabe), comme l'en-tête de l'assistant.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ULTRA ", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx, letterSpacing = 3.sp, color = Ux.Text, maxLines = 1, softWrap = false)
                    Text("TV", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 56.spx, letterSpacing = 3.sp, color = Ux.Text3, maxLines = 1, softWrap = false)
                }
            }
            LoadingDotsRow(animate)
        }
    }
}

@Composable
private fun LoadingDotsRow(animate: Boolean) {
    val t = remember { mutableLongStateOf(0L) }
    if (animate) LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameMillis { it }
        while (true) androidx.compose.runtime.withFrameMillis { t.longValue = it - start }
    }
    val dot = Ux.Accent
    Canvas(Modifier.width(64.design).height(14.design)) {
        val r = size.height / 2f
        val step = (size.width - 2 * r) / (LoadingDots.COUNT - 1)
        for (i in 0 until LoadingDots.COUNT) {
            drawCircle(dot.copy(alpha = LoadingDots.alpha(i, t.longValue, animate)), radius = r, center = Offset(r + i * step, r))
        }
    }
}
