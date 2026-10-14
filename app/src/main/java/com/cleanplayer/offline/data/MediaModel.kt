package com.cleanplayer.offline.data

import android.net.Uri
import java.util.Locale

data class VideoItem(
    val id: Long,
    val contentUri: Uri,
    val filePath: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val folderName: String,
    val folderPath: String,
    val lastPositionMs: Long = 0L,
    val dateAdded: Long = 0L
) {
    fun getFormattedDuration(): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun getFormattedSize(): String {
        val mb = sizeBytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }

    fun getResolutionBadge(): String? {
        return when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 1920 || height >= 1080 -> "FHD"
            width >= 1280 || height >= 720 -> "HD"
            else -> null
        }
    }
}

data class FolderItem(
    val folderName: String,
    val folderPath: String,
    val videoCount: Int,
    val representativeUri: Uri?
)
