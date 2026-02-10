package com.carsondavis.notetaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.carsondavis.notetaker.ui.screens.NoteInputScreen
import com.carsondavis.notetaker.ui.theme.NoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NoteCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteInputScreen(
                        onSettingsClick = {
                            // From lock screen, settings requires unlock.
                            // Use requestDismissKeyguard to prompt biometric/PIN
                            val keyguardManager = getSystemService(
                                android.app.KeyguardManager::class.java
                            )
                            keyguardManager.requestDismissKeyguard(
                                this@NoteCaptureActivity,
                                object : android.app.KeyguardManager.KeyguardDismissCallback() {
                                    override fun onDismissSucceeded() {
                                        val intent = android.content.Intent(
                                            this@NoteCaptureActivity,
                                            MainActivity::class.java
                                        ).apply {
                                            putExtra("open_settings", true)
                                        }
                                        startActivity(intent)
                                        finish()
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
