package com.ultratv.tv.nativeapp.ui.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.LiveBadge
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Vrai quand l'activité est en image dans l'image : le lecteur n'affiche alors que la vidéo. */
object PipState {
    var active by mutableStateOf(false)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Luminosité de la FENÊTRE (jamais celle du système) et volume média, en niveaux 0..1. */
class PlayerLevels(private val context: Context) {
    private val activity = context.findActivity()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun brightness(): Float {
        val w = activity?.window?.attributes?.screenBrightness ?: -1f
        if (w >= 0f) return w
        return runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    fun setBrightness(level: Float) {
        val win = activity?.window ?: return
        win.attributes = win.attributes.apply { screenBrightness = level.coerceIn(0.02f, 1f) }
    }

    /** Rend la luminosité au système en quittant le lecteur. */
    fun resetBrightness() {
        val win = activity?.window ?: return
        win.attributes = win.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(level: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level.coerceIn(0f, 1f) * max).roundToInt(), 0)
    }
}

/** Retour visuel d'un geste : luminosité / volume (jauge), saut de 10 s, format d'image. */
sealed interface GestureHud {
    data class Level(val role: DragRole, val level: Float) : GestureHud
    data class Seek(val forward: Boolean) : GestureHud
    data class Aspect(val mode: AspectMode) : GestureHud
    data class Zap(val next: Boolean) : GestureHud
}

/**
 * Couche de gestes du lecteur : un appui bascule les contrôles, un double appui saute de 10 s (VOD) ou pause (centre),
 * un glissement vertical règle la luminosité (gauche) ou le volume (droite) ou zappe (centre, direct), deux doigts changent le format.
 * La logique de décision est dans MobileLogic (testée) ; ce composable ne fait que traduire les événements tactiles.
 */
@Composable
fun GestureLayer(
    isLive: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (TapZone) -> Unit,
    onDragStart: (DragRole) -> Unit,
    onDrag: (DragRole, totalDy: Float, heightPx: Float) -> Unit,
    onDragEnd: (DragRole, totalDy: Float) -> Unit,
    onPinch: (scale: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tap by rememberUpdatedState(onTap)
    val dbl by rememberUpdatedState(onDoubleTap)
    val ds by rememberUpdatedState(onDragStart)
    val d by rememberUpdatedState(onDrag)
    val de by rememberUpdatedState(onDragEnd)
    val pinch by rememberUpdatedState(onPinch)
    val live by rememberUpdatedState(isLive)
    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { tap() }, onDoubleTap = { off -> dbl(tapZone(off.x, size.width.toFloat())) })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val role = dragRole(down.position.x, w, live)
                    var dragging = false; var pinching = false
                    var total = 0f; var startDist = 0f; var scale = 1f
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val dist = (pressed[0].position - pressed[1].position).getDistance()
                            if (startDist == 0f) startDist = dist else scale = dist / startDist
                            pinching = true
                            ev.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && !pinching) {
                            val c = pressed[0]
                            if (!dragging && role != DragRole.NONE && abs(c.position.y - down.position.y) > viewConfiguration.touchSlop &&
                                abs(c.position.y - down.position.y) > abs(c.position.x - down.position.x)
                            ) { dragging = true; ds(role) }
                            if (dragging) { total = c.position.y - down.position.y; d(role, total, h); c.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (pinching) pinch(scale)
                    if (dragging) de(role, total)
                }
            },
    )
}

/** Jauge verticale (luminosité / volume) en bord d'écran, ou pastille de saut / format au centre. */
@Composable
fun BoxScope.GestureHudView(hud: GestureHud?) {
    val M = LocalMobileStrings.current
    when (hud) {
        is GestureHud.Level -> {
            val left = hud.role == DragRole.BRIGHTNESS
            Column(
                Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 24.dp).width(44.dp).height(150.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0x990A0A0C)).padding(vertical = 10.dp)
                    .semantics { contentDescription = (if (left) M.brightness else M.volume) + " " + (hud.level * 100).roundToInt() + "%" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            ) {
                Box(Modifier.weight(1f).width(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(hud.level.coerceIn(0f, 1f)).background(Color.White))
                }
                DIcon(if (left) MobileIcons.Brightness else if (hud.level <= 0.01f) MobileIcons.Mute else MobileIcons.Volume, 16.dp, Color(0xFFF5F5F7))
            }
        }
        is GestureHud.Seek -> Box(
            Modifier.align(if (hud.forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 64.dp).size(64.dp).clip(CircleShape).background(Color(0x990A0A0C)),
            contentAlignment = Alignment.Center,
        ) { Text(if (hud.forward) "+10" else "−10", color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
        is GestureHud.Aspect -> HudPill(M.aspectChanged + " · " + hud.mode.label())
        is GestureHud.Zap -> HudPill(if (hud.next) M.nextChannel else M.previousChannel)
        null -> Unit
    }
}

private fun AspectMode.label() = when (this) { AspectMode.FIT -> "Fit"; AspectMode.FILL -> "Fill"; AspectMode.ZOOM -> "Zoom"; AspectMode.R16_9 -> "16:9"; AspectMode.R4_3 -> "4:3" }

@Composable
private fun BoxScope.HudPill(text: String) {
    Text(
        text, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xB30A0A0C)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

data class ControlPill(val label: String, val icon: String, val onClick: () -> Unit, val active: Boolean = false)

/**
 * Contrôles tactiles (maquette MobileLecteur) : en-tête (retour, badge, titre, réglages), centre (−10, pause, +10),
 * pied (heures, barre de progression, pilules). Toujours sombres : le lecteur ignore le thème clair.
 * [compact] : bandeau vidéo du mode portrait (pas de pilules ; elles sont sous la vidéo).
 */
@Composable
fun BoxScope.MobileControls(
    isLive: Boolean, live: Boolean = isLive, badge: String?, title: String, subtitle: String?, playing: Boolean,
    fraction: Float, startLabel: String, endLabel: String, seekable: Boolean, compact: Boolean,
    onBack: () -> Unit, onTogglePlay: () -> Unit, onSeekBy: (Long) -> Unit, onSeekTo: (Float) -> Unit, onSettings: () -> Unit,
    pills: List<ControlPill>, onFullscreen: (() -> Unit)? = null, jumpSec: Int = 10,
) {
    val M = LocalMobileStrings.current
    val bar = Color(0xB80A0A0C)
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    // En-tête
    Row(
        Modifier.align(Alignment.TopStart).fillMaxWidth().background(bar).windowInsetsPadding(insets).padding(horizontal = if (compact) 8.dp else 24.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerRoundButton(MobileIcons.Back, M.a11yBack, onBack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (badge != null) Text(badge, color = com.ultratv.tv.nativeapp.ui.design.Ux.Accent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = Color(0xFFF5F5F7), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onFullscreen != null) PlayerRoundButton(MobileIcons.Fullscreen, M.rotateHint, onFullscreen)
        PlayerRoundButton(MobileIcons.Tune, M.a11ySettings, onSettings)
    }
    // Centre
    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!isLive) SeekCircle("−$jumpSec", M.a11ySeekBack) { onSeekBy(-jumpSec * 1000L) }
        Box(
            Modifier.size(if (compact) 56.dp else 72.dp).clip(CircleShape).background(Color.White).clickable(onClickLabel = if (playing) M.a11yPause else M.a11yPlay, onClick = onTogglePlay)
                .semantics { contentDescription = if (playing) M.a11yPause else M.a11yPlay },
            contentAlignment = Alignment.Center,
        ) { DIcon(if (playing) MobileIcons.Pause else "M7 4v16l13-8z", if (compact) 22.dp else 28.dp, Color(0xFF0A0A0C), fill = true) }
        if (!isLive) SeekCircle("+$jumpSec", M.a11ySeekForward) { onSeekBy(jumpSec * 1000L) }
    }
    // Pied
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().background(bar).windowInsetsPadding(insets).padding(horizontal = if (compact) 12.dp else 24.dp).padding(top = 8.dp, bottom = if (compact) 6.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(startLabel, color = Color(0xFFC4C4CC), fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
            SeekBar(fraction, seekable, onSeekTo, Modifier.weight(1f))
            Text(endLabel, color = Color(0xFFC4C4CC), fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
        }
        if (!compact && pills.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pills.forEach { PlayerPill(it) }
        }
    }
}

@Composable
fun PlayerRoundButton(icon: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.Center) { DIcon(icon, 18.dp, Color(0xFFF5F5F7), strokeWidth = 2.2f) }
    }
}

@Composable
private fun SeekCircle(label: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x990A0A0C)).clickable(onClickLabel = description, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

/** Pilule du pied (32 dp visibles, 48 dp tactiles) : Chaînes, Pistes, Depuis le début, Veille, Image dans l'image… */
@Composable
fun PlayerPill(p: ControlPill) {
    Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = p.label, onClick = p.onClick).semantics { contentDescription = p.label }, contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (p.active) com.ultratv.tv.nativeapp.ui.design.Ux.Accent else Color(0x1FFFFFFF)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DIcon(p.icon, 14.dp, Color(0xFFF5F5F7))
            Text(p.label, color = Color(0xFFF5F5F7), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Barre de progression de 4 dp, poignée de 16 dp ; un appui ou un glissement (VOD) déplace la lecture. Zone tactile de 48 dp. */
@Composable
private fun SeekBar(fraction: Float, seekable: Boolean, onSeekTo: (Float) -> Unit, modifier: Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier.height(48.dp).then(
            if (!seekable) Modifier else Modifier.pointerInput(Unit) {
                detectTapGestures { off -> onSeekTo((off.x / size.width).coerceIn(0f, 1f)) }
            }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = down.position
                    do {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (abs(c.position.x - down.position.x) > viewConfiguration.touchSlop) { last = c.position; c.consume() }
                    } while (ev.changes.any { it.pressed })
                    if (last != down.position) onSeekTo((last.x / size.width).coerceIn(0f, 1f))
                }
            },
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3F3F46)))
        Box(Modifier.fillMaxWidth(f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(com.ultratv.tv.nativeapp.ui.design.Ux.Accent))
        if (seekable) Box(Modifier.offset(x = maxWidth * f - 8.dp).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

/** Badge du direct : « EN DIRECT · 101 SPORT 1 ». */
fun liveBadgeText(live: String, number: String?, channel: String?): String =
    listOfNotNull(live.uppercase(), listOfNotNull(number, channel?.uppercase()).joinToString(" ").takeIf { it.isNotBlank() }).joinToString(" · ")


/** Verrouille l'orientation (bouton « plein écran » du mode portrait) ; null rend la main au capteur. */
fun Context.lockOrientation(landscape: Boolean?) {
    val a = findActivity() ?: return
    a.requestedOrientation = when (landscape) {
        true -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        false -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        null -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

/** Plein écran immersif en paysage ; barres système visibles en portrait. Rend les barres en quittant. */
@Composable
fun ImmersiveWhen(fullscreen: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(fullscreen) {
        val w = view.context.findActivity()?.window
        val c = w?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        c?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) c?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else c?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { c?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Passe en image dans l'image (16:9), si l'appareil le permet. */
fun Context.enterPip(): Boolean {
    val a = findActivity() ?: return false
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
    return runCatching {
        a.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
    }.getOrDefault(false)
}

/**
 * Mode portrait : la vidéo (16:9) est en haut ; dessous, le titre, les pilules d'action et, en direct, la liste
 * des chaînes de la file de zapping (un appui zappe).
 */
@Composable
fun PortraitInfo(
    live: Boolean, liveLabel: String, title: String, subtitle: String?, pills: List<ControlPill>,
    entries: List<com.ultratv.tv.nativeapp.ui.player.PlayerViewModel.DrawerEntry>, channelsLabel: String,
    onPick: (com.ultratv.tv.nativeapp.data.db.ChannelEntity) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Color(0xFF0A0A0C)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (live) LiveBadge(liveLabel)
            Text(title, color = Color(0xFFF5F5F7), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Color(0xFFA1A1AA), fontFamily = Manrope, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pills.forEach { PlayerPill(it) } }
        if (live && entries.isNotEmpty()) {
            Text(channelsLabel.uppercase(), color = Color(0xFFA1A1AA), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(horizontal = 20.dp))
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val currentIndex = entries.indexOfFirst { it.isCurrent }
            LaunchedEffect(currentIndex) { if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f, fill = true), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
                items(entries.size, key = { entries[it].channel.id }) { i ->
                    val e = entries[i]
                    com.ultratv.tv.nativeapp.ui.design.FocusSurface(
                        onClick = { onPick(e.channel) }, shape = RoundedCornerShape(14.dp),
                        bg = if (e.isCurrent) com.ultratv.tv.nativeapp.ui.design.Ux.Surface2 else Color(0xFF141418),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) { _ ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            com.ultratv.tv.nativeapp.ui.design.LogoBox(e.channel.logo, e.channel.title, Modifier.width(48.dp).height(32.dp), radius = 12, pad = 4, bg = Color(0xFF26262D))
                            Column(Modifier.weight(1f)) {
                                Text(e.channel.title, color = Color(0xFFF5F5F7), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.now?.title.orEmpty(), color = Color(0xFFA1A1AA), fontFamily = Manrope, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (e.isCurrent) Box(Modifier.size(8.dp).clip(CircleShape).background(com.ultratv.tv.nativeapp.ui.design.Ux.Accent))
                        }
                    }
                }
            }
        }
    }
}
