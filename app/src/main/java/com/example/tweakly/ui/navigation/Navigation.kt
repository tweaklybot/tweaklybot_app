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
import com.example.tweakly.data.repository.SettingsRepository
import com.example.tweakly.ui.auth.AuthScreen
import com.example.tweakly.ui.editor.PhotoEditorScreen
import com.example.tweakly.ui.gallery.GalleryScreen
import com.example.tweakly.ui.onboarding.OnboardingScreen
import com.example.tweakly.ui.search.SearchScreen
import com.example.tweakly.ui.settings.SettingsScreen
import com.example.tweakly.ui.subscription.SubscriptionScreen
import com.example.tweakly.ui.viewer.ViewerScreen

sealed class Route(val path: String) {
    object Onboarding   : Route("onboarding")
    object Auth         : Route("auth")
    object Gallery      : Route("gallery")
    object Settings     : Route("settings")
    object Subscription : Route("subscription")
    object Search       : Route("search")
    object Viewer       : Route("viewer/{mediaId}") { fun go(id: Long) = "viewer/$id" }
    object Editor       : Route("editor/{mediaUri}/{mediaName}") {
        fun go(uri: String, name: String) = "editor/${java.net.URLEncoder.encode(uri,"UTF-8")}/${java.net.URLEncoder.encode(name,"UTF-8")}"
    }
}

@Composable
fun TweaklyNavGraph(
    settingsRepository: SettingsRepository = hiltViewModel<NavHelperViewModel>().settingsRepo
) {
    val navController = rememberNavController()
    val settings by settingsRepository.settings.collectAsState(initial = null)

    if (settings == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val s = settings!!
    val start = when {
        !s.skipOnboarding -> Route.Onboarding.path
        s.isGuestMode     -> Route.Gallery.path
        else              -> Route.Auth.path
    }

    NavHost(navController = navController, startDestination = start,
        enterTransition  = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(250)) },
        exitTransition   = { slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200)) },
        popEnterTransition  = { slideInHorizontally(tween(300)) { -it } + fadeIn(tween(250)) },
        popExitTransition   = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) }
    ) {
        composable(Route.Onboarding.path) {
            OnboardingScreen(
                onContinue = { navController.navigate(Route.Auth.path) { popUpTo(Route.Onboarding.path) { inclusive = true } } },
                onSkip     = { navController.navigate(Route.Gallery.path) { popUpTo(Route.Onboarding.path) { inclusive = true } } }
            )
        }
        composable(Route.Auth.path) {
            AuthScreen(
                onSuccess = { navController.navigate(Route.Gallery.path) { popUpTo(Route.Auth.path) { inclusive = true } } },
                onGuest   = { navController.navigate(Route.Gallery.path) { popUpTo(Route.Auth.path) { inclusive = true } } }
            )
        }
        composable(Route.Gallery.path) {
            GalleryScreen(
                onMediaClick     = { navController.navigate(Route.Viewer.go(it)) },
                onSettingsClick  = { navController.navigate(Route.Settings.path) },
                onSearchClick    = { navController.navigate(Route.Search.path) },
                onSubscribeClick = { navController.navigate(Route.Subscription.path) }
            )
        }
        composable(Route.Viewer.path,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })) { back ->
            ViewerScreen(
                mediaId = back.arguments!!.getLong("mediaId"),
                onBack  = { navController.popBackStack() },
                onEdit  = { uri, name -> navController.navigate(Route.Editor.go(uri, name)) }
            )
        }
        composable(Route.Editor.path,
            arguments = listOf(
                navArgument("mediaUri")  { type = NavType.StringType },
                navArgument("mediaName") { type = NavType.StringType }
            )) { back ->
            PhotoEditorScreen(
                mediaUri  = java.net.URLDecoder.decode(back.arguments!!.getString("mediaUri")!!, "UTF-8"),
                mediaName = java.net.URLDecoder.decode(back.arguments!!.getString("mediaName")!!, "UTF-8"),
                onBack    = { navController.popBackStack() }
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                onBack           = { navController.popBackStack() },
                onLogout         = { navController.navigate(Route.Auth.path) { popUpTo(0) { inclusive = true } } },
                onSubscribeClick = { navController.navigate(Route.Subscription.path) }
            )
        }
        composable(Route.Subscription.path) {
            SubscriptionScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Search.path) {
            SearchScreen(
                onBack       = { navController.popBackStack() },
                onMediaClick = { navController.navigate(Route.Viewer.go(it)) }
            )
        }
    }
}
