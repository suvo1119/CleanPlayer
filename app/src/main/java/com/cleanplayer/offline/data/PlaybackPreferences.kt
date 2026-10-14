package com.cleanplayer.offline.data

import android.content.Context
import android.content.SharedPreferences

class PlaybackPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleanplayer_playback_prefs", Context.MODE_PRIVATE)

    fun savePosition(mediaUri: String, positionMs: Long, durationMs: Long) {
        val targetPosition = if (durationMs > 0 && positionMs > durationMs * 0.95) {
            0L
        } else {
            positionMs
        }
        prefs.edit().putLong("pos_$mediaUri", targetPosition).apply()
        if (targetPosition > 0) {
            saveRecentVideo(mediaUri)
        }
    }

    fun getPosition(mediaUri: String): Long {
        return prefs.getLong("pos_$mediaUri", 0L)
    }

    fun saveRecentVideo(mediaUri: String) {
        val currentList = getRecentVideos().toMutableList()
        currentList.remove(mediaUri)
        currentList.add(0, mediaUri)
        val trimmed = currentList.take(15)
        prefs.edit().putString("recent_videos", trimmed.joinToString(",,,")).apply()
    }

    fun getRecentVideos(): List<String> {
        val raw = prefs.getString("recent_videos", null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",,,").filter { it.isNotBlank() }
    }

    fun saveSpeed(speed: Float) {
        prefs.edit().putFloat("default_speed", speed).apply()
    }

    fun getSpeed(): Float {
        return prefs.getFloat("default_speed", 1.0f)
    }

    fun saveResizeMode(mode: Int) {
        prefs.edit().putInt("default_resize_mode", mode).apply()
    }

    fun getResizeMode(): Int {
        return prefs.getInt("default_resize_mode", 0) // 0 = FIT
    }

    fun saveIsGridViewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_grid_view_enabled", enabled).apply()
    }

    fun isGridViewEnabled(): Boolean {
        return prefs.getBoolean("is_grid_view_enabled", false)
    }

    fun saveOrientationMode(mode: Int) {
        prefs.edit().putInt("orientation_mode", mode).apply()
    }

    fun getOrientationMode(): Int {
        return prefs.getInt("orientation_mode", 0) // 0 = Auto Sensor, 1 = Landscape, 2 = Portrait
    }
}
