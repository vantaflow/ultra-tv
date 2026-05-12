package com.ultratv.tv.nativeapp.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
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
import com.ultratv.tv.nativeapp.ui.common.PosterCard

@Composable
fun SeriesScreen(onOpen: (Long) -> Unit, vm: SeriesListViewModel = hiltViewModel()) {
    val list by vm.items.collectAsState()
    val cats by vm.categories.collectAsState()
    val sel by vm.selectedCategory.collectAsState()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Series", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        CategoryChips(categories = cats, selected = sel, onSelect = vm::selectCategory)
        Text("${list.size} titles", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.id }) { s ->
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
