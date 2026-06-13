package com.prplegryn.moegirl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    onReload: () -> Unit,
    onSavePosition: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(file.id, state.downloadUrl) {
        if (state.downloadUrl.isNullOrBlank()) {
            onLoad()
        }
    }

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val previousOrientation = activity.requestedOrientation
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                activity.requestedOrientation = previousOrientation
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(onBack = onBack)

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
            state.downloadUrl != null -> ModernVideoPlayer(
                file = file,
                url = state.downloadUrl,
                savedPositionMs = state.savedPositionMs,
                onSavePosition = onSavePosition,
                onBack = onBack,
                onReload = onReload,
                onRotate = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                },
            )
        }
    }
}

@Composable
private fun ModernVideoPlayer(
    file: CloudFile,
    url: String,
    savedPositionMs: Long,
    onSavePosition: (Long) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onRotate: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            if (savedPositionMs > 1_000L) seekTo(savedPositionMs)
            playWhenReady = true
        }
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(savedPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    val effectivePosition = seekPreviewMs ?: positionMs

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            delay(350L)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(1_500L)
            onSavePosition(player.currentPosition.coerceAtLeast(0L))
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, seekPreviewMs) {
        if (controlsVisible && isPlaying && seekPreviewMs == null) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    DisposableEffect(player) {
        onDispose {
            onSavePosition(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player, durationMs) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        },
                    )
                }
                .pointerInput(player, durationMs) {
                    detectDragGestures(
                        onDragStart = {
                            if (durationMs > 0L) {
                                controlsVisible = true
                                seekPreviewMs = player.currentPosition.coerceAtLeast(0L)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (durationMs > 0L && seekPreviewMs != null) {
                                change.consume()
                                val deltaMs = (dragAmount.x * 420f).toLong()
                                seekPreviewMs = ((seekPreviewMs ?: positionMs) + deltaMs).coerceIn(0L, durationMs)
                            }
                        },
                        onDragEnd = {
                            seekPreviewMs?.let { target ->
                                player.seekTo(target)
                                positionMs = target
                                onSavePosition(target)
                            }
                            seekPreviewMs = null
                        },
                        onDragCancel = {
                            seekPreviewMs = null
                        },
                    )
                },
        )

        if (isBuffering && seekPreviewMs == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopBar(
                title = file.name,
                onBack = onBack,
                onReload = onReload,
                onRotate = onRotate,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomControls(
                player = player,
                positionMs = effectivePosition,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onInteraction = { controlsVisible = true },
                onSavePosition = onSavePosition,
            )
        }

        seekPreviewMs?.let { preview ->
            SeekPreview(
                positionMs = preview,
                durationMs = durationMs,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent),
                ),
            )
            .systemBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
private fun PlayerBottomControls(
    player: ExoPlayer,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onInteraction: () -> Unit,
    onSavePosition: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Slider(
            value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = { value ->
                onInteraction()
                player.seekTo(value.toLong().coerceIn(0L, durationMs.coerceAtLeast(1L)))
            },
            onValueChangeFinished = {
                onSavePosition(player.currentPosition.coerceAtLeast(0L))
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    onInteraction()
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Replay10, contentDescription = "后退 10 秒", tint = Color.White)
            }
            IconButton(
                onClick = {
                    onInteraction()
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(
                onClick = {
                    onInteraction()
                    val end = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end))
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Forward10, contentDescription = "前进 10 秒", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SeekPreview(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
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

private fun formatTime(rawMs: Long): String {
    val totalSeconds = (rawMs.coerceAtLeast(0L) / 1000L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
