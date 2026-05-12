package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun HomeScreen(
    onGoLive: () -> Unit,
    onGoMovies: () -> Unit,
    onGoSeries: () -> Unit,
    onGoSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Welcome to Ultra TV", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Native build · D-pad ready", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onGoLive) { Text("Live TV", fontSize = 18.sp) }
            Button(onClick = onGoMovies) { Text("Movies", fontSize = 18.sp) }
            Button(onClick = onGoSeries) { Text("Series", fontSize = 18.sp) }
            Button(onClick = onGoSettings) { Text("Settings", fontSize = 18.sp) }
        }
    }
}
