package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.CategoryEntity

@Composable
fun CategoryChips(
    categories: List<CategoryEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(text = "All", on = selected == null) { onSelect(null) }
        categories.forEach { cat ->
            Chip(
                text = cat.name + if (cat.locked) " 🔒" else "",
                on = selected == cat.remoteId,
            ) { onSelect(cat.remoteId) }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = if (on) ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) else ButtonDefaults.colors(),
    ) { Text(text, fontSize = 14.sp) }
}
