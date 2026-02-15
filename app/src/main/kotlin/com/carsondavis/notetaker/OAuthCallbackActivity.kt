package com.carsondavis.notetaker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.carsondavis.notetaker.data.auth.OAuthCallbackData
import com.carsondavis.notetaker.data.auth.OAuthCallbackHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject lateinit var callbackHolder: OAuthCallbackHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        val code = uri?.getQueryParameter("code")
        val state = uri?.getQueryParameter("state")

        if (code != null) {
            callbackHolder.setCallback(OAuthCallbackData(code = code, state = state ?: ""))
        }

        // Bounce to MainActivity
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}
