package com.carsondavis.notetaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.ui.navigation.AppNavGraph
import com.carsondavis.notetaker.ui.theme.NoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialRoute = when {
            intent.getBooleanExtra("open_settings", false) -> "settings"
            intent.getBooleanExtra("open_browse", false) -> "browse"
            else -> null
        }

        setContent {
            NoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(
                        authManager = authManager,
                        initialRoute = initialRoute
                    )
                }
            }
        }
    }
}
