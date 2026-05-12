package com.ultratv.tv.nativeapp.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LiveScreen(onPlay: (url: String, title: String) -> Unit, vm: LiveViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val query by vm.query.collectAsState()
    val filtered by remember(channels, query) {
        derivedStateOf {
            if (query.isBlank()) channels
            else channels.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live TV", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("${filtered.size} channels", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (filtered.isEmpty()) {
            Text(
                if (channels.isEmpty())
                    "No channels yet — add an Xtream provider in Settings, then re-sync."
                else
                    "No channels match the current filter.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.id }) { c ->
                    Card(
                        onClick = { vm.resolveAndPlay(c, onPlay) },
                        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (c.logo != null) {
                                    AsyncImage(
                                        model = c.logo,
                                        contentDescription = c.name,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text("📺", fontSize = 48.sp)
                                }
                            }
                            Text(
                                c.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}
