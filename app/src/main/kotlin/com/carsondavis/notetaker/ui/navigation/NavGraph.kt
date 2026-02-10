package com.carsondavis.notetaker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.ui.screens.AuthScreen
import com.carsondavis.notetaker.ui.screens.NoteInputScreen
import com.carsondavis.notetaker.ui.screens.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object AuthRoute
@Serializable object NoteRoute
@Serializable object SettingsRoute

@Composable
fun AppNavGraph(authManager: AuthManager) {
    val isAuthenticated by authManager.isAuthenticated.collectAsState(initial = false)
    val hasRepo by authManager.hasRepo.collectAsState(initial = false)

    val navController = rememberNavController()

    val startDestination: Any = if (isAuthenticated && hasRepo) NoteRoute else AuthRoute

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
    }
}
