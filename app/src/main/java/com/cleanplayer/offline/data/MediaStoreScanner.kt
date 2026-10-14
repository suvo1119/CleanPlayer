package com.cleanplayer.offline.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    private val preferences = PlaybackPreferences(context)
    @Volatile
    private var cachedVideos: List<VideoItem>? = null

    fun clearCache() {
        cachedVideos = null
    }

    suspend fun getAllVideos(forceRefresh: Boolean = false): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedVideos?.let { return@withContext it }
        }

        val videos = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            } else {
                MediaStore.Video.Media.DATA
            }
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video_$id"
                    val path = cursor.getString(dataColumn) ?: ""
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val file = File(path)
                    val folderName = file.parentFile?.name ?: "Internal Storage"
                    val folderPath = file.parentFile?.absolutePath ?: ""

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val lastPosition = preferences.getPosition(contentUri.toString())

                    if (size > 0 && duration > 0) {
                        videos.add(
                            VideoItem(
                                id = id,
                                contentUri = contentUri,
                                filePath = path,
                                displayName = name,
                                durationMs = duration,
                                sizeBytes = size,
                                width = width,
                                height = height,
                                folderName = folderName,
                                folderPath = folderPath,
                                lastPositionMs = lastPosition,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedVideos = videos
        videos
    }

    suspend fun getRecentVideosList(): List<VideoItem> = withContext(Dispatchers.IO) {
        val allVideos = getAllVideos()
        val recentUris = preferences.getRecentVideos()
        if (recentUris.isNotEmpty()) {
            val videoMap = allVideos.associateBy { it.contentUri.toString() }
            recentUris.mapNotNull { uriStr -> videoMap[uriStr] }
        } else {
            allVideos.filter { it.lastPositionMs > 0 }.sortedByDescending { it.lastPositionMs }
        }
    }

    suspend fun getFolders(forceRefresh: Boolean = false): List<FolderItem> = withContext(Dispatchers.IO) {
        val videos = getAllVideos(forceRefresh)
        val folderMap = mutableMapOf<String, MutableList<VideoItem>>()

        for (video in videos) {
            val list = folderMap.getOrPut(video.folderPath) { mutableListOf() }
            list.add(video)
        }

        folderMap.map { (path, folderVideos) ->
            FolderItem(
                folderName = folderVideos.first().folderName,
                folderPath = path,
                videoCount = folderVideos.size,
                representativeUri = folderVideos.firstOrNull()?.contentUri
            )
        }.sortedByDescending { it.videoCount }
    }

    suspend fun getVideosInFolder(folderPath: String, forceRefresh: Boolean = false): List<VideoItem> = withContext(Dispatchers.IO) {
        getAllVideos(forceRefresh).filter { it.folderPath == folderPath }
    }
}
