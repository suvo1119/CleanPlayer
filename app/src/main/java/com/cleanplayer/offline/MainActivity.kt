package com.cleanplayer.offline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cleanplayer.offline.theme.BgDark
import com.cleanplayer.offline.theme.CleanPlayerTheme
import com.cleanplayer.offline.ui.FolderListScreen
import com.cleanplayer.offline.ui.VideoListScreen
import com.cleanplayer.offline.ui.VideoPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private var hasStoragePermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermission()

        val intentData: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        setContent {
            CleanPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    AppNavigation(
                        hasPermission = hasStoragePermission,
                        onRequestPermission = { requestPermission() },
                        initialExternalUri = intentData
                    )
                }
            }
        }
    }

    private fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        hasStoragePermission = ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }
}

@Composable
fun AppNavigation(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    initialExternalUri: Uri?
) {
    val navController = rememberNavController()
    val startDestination = if (initialExternalUri != null) {
        val encodedUri = URLEncoder.encode(initialExternalUri.toString(), StandardCharsets.UTF_8.toString())
        "player?uri=$encodedUri&title=Playing%20Media"
    } else {
        "folders"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) + slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth / 4 } },
        exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth / 4 } },
        popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth / 4 } },
        popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth / 4 } }
    ) {
        // Screen S01: Folder List
        composable("folders") {
            FolderListScreen(
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onFolderClick = { folderPath, folderName ->
                    val encodedPath = URLEncoder.encode(folderPath, StandardCharsets.UTF_8.toString())
                    val encodedName = URLEncoder.encode(folderName, StandardCharsets.UTF_8.toString())
                    navController.navigate("videos/$encodedPath/$encodedName")
                },
                onRecentVideoClick = { video ->
                    val encodedUri = URLEncoder.encode(video.contentUri.toString(), StandardCharsets.UTF_8.toString())
                    val encodedTitle = URLEncoder.encode(video.displayName, StandardCharsets.UTF_8.toString())
                    navController.navigate("player?uri=$encodedUri&title=$encodedTitle")
                }
            )
        }

        // Screen S02: Video List within folder
        composable(
            route = "videos/{folderPath}/{folderName}",
            arguments = listOf(
                navArgument("folderPath") { type = NavType.StringType },
                navArgument("folderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val folderPath = URLDecoder.decode(backStackEntry.arguments?.getString("folderPath") ?: "", StandardCharsets.UTF_8.toString())
            val folderName = URLDecoder.decode(backStackEntry.arguments?.getString("folderName") ?: "", StandardCharsets.UTF_8.toString())

            VideoListScreen(
                folderPath = folderPath,
                folderName = folderName,
                onBackClick = { navController.popBackStack() },
                onVideoClick = { video, startPos ->
                    val encodedUri = URLEncoder.encode(video.contentUri.toString(), StandardCharsets.UTF_8.toString())
                    val encodedTitle = URLEncoder.encode(video.displayName, StandardCharsets.UTF_8.toString())
                    val posParam = if (startPos != null) "&startPos=$startPos" else ""
                    navController.navigate("player?uri=$encodedUri&title=$encodedTitle$posParam")
                }
            )
        }

        // Screen S03: Immersive Video Player
        composable(
            route = "player?uri={uri}&title={title}&startPos={startPos}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "Video" },
                navArgument("startPos") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val uriString = URLDecoder.decode(backStackEntry.arguments?.getString("uri") ?: "", StandardCharsets.UTF_8.toString())
            val titleString = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
            val startPos = backStackEntry.arguments?.getLong("startPos") ?: -1L

            VideoPlayerScreen(
                videoUriString = uriString,
                videoTitle = titleString,
                startPositionMs = if (startPos >= 0L) startPos else null,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
