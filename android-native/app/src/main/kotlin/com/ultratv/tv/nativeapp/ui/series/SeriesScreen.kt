package com.ultratv.tv.nativeapp.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.CategoryChips
import com.ultratv.tv.nativeapp.ui.common.ContentRail
import com.ultratv.tv.nativeapp.ui.common.HeroBanner
import com.ultratv.tv.nativeapp.ui.common.PosterCard

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(onOpen: (Long) -> Unit, vm: SeriesListViewModel = hiltViewModel()) {
    val sel by vm.selectedCategory.collectAsState()
    val cats by vm.categories.collectAsState()
    val rails by vm.rails.collectAsState()
    val featured by vm.featured.collectAsState()
    val flatItems by vm.items.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    val railsMode = sel == null
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        if (railsMode && featured != null) {
            HeroBanner(
                eyebrow = "Série du moment",
                title = featured!!.name,
                subtitle = featured!!.plot,
                meta = listOfNotNull(
                    featured!!.year?.toString(),
                    featured!!.rating?.let { "★ %.1f".format(it) },
                ),
                image = featured!!.poster,
                primaryLabel = S.open,
                onPrimary = { onOpen(featured!!.id) },
                secondaryLabel = "Plus d'infos",
                onSecondary = { onOpen(featured!!.id) },
            )
        } else {
            Spacer(Modifier.height(60.dp))
            Text(
                S.seriesTitle,
                fontFamily = com.ultratv.tv.nativeapp.ui.theme.UltraFonts.Serif,
                fontSize = 64.sp,
                letterSpacing = (-1.5).sp,
                color = com.ultratv.tv.nativeapp.ui.theme.UltraTokens.Fg,
                modifier = Modifier.padding(start = com.ultratv.tv.nativeapp.ui.theme.UltraTokens.EdgeGutter),
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(start = com.ultratv.tv.nativeapp.ui.theme.UltraTokens.EdgeGutter, bottom = 12.dp)) {
            CategoryChips(categories = cats, selected = sel, onSelect = vm::selectCategory)
        }

        if (railsMode) {
            if (rails.isEmpty()) {
                Text(
                    S.noSeries,
                    color = com.ultratv.tv.nativeapp.ui.theme.UltraTokens.Fg3,
                    modifier = Modifier.padding(start = com.ultratv.tv.nativeapp.ui.theme.UltraTokens.EdgeGutter),
                )
            }
            rails.forEachIndexed { idx, rail ->
                ContentRail(
                    title = rail.category?.name ?: S.railOther,
                    eyebrow = if (idx == 0) "Séries" else null,
                    items = rail.items,
                    itemKey = { it.id },
                ) { s ->
                    PosterCard(
                        title = s.name,
                        poster = s.poster,
                        subtitle = s.year?.toString(),
                        placeholderEmoji = "📺",
                    ) { onOpen(s.id) }
                }
            }
            Spacer(Modifier.height(40.dp))
        } else {
            val paged = vm.pagedSeries.collectAsLazyPagingItems()
            Text("${paged.itemCount} titles loaded${if (paged.loadState.append is androidx.paging.LoadState.Loading) "…" else ""}",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(720.dp),
            ) {
                items(
                    count = paged.itemCount,
                    key = { idx -> paged.peek(idx)?.id ?: idx },
                ) { idx ->
                    val s = paged[idx] ?: return@items
                    PosterCard(
                        title = s.name,
                        poster = s.poster,
                        subtitle = s.year?.toString(),
                        placeholderEmoji = "📺",
                    ) { onOpen(s.id) }
                }
            }
        }
    }
    }
}
