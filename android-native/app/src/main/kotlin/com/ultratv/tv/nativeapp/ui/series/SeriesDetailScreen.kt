package com.ultratv.tv.nativeapp.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import coil.compose.AsyncImage

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    onPlayEpisode: (url: String, title: String) -> Unit,
    vm: SeriesDetailViewModel = hiltViewModel(),
) {
    val s by vm.series.collectAsState()
    val eps by vm.episodes.collectAsState()
    val loading by vm.loading.collectAsState()
    LaunchedEffect(seriesId) { vm.load(seriesId) }

    val series = s
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    if (series == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.detailLoading, color = MaterialTheme.colorScheme.onBackground) }
        return
    }
    val T = UltraTokens
    val F = UltraFonts
    Column(
        Modifier.fillMaxSize().padding(start = 140.dp, end = 80.dp, top = 110.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(50.dp)) {
            Box(
                Modifier.width(320.dp).height(480.dp).clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (series.poster != null) AsyncImage(model = series.poster, contentDescription = series.name, modifier = Modifier.fillMaxSize())
                else Text("📺", fontSize = 80.sp)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    "SÉRIE${series.year?.let { " · $it" } ?: ""}",
                    color = T.Accent,
                    fontSize = 13.sp,
                    letterSpacing = 2.3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    series.name,
                    fontFamily = F.Serif,
                    fontSize = 72.sp,
                    lineHeight = 70.sp,
                    letterSpacing = (-2.0).sp,
                    color = T.Fg,
                    maxLines = 2,
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    series.rating?.let {
                        Text(
                            "${(it * 10).toInt()}% match",
                            color = T.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    series.year?.let { Text("$it", color = T.Fg2, fontSize = 13.sp) }
                    if (loading) Text("Chargement épisodes…", color = T.Fg3, fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))
                series.plot?.let {
                    Text(it, color = T.Fg2, fontSize = 17.sp, lineHeight = 26.sp, maxLines = 6)
                }
                Spacer(Modifier.height(24.dp))
                com.ultratv.tv.nativeapp.ui.common.FavoriteButton(kind = "SERIES", remoteId = series.remoteId)
            }
        }

        Spacer(Modifier.height(40.dp))
        Text(
            S.seriesDetailEpisodes.uppercase(),
            color = T.Fg3,
            fontSize = 11.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        if (eps.isEmpty() && !loading) {
            Text(S.seriesNoEpisodes, color = T.Fg3)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(eps, key = { it.id }) { ep ->
                    Card(
                        onClick = {
                            vm.playEpisode(series.name, series.remoteId, series.providerId, ep, onPlayEpisode)
                        },
                        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.colors(containerColor = T.Surface1),
                        modifier = Modifier.border(1.dp, T.Line, RoundedCornerShape(12.dp)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "S%02dE%02d".format(ep.season, ep.episode),
                                color = T.Accent,
                                fontSize = 13.sp,
                                fontFamily = F.Mono,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(80.dp),
                            )
                            Text(ep.title, color = T.Fg, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
