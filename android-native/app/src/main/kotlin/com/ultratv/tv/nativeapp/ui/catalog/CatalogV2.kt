package com.ultratv.tv.nativeapp.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.BackdropImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LocalAnimations
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Catalogue Films / Séries, disposition TV de la nouvelle interface (maquettes « Films ») : onglets de catégories,
 * hero « À la une », rangées d'affiches (l'affiche focalisée grossit, porte un contour et son titre dessous).
 * Rendu seul : les données et les actions viennent de l'appelant (aucun ViewModel ici, testable avec des données fictives).
 * Palette : jetons [Ux] (clair et sombre). Aucun flou ; une seule échelle par affiche focalisée.
 */

/** Hero : construit à partir du premier titre de la première rangée (seuls champs réels de la source). */
@Immutable
data class CatalogHero(val id: Long, val title: String, val meta: String, val image: String?, val synopsis: String? = null)

fun heroOf(p: PosterItem): CatalogHero = CatalogHero(
    id = p.id, title = p.title,
    meta = listOfNotNull(p.year?.toString(), p.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · "),
    image = p.poster,
)

/** Onglet : [key] null = « Tous ». */
@Immutable
data class CatalogTabUi(val key: String?, val label: String)

@Composable
internal fun CatalogRowsTv(
    tabs: List<CatalogTabUi>,
    selectedKey: String?,
    onTab: (String?) -> Unit,
    filters: @Composable () -> Unit,
    hero: CatalogHero?,
    featuredLabel: String,
    watchLabel: String,
    onWatch: (Long) -> Unit,
    heroExtra: @Composable () -> Unit,
    chips: List<CategoryChip>,
    labelOf: @Composable (CategoryChip) -> String,
    rowItems: @Composable (String) -> List<PosterItem>?,
    seeAllLabel: String,
    onOpen: (Long) -> Unit,
    onSeeAll: (String) -> Unit,
    firstPoster: FocusRequester,
    onFirstPosterFocus: (Boolean) -> Unit,
    kind: TmdbKind? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.design),
        contentPadding = PaddingValues(top = 54.design, bottom = 54.design),
    ) {
        item(key = "tabs", contentType = "tabs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.design), contentPadding = PaddingValues(start = 72.design, end = 96.design)) {
                items(tabs, key = { it.key ?: "__all" }, contentType = { "tab" }) { t -> Chip(t.label, selectedKey == t.key) { onTab(t.key) } }
            }
        }
        item(key = "filters", contentType = "filters") { Box(Modifier.padding(start = 72.design, end = 96.design)) { filters() } }
        if (hero != null) item(key = "hero", contentType = "hero") {
            CatalogHeroTv(hero, featuredLabel, watchLabel, { onWatch(hero.id) }, heroExtra)
        }
        items(chips.size, key = { chips[it].remoteId }, contentType = { "row" }) { idx ->
            val c = chips[idx]
            val list = rowItems(c.remoteId)
            Column(verticalArrangement = Arrangement.spacedBy(18.design)) {
                Text(
                    labelOf(c), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 72.design, end = 96.design),
                )
                if (list == null) { SkeletonRow(false); return@Column }
                // Marge verticale : l'affiche focalisée (x1,15 + contour) déborde de ~34 px en haut et en bas ; la LazyRow rogne à ses bords.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(24.design), contentPadding = PaddingValues(start = 72.design, end = 96.design, top = 40.design, bottom = 24.design)) {
                    items(list.size, key = { list[it].id }, contentType = { "poster" }) { i ->
                        val it = list[i]
                        val m = Modifier.width(200.design)
                        PosterCellTv(
                            it, if (idx == 0 && i == 0) m.focusRequester(firstPoster).onFocusChanged { f -> onFirstPosterFocus(f.isFocused) } else m,
                            kind, 200.design,
                        ) { onOpen(it.id) }
                    }
                    if (list.size >= ROW_SIZE_TV) item(key = "more-${c.remoteId}") {
                        FocusSurface(
                            onClick = { onSeeAll(c.remoteId) }, shape = RoundedCornerShape(20.design), bg = Ux.Surface, focusedScale = 1f, ringWidth = 0.dp,
                            modifier = Modifier.width(200.design).height(300.design),
                        ) { f ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(seeAllLabel, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Même valeur que la taille d'une rangée du ViewModel (20) : au-delà, « Voir tout ». */
private const val ROW_SIZE_TV = 20

/** Hero : image à droite (voile vers la gauche et le bas), pastille « À la une », titre, boutons. */
@Composable
private fun CatalogHeroTv(hero: CatalogHero, featured: String, watch: String, onWatch: () -> Unit, extra: @Composable () -> Unit) {
    val bg = Ux.Bg
    Box(Modifier.fillMaxWidth().height(440.design)) {
        BackdropImage(hero.image, Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.62f).fillMaxHeight(), decodeSize = DpSize(400.dp, 440.dp))
        // Voile de lisibilité : plein à gauche, atténué sur l'image (35 %), puis fondu vers le fond en bas.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg, 0.38f to bg, 1f to bg.copy(alpha = 0.35f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to bg)))
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 72.design).width(780.design),
            verticalArrangement = Arrangement.spacedBy(16.design),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.design), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    featured.uppercase(), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.spx, letterSpacing = 0.5.sp, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(Ux.Accent).padding(horizontal = 12.design, vertical = 5.design),
                )
                if (hero.meta.isNotBlank()) Text(hero.meta, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1)
            }
            Text(hero.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 68.spx, lineHeight = 70.spx, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hero.synopsis?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 33.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 8.design), horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically) {
                FocusSurface(onClick = onWatch, shape = CircleShape, bg = Ux.Cta, focusedBg = Ux.Accent, focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.height(60.design)) { f ->
                    Row(
                        Modifier.height(60.design).padding(horizontal = 30.design), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.design),
                    ) {
                        val ink = if (f) Color.White else Ux.TextOnLight
                        DIcon(Icons.Play, 22.design, ink, fill = true)
                        Text(watch, color = ink, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.spx, maxLines = 1, softWrap = false)
                    }
                }
                extra()
            }
        }
    }
}

/**
 * Affiche TV : 200 × 300 au repos ; focalisée, ×1,15 avec un contour de 4 px décalé de 4 px et son titre dessous
 * (titre · année · note). Un seul calque graphique ; l'état de focus n'est lu que dans la couche et le dessin.
 * Progression (si connue) : fine barre accent au bas de l'affiche.
 */
@Composable
internal fun PosterCellTv(
    item: PosterItem,
    modifier: Modifier,
    kind: TmdbKind?,
    imageWidth: Dp?,
    onClick: () -> Unit,
) {
    val anim = LocalAnimations.current
    var f by remember { mutableStateOf(false) }
    val outline = Ux.Cta
    Column(modifier.onFocusChanged { f = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(10.design)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .graphicsLayer { val s = if (f) anim.focusScale(1.15f) else 1f; scaleX = s; scaleY = s }
                .drawBehind {
                    if (f) {
                        val off = 6.design.toPx()
                        drawRoundRect(
                            outline, topLeft = Offset(-off, -off), size = Size(size.width + 2 * off, size.height + 2 * off),
                            cornerRadius = CornerRadius(20.design.toPx() + off), style = Stroke(width = 4.design.toPx()),
                        )
                    }
                },
        ) {
            FocusSurface(
                onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Tone, focusedBg = Ux.Tone, focusedScale = 1f, ringWidth = 0.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    PosterImage(
                        item.poster, item.title, Modifier.fillMaxSize(), radius = 20, kind = kind,
                        decodeSize = imageWidth?.let { DpSize(it, it * 1.5f) },
                    )
                    com.ultratv.tv.nativeapp.ui.common.LangBadge(item.lang, modifier = Modifier.align(Alignment.TopStart).padding(10.design))
                    if (item.watched) WatchedBadge(Modifier.align(Alignment.BottomEnd).padding(8.design))
                    if (item.progress > 0f) Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.design, vertical = 12.design).height(5.design)
                            .clip(RoundedCornerShape(3.design)).background(Color(0x80000000)),
                    ) { Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress.coerceIn(0f, 1f)).background(Ux.Accent)) }
                }
            }
        }
        // Zone du titre réservée (rangées alignées) ; le titre n'apparaît qu'au focus, sous l'affiche agrandie.
        Box(Modifier.fillMaxWidth().height(56.design)) {
            if (f) {
                val meta = remember(item.year, item.rating) { listOfNotNull(item.year?.toString(), item.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · ") }
                Text(
                    if (meta.isEmpty()) item.title else "${item.title} · $meta", color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.spx,
                    lineHeight = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 26.design),
                )
            }
        }
    }
}
