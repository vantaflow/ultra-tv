package com.ultratv.tv.nativeapp.ui.player

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text

@Composable
fun PlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 3000
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Column {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(url.substringBefore('?').takeLast(60), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            Button(onClick = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "video/*")
                        putExtra("title", title)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with…"))
                }
            }) { Text("Open in external player") }
        }
    }
}
