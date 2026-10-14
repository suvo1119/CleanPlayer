package com.cleanplayer.offline.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanplayer.offline.theme.AccentBlueLight
import com.cleanplayer.offline.theme.OverlayBlack80
import java.util.Locale

sealed class GestureHUDState {
    object Idle : GestureHUDState()
    data class Volume(val percent: Int) : GestureHUDState()
    data class Brightness(val percent: Int) : GestureHUDState()
    data class Seek(val deltaSeconds: Long, val targetMs: Long, val totalDurationMs: Long) : GestureHUDState()
}

@Composable
fun GestureHUD(
    state: GestureHUDState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is GestureHUDState.Idle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(OverlayBlack80)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is GestureHUDState.Volume -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (state.percent == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${state.percent}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        VerticalProgressBar(percent = state.percent)
                    }
                }

                is GestureHUDState.Brightness -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Brightness",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${state.percent}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        VerticalProgressBar(percent = state.percent)
                    }
                }

                is GestureHUDState.Seek -> {
                    val sign = if (state.deltaSeconds >= 0) "+" else ""
                    val targetSec = state.targetMs / 1000
                    val totalSec = state.totalDurationMs / 1000
                    val targetStr = String.format(Locale.getDefault(), "%02d:%02d", targetSec / 60, targetSec % 60)
                    val totalStr = String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (state.deltaSeconds >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = "Seek",
                            tint = AccentBlueLight,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$sign${state.deltaSeconds}s",
                            color = AccentBlueLight,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$targetStr / $totalStr",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                GestureHUDState.Idle -> {}
            }
        }
    }
}

@Composable
private fun VerticalProgressBar(percent: Int) {
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = (percent.coerceIn(0, 100) / 100f))
                .clip(CircleShape)
                .background(AccentBlueLight)
        )
    }
}
