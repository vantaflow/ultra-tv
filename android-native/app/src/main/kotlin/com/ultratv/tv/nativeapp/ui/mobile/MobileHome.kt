package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.design.BackdropImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.LogoMark
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.home.HeroItem

/** Données de l'accueil tactile (extraites du ViewModel pour garder l'écran sans état). */
data class MobileHomeState(
    val providersLoaded: Boolean,
    val hasProviders: Boolean,
    val hero: HeroItem?,
    val resume: List<WatchHistoryEntity>,
    val channels: List<ChannelEntity>,
    val showingFavorites: Boolean,
    val nowPlaying: Map<Long, EpgEntity>,
    val syncPercent: Int?,
    val profileInitial: String,
    val profileColor: Int,
)

/** En-tête commun aux écrans principaux tactiles : logo, recherche, profil (maquette MobileAccueil). */
@Composable
fun MobileTopBar(profileInitial: String, profileColor: Int, onSearch: () -> Unit, onProfile: () -> Unit) {
    val M = LocalMobileStrings.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoMark(68)
            Row {
                Text("ULTRA ", fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 0.8.sp, color = Ux.Text)
                Text("TV", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 0.8.sp, color = Ux.Text3)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconCircle(Icons.Search, M.a11ySearch, Ux.Surface, Ux.Text, onSearch)
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = M.a11yProfile, onClick = onProfile).semantics { contentDescription = M.a11yProfile },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(profileColor)), contentAlignment = Alignment.Center) {
                    Text(profileInitial, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/** Bouton rond de 48 dp (pastille de 40) avec description pour TalkBack. */
@Composable
fun IconCircle(icon: String, description: String, bg: Color, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { DIcon(icon, 20.dp, ink) }
    }
}

@Composable
fun MobileHomeScreen(
    state: MobileHomeState,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onGoLive: () -> Unit,
    onGoSettings: () -> Unit,
    onGoGuide: () -> Unit,
    onGoFavorites: () -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    onPlayHistory: (WatchHistoryEntity) -> Unit,
    onOpenHero: (HeroItem) -> Unit,
) {
    val D = LocalDs.current
    val wide = rememberWindowClass() != WindowClass.COMPACT
    TouchRefresh(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            MobileTopBar(state.profileInitial, state.profileColor, onSearch, onProfile)
            val h = state.hero
            when {
                h != null -> MobileHero(h, D, wide) { onOpenHero(h) }
                state.providersLoaded && !state.hasProviders -> MobileNotice(D.homeNoSource, D.syncCloudHint, D.syncCloud, onGoSettings)
                state.providersLoaded -> MobileNotice(D.homeEmpty, null, null, {}, state.syncPercent)
            }
            if (state.resume.isNotEmpty()) {
                SectionHeader(D.continueWatching, null, {})
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(state.resume, key = { "${it.kind}-${it.remoteId}" }, contentType = { "resume" }) { e -> ResumeCard(e, D) { onPlayHistory(e) } }
                }
            }
            if (state.channels.isNotEmpty()) {
                SectionHeader(if (state.showingFavorites) D.favoriteChannels else D.directTitle, LocalMobileStrings.current.seeAll, if (state.showingFavorites) onGoFavorites else onGoLive)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(state.channels, key = { it.id }, contentType = { "channel" }) { c -> FavChannelCard(c, state.nowPlaying[c.id]) { onPlay(c.streamUrl, c.title) } }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (action != null) Text(action, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 14.dp))
    }
}

/** Hero « À la une » : carte 216 dp (tablette : 280), visuel plein cadre, voile, titre et bouton Regarder. */
@Composable
private fun MobileHero(h: HeroItem, D: DesignStrings, wide: Boolean, onOpen: () -> Unit) {
    val S = LocalStrings.current
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(if (wide) 300.dp else 216.dp).clip(shape).background(Ux.Tone).clickable(onClickLabel = D.watch, onClick = onOpen)) {
        if (h.backdrop != null) BackdropImage(h.backdrop, Modifier.fillMaxSize())
        else PosterImage(h.poster, h.title, Modifier.align(Alignment.CenterEnd).fillMaxSize(), radius = 0)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xC70A0A0C)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (h.kind == HeroItem.Kind.SERIES) S.seriesTitle else S.moviesTitle).uppercase(), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ux.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                val meta = listOfNotNull(h.year?.toString(), h.genre, h.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = Color(0xFFC4C4CC), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(h.title, color = Color(0xFFF5F5F7), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MobileNotice(title: String, hint: String?, action: String?, onAction: () -> Unit, percent: Int? = null) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ux.SurfaceDeep).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        if (hint != null) Text(hint, color = Ux.Text2, fontFamily = Manrope, fontSize = 14.sp)
        if (percent != null || action == null) ProgressLine((percent ?: 0) / 100f, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)), heightPx = 8, track = Ux.Surface2)
        if (action != null) PillButton(action, onAction, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
    }
}

/** Carte « Reprendre » : 200 dp de large, vignette 112 dp avec progression, titre et temps restant. */
@Composable
private fun ResumeCard(e: WatchHistoryEntity, D: DesignStrings, onClick: () -> Unit) {
    val progress = if (e.durationMs > 0) e.positionMs.toFloat() / e.durationMs else 0f
    val mins = ((e.durationMs - e.positionMs).coerceAtLeast(0) / 60_000).toInt()
    val meta = when {
        e.durationMs <= 0 -> null
        mins >= 60 -> D.hourMinLeft.format(mins / 60, mins % 60)
        else -> D.minLeft.format(mins)
    }
    val title = TitleCleaner.clean(e.title).title
    Column(Modifier.width(200.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = title, onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(112.dp)) {
            ThumbImage(e.poster, e.title, Modifier.fillMaxSize(), radius = 28)
            ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 8, track = Color(0x66000000))
        }
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) Text(meta, color = Ux.Text3, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Carte chaîne 120×84 dp : logo en boîte fixe, nom sur une ligne ; le programme en cours sert de description TalkBack. */
@Composable
private fun FavChannelCard(c: ChannelEntity, now: EpgEntity?, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.dp), bg = Ux.SurfaceDeep, modifier = Modifier.width(120.dp).height(84.dp).semantics { contentDescription = c.title + (now?.title?.let { " — $it" } ?: "") }) { _ ->
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            LogoBox(c.logo, c.title, Modifier.width(44.dp).height(28.dp), radius = 12, pad = 4)
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
