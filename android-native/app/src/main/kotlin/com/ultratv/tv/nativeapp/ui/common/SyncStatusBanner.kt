package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@HiltViewModel
class SyncStatusViewModel @Inject constructor(bus: SyncStatusBus) : ViewModel() {
    val status = bus.status
}

/**
 * Slim banner pinned at the top of the app while a sync runs. Shows the
 * provider name, current step, and a linear progress bar. Hidden when idle.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun SyncStatusBanner(vm: SyncStatusViewModel = hiltViewModel()) {
    val status by vm.status.collectAsState()
    AnimatedVisibility(
        visible = status != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        val s = status ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔄", fontSize = 13.sp)
                Text(s.provider, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(s.step, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                s.percent?.let { Text("· $it%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
            if (s.percent != null) {
                LinearProgressIndicator(
                    progress = { (s.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
