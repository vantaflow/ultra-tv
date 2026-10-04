package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.BackdropImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LiveBadge
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.SectionTitle
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Accueil (maquette Accueil.dc.html) : hero « À la une » de 440 px, rails « Reprendre la lecture »
 * (cartes 16:9 avec progression) et « Chaînes favorites ». Aucune donnée factice : tout vient de la
 * source ; une métadonnée absente est masquée. Chaque image occupe un cadre fixe.
 */
@Composable
fun HomeScreen(
    onGoLive: () -> Unit,
    onGoMovies: () -> Unit,
    onGoSeries: () -> Unit,
    onGoSettings: () -> Unit,
    onGoCloud: () -> Unit = {},
    onGoGuide: () -> Unit = {},
    onPlay: (url: String, title: String) -> Unit = { _, _ -> },
    onOpenMovie: (Long) -> Unit = {},
    onOpenSeries: (Long) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val providers by vm.providers.collectAsState()
    val loaded by vm.providersLoaded.collectAsState()
    val hero by vm.hero.collectAsState()
    val resume by vm.continueWatching.collectAsState()
    val channels by vm.channels.collectAsState()
    val favorites by vm.showingFavorites.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()
    val sync by vm.syncStatus.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 72.design, top = 54.design, bottom = 54.design),
        verticalArrangement = Arrangement.spacedBy(48.design),
    ) {
        val h = hero
        when {
            h != null -> Hero(h, onOpen = { if (h.kind == HeroItem.Kind.SERIES) onOpenSeries(h.id) else onOpenMovie(h.id) }, onGuide = onGoGuide)
            loaded && providers.isEmpty() -> EmptyCard(D.homeNoSource, D.syncCloudHint, D.addSource, onGoSettings, D.cardCloud, onGoCloud)
            // Sources héritées non prises en charge seulement (ex. Stalker, retiré en 1.1.1) : rien ne se chargera, on le dit.
            loaded && providers.none { it.kind in com.ultratv.tv.nativeapp.ui.settings.SUPPORTED_KINDS } -> EmptyCard(D.unsupportedSource, D.unsupportedSourceHint, D.fixSource, onGoSettings)
            loaded -> LoadingCard(D.homeEmpty, sync?.percent)
        }

        if (resume.isNotEmpty()) Section(D.continueWatching) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 96.design)) {
                items(resume, key = { "${it.kind}-${it.remoteId}" }, contentType = { "resume" }) { e ->
                    ResumeCard(e) { vm.playFromHistory(e); onPlay(e.streamUrl, e.title) }
                }
            }
        }

        if (channels.isNotEmpty()) Section(if (favorites) D.favoriteChannels else LocalStrings.current.navLive) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.design), contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 96.design)) {
                items(channels, key = { it.id }, contentType = { "channel" }) { c ->
                    ChannelCard(c, nowPlaying[c.id]) { onPlay(c.streamUrl, c.title) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.design)) {
        SectionTitle(title)
        content()
    }
}

// ───────────────────────── Hero 440 px ─────────────────────────

@Composable
private fun Hero(h: HeroItem, onOpen: () -> Unit, onGuide: () -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    RequestInitialFocus(requester, hasFocus = { focused })
    Box(
        Modifier.fillMaxWidth().padding(end = 96.design).height(440.design)
            .clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep),
    ) {
        // Visuel : zone fixe de 760×440 à droite. Paysage → Crop plein cadre ; sinon l'affiche 2:3
        // dans sa propre zone fixe (jamais étirée, jamais sous le texte), sur la couleur du cadre.
        Box(Modifier.align(Alignment.CenterEnd).width(760.design).fillMaxHeight().background(Ux.Tone)) {
            if (h.backdrop != null) {
                BackdropImage(h.backdrop, Modifier.fillMaxSize())
            } else {
                PosterImage(h.poster, h.title, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(293.design), radius = 0)
            }
            // Voile de lisibilité : fondu vers le fond du cadre côté texte.
            Box(Modifier.fillMaxSize().background(com.ultratv.tv.nativeapp.ui.design.startToEndBrush(0f to Ux.SurfaceDeep, 0.45f to Ux.SurfaceDeep.copy(alpha = 0.6f), 1f to Color.Transparent)))
        }
        Column(
            Modifier.fillMaxHeight().width(880.design).padding(56.design),
            verticalArrangement = Arrangement.spacedBy(24.design, Alignment.Bottom),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                LiveBadge(if (h.kind == HeroItem.Kind.SERIES) S.seriesTitle.uppercase() else S.moviesTitle.uppercase())
                val meta = listOfNotNull(h.year?.toString(), h.genre, h.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                h.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 64.spx, lineHeight = 67.spx,
                letterSpacing = (-1).spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 820.design),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                PillButton(
                    D.watch, onClick = onOpen, heightPx = 76, hPadPx = 40, fontPx = 28, weight = FontWeight.Bold,
                    iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused },
                )
                PillButton(D.tvGuide, onClick = onGuide)
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, hint: String, action: String, onAction: () -> Unit, action2: String? = null, onAction2: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(end = 96.design).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(56.design),
        verticalArrangement = Arrangement.spacedBy(20.design),
    ) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(hint, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val requester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        RequestInitialFocus(requester, hasFocus = { focused })
        Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
            PillButton(action, onClick = onAction, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused })
            if (action2 != null) PillButton(action2, onClick = onAction2, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingCard(text: String, percent: Int?) {
    Column(
        Modifier.fillMaxWidth().padding(end = 96.design).height(440.design).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(56.design),
        verticalArrangement = Arrangement.spacedBy(24.design, Alignment.Bottom),
    ) {
        Text(text, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().height(10.design).clip(RoundedCornerShape(5.design)).background(Ux.Surface2)) {
            ProgressLine((percent ?: 0) / 100f, Modifier.fillMaxWidth(), heightPx = 10)
        }
    }
}

// ───────────────────────── Cartes ─────────────────────────

/** Carte « Reprendre » : vignette 16:9 (360×202) avec progression, titre 24, reste à voir 20. */
@Composable
private fun ResumeCard(e: WatchHistoryEntity, onClick: () -> Unit) {
    val D = LocalDs.current
    val progress = if (e.durationMs > 0) e.positionMs.toFloat() / e.durationMs else 0f
    val mins = ((e.durationMs - e.positionMs).coerceAtLeast(0) / 60_000).toInt()
    val meta = when {
        e.durationMs <= 0 -> null
        mins >= 60 -> D.hourMinLeft.format(mins / 60, mins % 60)
        else -> D.minLeft.format(mins)
    }
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Color.Transparent, modifier = Modifier.width(360.design)) { f ->
        Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
            Box(Modifier.fillMaxWidth().height(202.design)) {
                ThumbImage(e.poster, e.title, Modifier.fillMaxSize(), radius = 20)
                ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 6, track = Color(0x66000000))
            }
            Column(Modifier.padding(horizontal = 4.design).padding(bottom = 6.design), verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(TitleCleaner.clean(e.title).title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) Text(meta, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Carte chaîne 220×132 (maquette) : nom 24 + logo en boîte fixe, programme en cours 18. */
@Composable
private fun ChannelCard(c: ChannelEntity, now: EpgEntity?, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Surface, modifier = Modifier.width(220.design).height(132.design)) { f ->
        Column(Modifier.fillMaxSize().padding(20.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.design))
                LogoBox(c.logo, c.title, Modifier.width(48.design).height(32.design), radius = 6, pad = 3, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2)
            }
            Text(
                now?.title ?: "", color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
