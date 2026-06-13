package com.prplegryn.moegirl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.prplegryn.moegirl.data.CloudFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    file: CloudFile,
    state: PlayerUiState,
    onLoad: () -> Unit,
    onSavePosition: (Long) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(file.id) {
        onLoad()
    }

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { onClear() }
        } else {
            val previousOrientation = activity.requestedOrientation
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                activity.requestedOrientation = previousOrientation
                controller.show(WindowInsetsCompat.Type.systemBars())
                onClear()
            }
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.isLoading -> PlayerMessage(
                title = "正在准备播放",
                body = file.name,
                loading = true,
            )
            state.error != null -> PlayerMessage(
                title = "播放失败",
                body = state.error,
                loading = false,
            )
            state.downloadUrl != null -> VideoPlayer(
                url = state.downloadUrl,
                savedPositionMs = state.savedPositionMs,
                onSavePosition = onSavePosition,
            )
        }

        PlayerTopBar(
            title = file.name,
            onBack = onBack,
            onReload = onLoad,
            onRotate = {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun VideoPlayer(
    url: String,
    savedPositionMs: Long,
    onSavePosition: (Long) -> Unit,
) {
    val context = LocalContext.current
    val player = remember(url, savedPositionMs) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            if (savedPositionMs > 1_000L) seekTo(savedPositionMs)
            playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(2_000L)
            onSavePosition(player.currentPosition)
        }
    }

    DisposableEffect(player) {
        onDispose {
            onSavePosition(player.currentPosition)
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                this.player = player
            }
        },
        update = { view ->
            view.player = player
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PlayerTopBar(
    title: String,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.56f))
            .systemBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onReload, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "重新载入",
                tint = Color.White,
            )
        }
        IconButton(onClick = onRotate, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.ScreenRotation,
                contentDescription = "横屏",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun PlayerMessage(
    title: String,
    body: String,
    loading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.size(18.dp))
        }
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
