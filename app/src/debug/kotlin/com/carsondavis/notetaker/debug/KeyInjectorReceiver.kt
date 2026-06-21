package com.carsondavis.notetaker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.carsondavis.notetaker.data.auth.AuthManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY developer helper (M46). Lets us inject the OpenAI API key (and switch
 * voice mode) from the dev machine via adb, so we don't have to thumb-type an
 * `sk-...` key on the device during testing. This class lives in `src/debug` and is
 * NOT compiled into release builds.
 *
 * Usage:
 *   adb shell am broadcast \
 *     -n com.carsondavis.notetaker/.debug.KeyInjectorReceiver \
 *     --es key "$OPENAI_API_KEY" --es mode cloud
 */
class KeyInjectorReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface KeyInjectorEntryPoint {
        fun authManager(): AuthManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key")
        val mode = intent.getStringExtra("mode")
        val pending = goAsync()
        val authManager = EntryPointAccessors
            .fromApplication(context.applicationContext, KeyInjectorEntryPoint::class.java)
            .authManager()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!key.isNullOrBlank()) {
                    authManager.setOpenAiKey(key)
                    Log.d("KeyInjector", "OpenAI key set (${key.length} chars)")
                }
                if (!mode.isNullOrBlank()) {
                    authManager.setVoiceMode(mode)
                    Log.d("KeyInjector", "voice mode set to $mode")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
