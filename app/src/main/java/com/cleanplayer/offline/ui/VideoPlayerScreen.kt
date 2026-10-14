package com.cleanplayer.offline.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.cleanplayer.offline.data.PlaybackPreferences
import com.cleanplayer.offline.player.PlayerManager
import com.cleanplayer.offline.theme.AccentBlueLight
import com.cleanplayer.offline.ui.components.GestureHUD
import com.cleanplayer.offline.ui.components.GestureHUDState
import com.cleanplayer.offline.ui.components.PlaybackSettingsDialog
import com.cleanplayer.offline.ui.components.TrackSelectorDialog
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUriString: String,
    videoTitle: String,
    startPositionMs: Long? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val preferences = remember { PlaybackPreferences(context) }
    val playerManager = remember { PlayerManager(context) }

    val videoUri = remember(videoUriString) { Uri.parse(videoUriString) }
    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()
    val currentResizeMode by playerManager.currentResizeMode.collectAsState()
    val currentSpeed by playerManager.playbackSpeed.collectAsState()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    var orientationMode by remember { mutableIntStateOf(preferences.getOrientationMode()) }

    var hudState by remember { mutableStateOf<GestureHUDState>(GestureHUDState.Idle) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    LaunchedEffect(orientationMode) {
        activity?.requestedOrientation = when (orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        preferences.saveOrientationMode(orientationMode)
    }

    LaunchedEffect(videoUri, startPositionMs) {
        playerManager.prepareAndPlay(videoUri, startPositionMs)
    }

    LaunchedEffect(areControlsVisible, isPlaying, isScrubbing) {
        if (areControlsVisible && isPlaying && !isScrubbing) {
            delay(3500)
            areControlsVisible = false
        }
    }

    LaunchedEffect(isPlaying, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                currentPositionMs = playerManager.player.currentPosition
                totalDurationMs = playerManager.player.duration.coerceAtLeast(0L)
                isPlaying = playerManager.player.isPlaying
            }
            delay(300)
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            playerManager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player Surface (Isolated Recomposition)
        PlayerViewSurface(
            playerManager = playerManager,
            resizeMode = currentResizeMode
        )

        // Gesture Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(onTap = { areControlsVisible = !areControlsVisible })
                        return@pointerInput
                    }

                    var dragMode = 0
                    var accumulatedDeltaY = 0f
                    var accumulatedDeltaX = 0f
                    var startSeekPosition = 0L
                    var accumulatedVolFloat = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            accumulatedDeltaY = 0f
                            accumulatedDeltaX = 0f
                            startSeekPosition = playerManager.player.currentPosition
                            accumulatedVolFloat = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

                            val isRightHalf = offset.x > (screenWidthPx * 0.55f)
                            val isLeftHalf = offset.x < (screenWidthPx * 0.45f)

                            dragMode = if (isRightHalf) 1 else if (isLeftHalf) 2 else 0
                        },
                        onDragEnd = {
                            if (dragMode == 3) {
                                val seekState = hudState as? GestureHUDState.Seek
                                if (seekState != null) {
                                    playerManager.player.seekTo(seekState.targetMs)
                                    currentPositionMs = seekState.targetMs
                                }
                            }
                            hudState = GestureHUDState.Idle
                            dragMode = 0
                        },
                        onDragCancel = {
                            hudState = GestureHUDState.Idle
                            dragMode = 0
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDeltaX += dragAmount.x
                            accumulatedDeltaY += dragAmount.y

                            if (dragMode != 3 && abs(accumulatedDeltaX) > 40f && abs(accumulatedDeltaX) > abs(accumulatedDeltaY)) {
                                dragMode = 3
                            }

                            when (dragMode) {
                                1 -> {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    if (maxVol > 0f) {
                                        val deltaVol = (-dragAmount.y / (screenHeightPx * 0.5f)) * maxVol
                                        accumulatedVolFloat = (accumulatedVolFloat + deltaVol).coerceIn(0f, maxVol)
                                        val targetVolInt = accumulatedVolFloat.roundToInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                        val percent = ((accumulatedVolFloat / maxVol) * 100).roundToInt().coerceIn(0, 100)
                                        hudState = GestureHUDState.Volume(percent)
                                    }
                                }

                                2 -> {
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        var currentB = lp.screenBrightness
                                        if (currentB < 0f) currentB = 0.5f
                                        val deltaB = -dragAmount.y / (screenHeightPx * 0.7f)
                                        val targetB = (currentB + deltaB).coerceIn(0.01f, 1.0f)
                                        lp.screenBrightness = targetB
                                        act.window.attributes = lp
                                        hudState = GestureHUDState.Brightness((targetB * 100).toInt())
                                    }
                                }

                                3 -> {
                                    val duration = playerManager.player.duration.coerceAtLeast(1L)
                                    val scrubFactor = 90.0
                                    val deltaSeconds = ((accumulatedDeltaX / screenWidthPx) * scrubFactor).toLong()
                                    val targetMs = (startSeekPosition + (deltaSeconds * 1000)).coerceIn(0L, duration)
                                    hudState = GestureHUDState.Seek(deltaSeconds, targetMs, duration)
                                }
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (!isLocked) {
                                if (offset.x < screenWidthPx / 2) {
                                    val newPos = (playerManager.player.currentPosition - 10000).coerceAtLeast(0L)
                                    playerManager.player.seekTo(newPos)
                                    hudState = GestureHUDState.Seek(-10, newPos, playerManager.player.duration)
                                } else {
                                    val newPos = (playerManager.player.currentPosition + 10000).coerceAtMost(playerManager.player.duration)
                                    playerManager.player.seekTo(newPos)
                                    hudState = GestureHUDState.Seek(+10, newPos, playerManager.player.duration)
                                }
                            }
                        },
                        onTap = {
                            areControlsVisible = !areControlsVisible
                        }
                    )
                }
        )

        GestureHUD(
            state = hudState,
            modifier = Modifier.align(Alignment.Center)
        )

        AnimatedVisibility(
            visible = areControlsVisible || isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        ) {
            IconButton(
                onClick = { isLocked = !isLocked },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock Controls",
                    tint = if (isLocked) AccentBlueLight else Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = areControlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = videoTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = {
                    orientationMode = (orientationMode + 1) % 3
                }) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Rotation",
                        tint = if (orientationMode != 0) AccentBlueLight else Color.White
                    )
                }

                IconButton(onClick = { showTrackDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Tracks",
                        tint = if (audioTracks.size > 1) AccentBlueLight else Color.White
                    )
                }

                IconButton(onClick = { showTrackDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        tint = if (subtitleTracks.any { it.isSelected }) AccentBlueLight else Color.White
                    )
                }

                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = areControlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerBottomControls(
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                isPlaying = isPlaying,
                isScrubbing = isScrubbing,
                scrubPositionMs = scrubPositionMs,
                onScrubStart = { frac ->
                    isScrubbing = true
                    scrubPositionMs = (frac * totalDurationMs.toFloat()).toLong()
                },
                onScrubFinish = { target ->
                    playerManager.player.seekTo(target)
                    currentPositionMs = target
                    isScrubbing = false
                },
                onPlayPauseToggle = {
                    if (playerManager.player.isPlaying) {
                        playerManager.player.pause()
                    } else {
                        playerManager.player.play()
                    }
                    isPlaying = playerManager.player.isPlaying
                },
                onRewind10 = {
                    val newPos = (playerManager.player.currentPosition - 10000).coerceAtLeast(0L)
                    playerManager.player.seekTo(newPos)
                    currentPositionMs = newPos
                },
                onForward10 = {
                    val newPos = (playerManager.player.currentPosition + 10000).coerceAtMost(playerManager.player.duration)
                    playerManager.player.seekTo(newPos)
                    currentPositionMs = newPos
                },
                onCycleResizeMode = {
                    playerManager.cycleResizeMode()
                }
            )
        }
    }

    if (showTrackDialog) {
        TrackSelectorDialog(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            onSelectAudio = { track -> playerManager.selectAudioTrack(track) },
            onSelectSubtitle = { track -> playerManager.selectSubtitleTrack(track) },
            onDismiss = { showTrackDialog = false }
        )
    }

    if (showSettingsDialog) {
        PlaybackSettingsDialog(
            currentSpeed = currentSpeed,
            currentResizeMode = currentResizeMode,
            onSpeedChange = { speed -> playerManager.setSpeed(speed) },
            onResizeModeChange = { mode -> playerManager.cycleResizeMode() },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerViewSurface(
    playerManager: PlayerManager,
    resizeMode: Int
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = playerManager.player
                useController = false
                this.resizeMode = resizeMode
            }
        },
        update = { view ->
            view.resizeMode = resizeMode
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PlayerBottomControls(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    onScrubStart: (Float) -> Unit,
    onScrubFinish: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onCycleResizeMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val activePosition = if (isScrubbing) scrubPositionMs else currentPositionMs
        val sliderPos = if (totalDurationMs > 0) {
            (activePosition.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Slider(
            value = sliderPos,
            onValueChange = onScrubStart,
            onValueChangeFinished = {
                val target = if (totalDurationMs > 0) (sliderPos * totalDurationMs.toFloat()).toLong() else 0L
                onScrubFinish(target)
            },
            colors = SliderDefaults.colors(
                thumbColor = AccentBlueLight,
                activeTrackColor = AccentBlueLight,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentSec = activePosition / 1000
            val totalSec = totalDurationMs / 1000
            val currentStr = String.format(Locale.getDefault(), "%02d:%02d", currentSec / 60, currentSec % 60)
            val totalStr = String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60)

            Text(
                text = "$currentStr / $totalStr",
                color = Color.White,
                fontSize = 12.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRewind10) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentBlueLight)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(onClick = onForward10) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                }
            }

            IconButton(onClick = onCycleResizeMode) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Aspect Ratio",
                    tint = Color.White
                )
            }
        }
    }
}
