package com.example.tweakly.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.tweakly.data.repository.AppSettings
import com.example.tweakly.data.repository.SettingsRepository
import com.example.tweakly.ui.auth.AuthScreen
import com.example.tweakly.ui.gallery.GalleryScreen
import com.example.tweakly.ui.onboarding.OnboardingScreen
import com.example.tweakly.ui.settings.SettingsScreen
import com.example.tweakly.ui.viewer.ViewerScreen

sealed class Route(val path: String) {
    object Onboarding : Route("onboarding")
    object Auth       : Route("auth")
    object Gallery    : Route("gallery")
    object Settings   : Route("settings")
    object Viewer     : Route("viewer/{mediaId}") {
        fun go(id: Long) = "viewer/$id"
    }
}

private val slideEnter  = slideInHorizontally(tween(300)) { it }
private val slideExit   = slideOutHorizontally(tween(300)) { -it }
private val slidePopEnter = slideInHorizontally(tween(300)) { -it }
private val slidePopExit  = slideOutHorizontally(tween(300)) { it }
private val fadeEnter   = fadeIn(tween(250))
private val fadeExit    = fadeOut(tween(200))

@Composable
fun TweaklyNavGraph(settingsRepository: SettingsRepository = hiltViewModel<NavHelperViewModel>().settingsRepo) {
    val navController = rememberNavController()

    // Use null as sentinel so we wait for DataStore to emit the real value
    val settings by settingsRepository.settings.collectAsState(initial = null)

    // Show a plain spinner until DataStore finishes its first read
    if (settings == null) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    val s = settings!! // safe after null check
    val start = when {
        !s.skipOnboarding -> Route.Onboarding.path
        s.isGuestMode     -> Route.Gallery.path
        else              -> Route.Auth.path
    }

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition  = { slideEnter + fadeEnter },
        exitTransition   = { slideExit + fadeExit },
        popEnterTransition  = { slidePopEnter + fadeEnter },
        popExitTransition   = { slidePopExit + fadeExit }
    ) {
        composable(Route.Onboarding.path) {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(Route.Auth.path) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Route.Gallery.path) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Auth.path) {
            AuthScreen(onSuccess = {
                navController.navigate(Route.Gallery.path) {
                    popUpTo(Route.Auth.path) { inclusive = true }
                }
            }, onGuest = {
                navController.navigate(Route.Gallery.path) {
                    popUpTo(Route.Auth.path) { inclusive = true }
                }
            })
        }

        composable(Route.Gallery.path) {
            GalleryScreen(
                onMediaClick    = { id -> navController.navigate(Route.Viewer.go(id)) },
                onSettingsClick = { navController.navigate(Route.Settings.path) }
            )
        }

        composable(
            Route.Viewer.path,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments!!.getLong("mediaId")
            ViewerScreen(mediaId = id, onBack = { navController.popBackStack() })
        }

        composable(Route.Settings.path) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Route.Auth.path) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
