package com.example.tweakly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tweakly.ui.auth.AuthScreen
import com.example.tweakly.ui.auth.AuthViewModel
import com.example.tweakly.ui.gallery.GalleryScreen
import com.example.tweakly.ui.onboarding.OnboardingScreen
import com.example.tweakly.ui.settings.SettingsScreen
import com.example.tweakly.ui.viewer.PhotoViewerScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Auth : Screen("auth")
    object Gallery : Screen("gallery")
    object PhotoViewer : Screen("viewer/{photoId}") {
        fun createRoute(photoId: Long) = "viewer/$photoId"
    }
    object Settings : Screen("settings")
}

@Composable
fun TweaklyNavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = false)

    val startDest = if (isLoggedIn) Screen.Gallery.route else Screen.Auth.route

    NavHost(navController = navController, startDestination = startDest) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onFinish = { navController.navigate(Screen.Auth.route) })
        }
        composable(Screen.Auth.route) {
            AuthScreen(onAuthSuccess = {
                navController.navigate(Screen.Gallery.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Gallery.route) {
            GalleryScreen(
                onPhotoClick = { photoId ->
                    navController.navigate(Screen.PhotoViewer.createRoute(photoId))
                },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            route = Screen.PhotoViewer.route,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
            PhotoViewerScreen(photoId = photoId, onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
