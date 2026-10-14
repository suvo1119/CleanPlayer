package com.cleanplayer.offline.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.cleanplayer.offline.data.PlaybackPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TrackInfo(
    val id: String,
    val name: String,
    val language: String?,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int
)

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val preferences = PlaybackPreferences(context)
    private var currentUri: Uri? = null

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _currentResizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val currentResizeMode: StateFlow<Int> = _currentResizeMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                updateAvailableTracks(tracks)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentUri?.let { uri ->
                        preferences.savePosition(uri.toString(), 0L, player.duration)
                    }
                }
            }
        })
    }

    fun prepareAndPlay(uri: Uri, startPositionMs: Long? = null) {
        currentUri = uri
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()

        val resumePos = startPositionMs ?: preferences.getPosition(uri.toString())
        if (resumePos > 0) {
            player.seekTo(resumePos)
        }
        player.playWhenReady = true
    }

    fun saveCurrentPosition() {
        val uri = currentUri ?: return
        val currentPos = player.currentPosition
        val duration = player.duration
        if (duration > 0 && currentPos > 0) {
            preferences.savePosition(uri.toString(), currentPos, duration)
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.playbackParameters = PlaybackParameters(speed)
        preferences.saveSpeed(speed)
    }

    fun cycleResizeMode(): Int {
        val nextMode = when (_currentResizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _currentResizeMode.value = nextMode
        preferences.saveResizeMode(nextMode)
        return nextMode
    }

    fun selectAudioTrack(trackInfo: TrackInfo) {
        val currentTracks = player.currentTracks
        val group = currentTracks.groups[trackInfo.groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackInfo.trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    fun selectSubtitleTrack(trackInfo: TrackInfo?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (trackInfo == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val currentTracks = player.currentTracks
            val group = currentTracks.groups[trackInfo.groupIndex]
            val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackInfo.trackIndex))
            builder.setOverrideForType(override)
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun getLanguageDisplayName(langCode: String?): String? {
        if (langCode.isNullOrBlank()) return null
        val cleanCode = langCode.trim().lowercase(Locale.ROOT)

        val knownMap = mapOf(
            "hin" to "Hindi", "hi" to "Hindi",
            "tam" to "Tamil", "ta" to "Tamil",
            "tel" to "Telugu", "te" to "Telugu",
            "kan" to "Kannada", "kn" to "Kannada",
            "mal" to "Malayalam", "ml" to "Malayalam",
            "eng" to "English", "en" to "English",
            "ben" to "Bengali", "bn" to "Bengali",
            "mar" to "Marathi", "mr" to "Marathi",
            "pan" to "Punjabi", "pa" to "Punjabi",
            "guj" to "Gujarati", "gu" to "Gujarati",
            "urd" to "Urdu", "ur" to "Urdu",
            "spa" to "Spanish", "es" to "Spanish",
            "fre" to "French", "fra" to "French", "fr" to "French",
            "ger" to "German", "deu" to "German", "de" to "German",
            "jpn" to "Japanese", "ja" to "Japanese", "zho" to "Chinese",
            "chi" to "Chinese", "zh" to "Chinese", "rus" to "Russian",
            "ru" to "Russian", "ara" to "Arabic", "ar" to "Arabic",
            "por" to "Portuguese", "pt" to "Portuguese", "ita" to "Italian",
            "it" to "Italian", "kor" to "Korean", "ko" to "Korean"
        )

        knownMap[cleanCode]?.let { return it }

        return try {
            val locale = Locale.forLanguageTag(cleanCode)
            val name = locale.getDisplayLanguage(Locale.ENGLISH)
            if (name.isNotBlank() && !name.equals(cleanCode, ignoreCase = true)) {
                name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
            } else {
                cleanCode.uppercase(Locale.ROOT)
            }
        } catch (_: Exception) {
            cleanCode.uppercase(Locale.ROOT)
        }
    }

    private fun updateAvailableTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackInfo>()
        val textList = mutableListOf<TrackInfo>()

        for (groupIndex in tracks.groups.indices) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                val langCode = format.language
                val langDisplay = getLanguageDisplayName(langCode)
                val rawLabel = format.label

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    val channelDesc = when (format.channelCount) {
                        1 -> "Mono"
                        2 -> "Stereo"
                        6 -> "5.1 Surround"
                        8 -> "7.1 Surround"
                        else -> if (format.channelCount > 0) "${format.channelCount} Ch" else null
                    }

                    val displayName = when {
                        !rawLabel.isNullOrBlank() && !langDisplay.isNullOrBlank() -> {
                            if (rawLabel.contains(langDisplay, ignoreCase = true)) {
                                rawLabel
                            } else {
                                "$langDisplay ($rawLabel)"
                            }
                        }
                        !langDisplay.isNullOrBlank() -> {
                            if (channelDesc != null) "$langDisplay ($channelDesc)" else langDisplay
                        }
                        !rawLabel.isNullOrBlank() -> rawLabel
                        else -> "Audio Track ${trackIndex + 1}${if (channelDesc != null) " ($channelDesc)" else ""}"
                    }

                    audioList.add(
                        TrackInfo(
                            id = format.id ?: "$groupIndex-$trackIndex",
                            name = displayName,
                            language = langDisplay ?: langCode,
                            isSelected = isSelected,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex
                        )
                    )
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    val subDisplayName = when {
                        !rawLabel.isNullOrBlank() && !langDisplay.isNullOrBlank() -> {
                            if (rawLabel.contains(langDisplay, ignoreCase = true)) rawLabel else "$langDisplay ($rawLabel)"
                        }
                        !langDisplay.isNullOrBlank() -> langDisplay
                        !rawLabel.isNullOrBlank() -> rawLabel
                        else -> "Subtitle Track ${trackIndex + 1}"
                    }

                    textList.add(
                        TrackInfo(
                            id = format.id ?: "$groupIndex-$trackIndex",
                            name = subDisplayName,
                            language = langDisplay ?: langCode,
                            isSelected = isSelected,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex
                        )
                    )
                }
            }
        }

        _audioTracks.value = audioList
        _subtitleTracks.value = textList
    }

    fun release() {
        saveCurrentPosition()
        player.release()
    }
}
