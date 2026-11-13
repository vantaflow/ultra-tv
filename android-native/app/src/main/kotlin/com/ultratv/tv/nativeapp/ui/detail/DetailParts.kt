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

/** Dégradés de lisibilité (vers la gauche et vers le bas) peints en UNE passe de dessin, sans couche supplémentaire. */
@Composable
fun DetailScrim() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bg = Ux.Bg
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val side = Brush.horizontalGradient(
                0f to bg.copy(alpha = 0.97f), 0.45f to bg.copy(alpha = 0.86f), 0.75f to bg.copy(alpha = 0.35f), 1f to Color.Transparent,
                startX = if (rtl) size.width else 0f, endX = if (rtl) 0f else size.width,
            )
            val bottom = Brush.verticalGradient(0.45f to Color.Transparent, 1f to bg.copy(alpha = 0.92f))
            onDrawBehind { drawRect(side); drawRect(bottom) }
        },
    )
}

/** Pastille de type : liseré accent + libellé espacé (FILM / SÉRIE). */
@Composable
fun TypeBadge(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.design).height(34.design).clip(RoundedCornerShape(3.design)).background(Ux.Accent))
        Spacer(Modifier.width(14.design))
        Text(text, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, letterSpacing = 5.sp, maxLines = 1)
    }
}

/** Grand titre de la fiche. */
@Composable
fun DetailTitle(title: String) {
    Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 72.spx, lineHeight = 76.spx, letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 1100.design))
}

/** « 2001 | Comédie | 9 saisons · 182 épisodes | IMDb 8.4 | 🍅 87% ». */
@Composable
fun InfoLineRow(line: InfoLine, extraBadges: List<String> = emptyList()) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
        line.texts.forEachIndexed { i, b ->
            if (i > 0) Box(Modifier.width(2.design).height(24.design).background(Ux.LineKey))
            Text(b, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.design))
        }
        line.imdb?.let {
            Box(Modifier.clip(RoundedCornerShape(8.design)).background(Color(0xFFF5C518)).padding(horizontal = 12.design, vertical = 3.design)) {
                Text(it, color = Color(0xFF0A0A0C), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
            }
        }
        line.rt?.let { Text("🍅 $it", color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1) }
        for (b in extraBadges) {
            Box(Modifier.clip(RoundedCornerShape(8.design)).background(Ux.Surface2).padding(horizontal = 12.design, vertical = 3.design)) {
                Text(b, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
            }
        }
    }
}

/** Synopsis sur 3 lignes. */
@Composable
fun DetailSynopsis(text: String) {
    Text(text, color = Ux.Text2, fontFamily = Manrope, fontSize = 25.spx, lineHeight = 36.spx, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 1100.design))
}

/** Bouton « Favoris » (cœur plein quand le titre y est) : même magasin que le cœur historique. */
@Composable
fun FavoritePill(kind: String, remoteId: String, modifier: Modifier = Modifier, vm: FavoriteToggleViewModel = hiltViewModel()) {
    LaunchedEffect(kind, remoteId) { vm.set(kind, remoteId) }
    val on by vm.isFav.collectAsState()
    val D = LocalDs.current
    PillButton(D.favoritesShort, onClick = { vm.toggle() }, modifier = modifier, heightPx = 64, hPadPx = 30, fontPx = 22, iconPath = Icons.Heart, iconFill = on)
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

/** Libellé de rangée (« Réalisateur », « Distribution ») suivi de ses pastilles. */
@Composable
fun PeopleRow(label: String, names: List<String>, onOpenPerson: (String) -> Unit) {
    if (names.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(14.design), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
        names.forEach { PersonPill(it, { onOpenPerson(it) }) }
    }
}
