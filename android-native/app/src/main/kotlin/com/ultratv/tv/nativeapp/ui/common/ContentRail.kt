package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import com.ultratv.tv.nativeapp.ui.theme.UltraType

/**
 * Netflix-style rail with the design bundle's editorial header (eyebrow + large
 * sans title). The TV-specific scroll behavior comes from Compose-TV's focus
 * handling: when focus moves outside the viewport, the LazyRow scrolls.
 */
@Composable
fun <T : Any> ContentRail(
    title: String,
    items: List<T>,
    itemKey: (T) -> Any,
    cardWidth: Dp = 200.dp,
    eyebrow: String? = null,
    emptyHint: String? = null,
    item: @Composable (T) -> Unit,
) {
    if (items.isEmpty() && emptyHint == null) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.padding(start = UltraTokens.EdgeGutter, end = UltraTokens.EdgeGutter)) {
            if (eyebrow != null) {
                Text(
                    eyebrow,
                    color = UltraTokens.Fg3,
                    fontSize = UltraType.Eyebrow.fontSize,
                    letterSpacing = UltraType.Eyebrow.letterSpacing,
                    style = UltraType.Eyebrow,
                )
            }
            Text(
                title,
                color = UltraTokens.Fg,
                style = UltraType.RailTitle,
                modifier = Modifier.padding(top = if (eyebrow != null) 4.dp else 0.dp),
            )
        }
        if (items.isEmpty()) {
            Text(
                emptyHint.orEmpty(),
                fontSize = 13.sp,
                color = UltraTokens.Fg3,
                modifier = Modifier.padding(start = UltraTokens.EdgeGutter),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = UltraTokens.EdgeGutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = itemKey) { entry ->
                    Column(Modifier.width(cardWidth)) { item(entry) }
                }
            }
        }
    }
}
