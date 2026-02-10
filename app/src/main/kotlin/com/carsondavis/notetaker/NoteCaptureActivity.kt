package com.carsondavis.notetaker

import android.app.KeyguardManager
import android.content.Intent
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
                        onSettingsClick = { dismissAndNavigate("open_settings") },
                        onBrowseClick = { dismissAndNavigate("open_browse") }
                    )
                }
            }
        }
    }

    private fun dismissAndNavigate(extraKey: String) {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    val intent = Intent(
                        this@NoteCaptureActivity,
                        MainActivity::class.java
                    ).apply {
                        putExtra(extraKey, true)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        )
    }
}
