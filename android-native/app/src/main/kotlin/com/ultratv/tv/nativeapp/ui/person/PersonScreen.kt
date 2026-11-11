package com.ultratv.tv.nativeapp.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import com.ultratv.tv.nativeapp.data.tmdb.PersonLogic
import com.ultratv.tv.nativeapp.i18n.personBadgeMovie
import com.ultratv.tv.nativeapp.i18n.personBadgeSeries
import com.ultratv.tv.nativeapp.i18n.personBorn
import com.ultratv.tv.nativeapp.i18n.personInPlaylist
import com.ultratv.tv.nativeapp.i18n.personReadLess
import com.ultratv.tv.nativeapp.i18n.personReadMore
import com.ultratv.tv.nativeapp.i18n.personRole
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.tmdb.PersonInfo
import com.ultratv.tv.nativeapp.data.tmdb.PersonRepository
import com.ultratv.tv.nativeapp.data.tmdb.TmdbImages
import com.ultratv.tv.nativeapp.data.tmdb.PersonWorks
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val repo: PersonRepository,
) : ViewModel() {
    /** null = en cours de chargement. */
    private val _works = MutableStateFlow<PersonWorks?>(null)
    val works: StateFlow<PersonWorks?> = _works

    private val _info = MutableStateFlow<PersonInfo?>(null)
    val info: StateFlow<PersonInfo?> = _info

    fun load(name: String) {
        if (_works.value != null) return
        viewModelScope.launch { _info.value = runCatching { repo.info(name) }.getOrNull() }
        viewModelScope.launch {
            val ps = providers.observeProviders().first()
            val pid = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id
            _works.value = if (pid == null) PersonWorks(emptyList(), emptyList()) else runCatching { repo.works(pid, name) }.getOrDefault(PersonWorks(emptyList(), emptyList()))
        }
    }
}

/** Une œuvre de la playlist dans la rangée de la page (film ou série). */
private data class Work(val tv: Boolean, val id: Long, val title: String, val poster: String?, val year: Int?, val rating: Double?)

/**
 * Page d'un acteur ou d'un réalisateur : photo ronde, nom, « métier · naissance · lieu », biographie sur 5 lignes
 * (« Lire la suite » la déplie), puis ses films et séries présents dans la playlist, en une rangée d'affiches.
 */
@Composable
fun PersonScreen(name: String, onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit, vm: PersonViewModel = hiltViewModel()) {
    LaunchedEffect(name) { vm.load(name) }
    val D = LocalDs.current
    val works by vm.works.collectAsState()
    val info by vm.info.collectAsState()
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    val list = remember(works) {
        works?.let { w -> (w.movies.map { Work(false, it.id, it.title, it.poster, it.year, it.rating) } + w.series.map { Work(true, it.id, it.title, it.poster, it.year, it.rating) }).sortedByDescending { it.year ?: 0 } }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .then(if (touch) Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp) else Modifier.padding(start = 72.design, end = 96.design, top = 54.design, bottom = 40.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 32.design),
    ) {
        PersonHeader(name, info, touch)
        when {
            list == null -> Text(D.personLoading, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 14.sp else 24.spx)
            list.isEmpty() -> Text(D.personEmpty, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 14.sp else 24.spx)
            else -> {
                Text(D.personInPlaylist(list.size), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 18.sp else 32.spx)
                val cell = if (touch) 112.dp else 220.design
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(if (touch) 12.dp else 28.design),
                    // Marge autour des cellules : l'anneau de focus et son léger agrandissement ne sont pas rognés.
                    contentPadding = PaddingValues(horizontal = if (touch) 2.dp else 8.design, vertical = if (touch) 2.dp else 10.design),
                ) {
                    items(list, key = { "${if (it.tv) "s" else "m"}-${it.id}" }) { w ->
                        WorkCell(w, cell, touch) { if (w.tv) onOpenSeries(w.id) else onOpenMovie(w.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(name: String, info: PersonInfo?, touch: Boolean) {
    val D = LocalDs.current
    val photoSize = if (touch) 88.dp else 220.design
    val photo = remember(info?.photo) { info?.photo?.let { TmdbImages.profile(it) } }
    val meta = remember(info, D.lang) {
        listOfNotNull(
            D.personRole(info?.department),
            PersonLogic.birthdayLabel(info?.birthday)?.let { D.personBorn(it) },
            info?.place,
        ).joinToString("  ·  ")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (touch) 14.dp else 40.design), verticalAlignment = Alignment.Top) {
        ThumbImage(photo, name, Modifier.size(photoSize), radius = if (touch) 44 else 110, decodeSize = DpSize(photoSize, photoSize))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 14.design)) {
            Text(name, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 26.sp else 52.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 13.sp else 26.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val bio = info?.bio
            if (bio != null) {
                var expanded by remember(bio) { mutableStateOf(false) }
                var overflows by remember(bio) { mutableStateOf(false) }
                Text(
                    bio, color = Ux.Text2, fontFamily = Manrope, fontSize = if (touch) 14.sp else 26.spx, lineHeight = if (touch) 20.sp else 38.spx,
                    maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                )
                if (overflows || expanded) PillButton(
                    if (expanded) D.personReadLess else D.personReadMore, { expanded = !expanded },
                    heightPx = if (touch) 48 else 64, hPadPx = 30, fontPx = if (touch) 20 else 24, weight = FontWeight.Bold,
                    iconPath = if (expanded) "M6 15l6-6 6 6" else "M6 9l6 6 6-6",
                )
            }
        }
    }
}

/** Affiche de la rangée : pastille FILM / SÉRIE en haut à gauche, note ★ en bas à droite, titre et année dessous. Image à taille explicite. */
@Composable
private fun WorkCell(w: Work, cell: androidx.compose.ui.unit.Dp, touch: Boolean, onClick: () -> Unit) {
    val D = LocalDs.current
    Column(Modifier.width(cell), verticalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 12.design)) {
        FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.design), bg = Ux.Surface, ringWidth = 5.design, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(Modifier.fillMaxSize()) {
                PosterImage(w.poster, w.title, Modifier.fillMaxSize(), radius = 18, kind = if (w.tv) TmdbKind.TV else TmdbKind.MOVIE, year = w.year, decodeSize = DpSize(cell, cell * 1.5f))
                Text(
                    if (w.tv) D.personBadgeSeries else D.personBadgeMovie, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 10.sp else 20.spx, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(if (touch) 6.dp else 10.design).clip(RoundedCornerShape(8.design)).background(Color(0xB30A0A0C)).padding(horizontal = 10.design, vertical = 4.design),
                )
                w.rating?.takeIf { it > 0 }?.let { r ->
                    Text(
                        "★ " + String.format(java.util.Locale.ROOT, "%.1f", r), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 11.sp else 22.spx, maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(if (touch) 6.dp else 10.design).clip(RoundedCornerShape(8.design)).background(Color(0xB30A0A0C)).padding(horizontal = 10.design, vertical = 4.design),
                    )
                }
            }
        }
        Text(w.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = if (touch) 13.sp else 24.spx, lineHeight = if (touch) 16.sp else 29.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(w.year?.toString().orEmpty(), color = Ux.Text3, fontFamily = Manrope, fontSize = if (touch) 12.sp else 22.spx, maxLines = 1, modifier = Modifier.height(if (touch) 16.dp else 30.design))
    }
}
