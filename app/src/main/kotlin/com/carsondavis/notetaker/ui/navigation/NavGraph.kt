package com.carsondavis.notetaker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.ui.screens.AuthScreen
import com.carsondavis.notetaker.ui.screens.BrowseScreen
import com.carsondavis.notetaker.ui.screens.NoteInputScreen
import com.carsondavis.notetaker.ui.screens.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object AuthRoute
@Serializable object NoteRoute
@Serializable object SettingsRoute
@Serializable object BrowseRoute

@Composable
fun AppNavGraph(
    authManager: AuthManager,
    initialRoute: String? = null
) {
    // Use null initial to avoid flash — don't render until DataStore emits
    val isAuthenticated by authManager.isAuthenticated.collectAsState(initial = null)
    val hasRepo by authManager.hasRepo.collectAsState(initial = null)

    // Wait for DataStore to emit before rendering
    if (isAuthenticated == null || hasRepo == null) return

    val authed = isAuthenticated == true && hasRepo == true

    val navController = rememberNavController()

    val startDestination: Any = if (authed) NoteRoute else AuthRoute

    // Handle initial route from intent extras (e.g. from NoteCaptureActivity)
    LaunchedEffect(initialRoute) {
        if (initialRoute != null && authed) {
            when (initialRoute) {
                "settings" -> navController.navigate(SettingsRoute)
                "browse" -> navController.navigate(BrowseRoute)
            }
        }
    }

    // Always return to NoteRoute when app comes back from background
    var isFirstStart by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (isFirstStart) {
            isFirstStart = false
        } else if (authed) {
            navController.popBackStack(NoteRoute, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<AuthRoute> {
            AuthScreen(
                onAuthComplete = {
                    navController.navigate(NoteRoute) {
                        popUpTo<AuthRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<NoteRoute> {
            NoteInputScreen(
                onSettingsClick = {
                    navController.navigate(SettingsRoute)
                },
                onBrowseClick = {
                    navController.navigate(BrowseRoute)
                }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(AuthRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<BrowseRoute> {
            BrowseScreen(
                onBack = { navController.popBackStack() },
                onSettingsClick = { navController.navigate(SettingsRoute) }
            )
        }
    }
}
