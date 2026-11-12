package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.ultratv.tv.nativeapp.ui.common.LocalLowRam

/** Mode de l'animation d'ouverture : complète (700 ms), simple fondu (box à faible mémoire) ou aucune. */
enum class StartupMode { FULL, FADE, NONE }

/** Une image de l'animation : tout en 0..1 sauf [scale], [haloPx] (px de conception) et [haloAlpha]. */
data class StartupFrame(
    /** Opacité du fond plein écran qui masque l'application (la révèle à la fin). */
    val bgAlpha: Float,
    val logoAlpha: Float,
    val scale: Float,
    /** 0 = triangle sombre, 1 = rouge vif. */
    val triangle: Float,
    val haloPx: Float,
    val haloAlpha: Float,
)

/**
 * Plan PUR de l'animation d'ouverture (maquette « Démarrage », 700 ms) :
 *  - 0 → 250 ms : le carré du logo apparaît (0 → 100 %, zoom 92 → 100 %) ;
 *  - 250 → 500 ms : le triangle passe au rouge vif, un halo bref s'étend (0 → 18 px) puis disparaît ;
 *  - 500 → 700 ms : le logo s'efface vers l'application, déjà chargée derrière.
 * Sur une box à faible mémoire : un simple fondu de 200 ms. Aucune animation si l'appareil les désactive.
 */
object StartupPlan {
    const val FULL_MS = 700
    const val FADE_MS = 200

    fun mode(animationsOff: Boolean, lowRam: Boolean): StartupMode = when {
        animationsOff -> StartupMode.NONE
        lowRam -> StartupMode.FADE
        else -> StartupMode.FULL
    }

    fun durationMs(mode: StartupMode): Int = when (mode) { StartupMode.FULL -> FULL_MS; StartupMode.FADE -> FADE_MS; StartupMode.NONE -> 0 }

    /** Échelle d'animation du système coupée (Options développeur : « Échelle de durée de l'animateur » = 0). */
    fun systemAnimationsOff(animatorDurationScale: Float): Boolean = animatorDurationScale <= 0f

    fun frame(mode: StartupMode, tMs: Float): StartupFrame {
        val t = tMs.coerceAtLeast(0f)
        return when (mode) {
            StartupMode.NONE -> StartupFrame(0f, 0f, 1f, 1f, 0f, 0f)
            StartupMode.FADE -> {
                val a = (1f - t / FADE_MS).coerceIn(0f, 1f)
                StartupFrame(bgAlpha = a, logoAlpha = a, scale = 1f, triangle = 1f, haloPx = 0f, haloAlpha = 0f)
            }
            StartupMode.FULL -> when {
                t < 250f -> {
                    val p = t / 250f
                    StartupFrame(1f, p, 0.92f + 0.08f * p, 0f, 0f, 0f)
                }
                t < 500f -> {
                    val p = (t - 250f) / 250f
                    StartupFrame(1f, 1f, 1f + 0.09f * p, p, 18f * p, 0.18f * kotlin.math.sin(Math.PI * p).toFloat())
                }
                else -> {
                    val p = ((t - 500f) / 200f).coerceIn(0f, 1f)
                    StartupFrame(1f - p, 1f - p, 1.09f, 1f, 0f, 0f)
                }
            }
        }
    }
}

/** Une seule fois par processus : un retour au premier plan (activité recréée) ne rejoue pas l'animation. */
private object StartupSeen { @Volatile var played = false }

private val STARTUP_BG = Color(0xFF0A0C10)
private val STARTUP_TILE = Color(0xFF1A1E27)
private val TRI_DARK = Color(0xFF7A2026)
private val TRI_BRIGHT = Color(0xFFFF3B47)

/**
 * Animation d'ouverture, posée au-dessus de l'application. Ne retarde JAMAIS le chargement (l'application se compose et charge
 * derrière) ; l'état d'animation n'est lu que dans le dessin (aucune recomposition par image) ; alpha / échelle seulement.
 */
@Composable
fun StartupAnimation() {
    val ctx = LocalContext.current
    val lowRam = LocalLowRam.current
    val appAnimOff = LocalAnimations.current.off
    val mode = remember {
        val scale = runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        if (StartupSeen.played) StartupMode.NONE else StartupPlan.mode(appAnimOff || StartupPlan.systemAnimationsOff(scale), lowRam)
    }
    var done by remember { mutableStateOf(mode == StartupMode.NONE) }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (mode == StartupMode.NONE) return@LaunchedEffect
        StartupSeen.played = true
        t.animateTo(StartupPlan.durationMs(mode).toFloat(), tween(StartupPlan.durationMs(mode), easing = LinearEasing))
        done = true
    }
    if (done) return
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val f = StartupPlan.frame(mode, t.value)
            val px = size.height / 1080f   // maquette 1920 × 1080
            drawRect(STARTUP_BG.copy(alpha = f.bgAlpha))
            if (f.logoAlpha <= 0f) return@Canvas
            val side = 150f * px * f.scale
            val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            val corner = CornerRadius(44f * px * f.scale)
            if (f.haloAlpha > 0f) {
                val h = f.haloPx * px
                drawRoundRect(
                    TRI_BRIGHT.copy(alpha = f.haloAlpha * f.logoAlpha), Offset(topLeft.x - h, topLeft.y - h), Size(side + 2 * h, side + 2 * h),
                    CornerRadius(corner.x + h), style = Stroke(width = h.coerceAtLeast(0.5f)),
                )
            }
            drawRoundRect(STARTUP_TILE.copy(alpha = f.logoAlpha), topLeft, Size(side, side), corner)
            // Triangle « play » : grille 24 de la maquette (M8 5 l11 7 -11 7z), centré dans le carré.
            val k = side * (104f / 150f) / 24f
            val ox = topLeft.x + (side - 24f * k) / 2f
            val oy = topLeft.y + (side - 24f * k) / 2f
            val tri = Path().apply {
                moveTo(ox + 8f * k, oy + 5f * k); lineTo(ox + 19f * k, oy + 12f * k); lineTo(ox + 8f * k, oy + 19f * k); close()
            }
            drawPath(tri, lerp(TRI_DARK, TRI_BRIGHT, f.triangle).copy(alpha = f.logoAlpha))
        }
    }
}
