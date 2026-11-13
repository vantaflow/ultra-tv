package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.ui.design.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.favoritesShort
import com.ultratv.tv.nativeapp.ui.common.FavoriteToggleViewModel
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Taille de décodage unique du fond de fiche : 1280×720 px suffisent (la source TMDB fait 1280 px) et ménagent la mémoire d'une box 32 bits. */
private const val BACKDROP_W = 1280
private const val BACKDROP_H = 720

/**
 * Fond plein écran de la fiche : UNE seule grande image, décodée à taille explicite, sans flou ni animation.
 * Un « fond » portrait (certaines sources y mettent l'affiche) est détecté au chargement : on montre alors l'affiche 2:3 nette
 * à droite sur un aplat, jamais agrandie en plein cadre.
 */
@Composable
fun DetailBackdrop(backdrop: String?, poster: String?, title: String) {
    val ctx = LocalContext.current
    var portrait by remember(backdrop) { mutableStateOf(backdrop.isNullOrBlank()) }
    Box(Modifier.fillMaxSize().background(Ux.Tone)) {
        if (!backdrop.isNullOrBlank() && !portrait) {
            val request = remember(backdrop) {
                ImageRequest.Builder(ctx).data(backdrop).size(BACKDROP_W, BACKDROP_H).scale(coil3.size.Scale.FILL).crossfade(false).build()
            }
            AsyncImage(
                model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { st -> val img = st.result.image; if (img.width.toFloat() / img.height.coerceAtLeast(1) < 1.3f) portrait = true },
                onError = { portrait = true },
            )
        }
        if (portrait) {
            val art = poster ?: backdrop
            PosterImage(art, title, Modifier.align(Alignment.CenterEnd).padding(end = 150.design).height(660.design).aspectRatio(2f / 3f), radius = 22, decodeSize = DpSize(220.dp, 330.dp))
        }
    }
}

/**
 * Logo officiel d'un titre (Fanart.tv) à la place du grand titre texte. Tant que l'image n'est pas chargée (ou si elle
 * manque, échoue ou n'existe pas), [fallback] (le titre texte) reste affiché ; le titre sert de description d'accessibilité.
 * Décodage borné à 800 × 310 px (taille d'un « clearlogo »), sans animation.
 */
@Composable
fun TitleLogo(logo: String?, title: String, maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    if (logo.isNullOrBlank()) { fallback(); return }
    val ctx = LocalContext.current
    var loaded by remember(logo) { mutableStateOf(false) }
    val request = remember(logo) { ImageRequest.Builder(ctx).data(logo).size(800, 310).crossfade(false).build() }
    Box(modifier) {
        if (!loaded) fallback()
        AsyncImage(
            model = request, contentDescription = if (loaded) title else null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = if (loaded) Modifier.widthIn(max = maxWidth).heightIn(max = maxHeight) else Modifier.size(1.dp),
            onSuccess = { loaded = true }, onError = { loaded = false },
        )
    }
}







/** Pastille cliquable (réalisateur, acteur) vers la page de la personne. */
@Composable
fun PersonPill(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(26.design), bg = Ux.Surface2, ringWidth = 4.design, focusedScale = 1.03f, modifier = modifier.height(52.design)) { f ->
        Box(Modifier.height(52.design).padding(horizontal = 24.design), contentAlignment = Alignment.Center) {
            Text(name, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 300.design))
        }
    }
}


