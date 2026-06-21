package com.carsondavis.notetaker.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth")

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPrefs: SharedPreferences
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USERNAME = stringPreferencesKey("username")
        val REPO_OWNER = stringPreferencesKey("repo_owner")
        val REPO_NAME = stringPreferencesKey("repo_name")
        val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
        val AUTH_TYPE = stringPreferencesKey("auth_type") // "pat" or "oauth"
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
        val TOKEN_UPDATED_AT = longPreferencesKey("token_updated_at")
        val VOICE_MODE = stringPreferencesKey("voice_mode") // "on_device" or "cloud"
        val OPENAI_KEY_UPDATED_AT = longPreferencesKey("openai_key_updated_at")
    }

    private object EncryptedKeys {
        const val ACCESS_TOKEN = "access_token"
        const val OPENAI_API_KEY = "openai_api_key"
    }

    companion object {
        const val VOICE_MODE_ON_DEVICE = "on_device"
        const val VOICE_MODE_CLOUD = "cloud"
    }

    /**
     * One-time migration: move token from plain DataStore to EncryptedSharedPreferences.
     * Called once at startup.
     */
    suspend fun migrateTokenIfNeeded() {
        val oldToken = context.dataStore.data.first()[Keys.ACCESS_TOKEN]
        if (oldToken != null && encryptedPrefs.getString(EncryptedKeys.ACCESS_TOKEN, null) == null) {
            encryptedPrefs.edit().putString(EncryptedKeys.ACCESS_TOKEN, oldToken).apply()
            context.dataStore.edit { it.remove(Keys.ACCESS_TOKEN) }
            // If no auth type is set, this is a legacy PAT user
            val authType = context.dataStore.data.first()[Keys.AUTH_TYPE]
            if (authType == null) {
                context.dataStore.edit { it[Keys.AUTH_TYPE] = "pat" }
            }
        }
    }

    val accessToken: Flow<String?> = context.dataStore.data.map {
        // Read from encrypted prefs; DataStore's TOKEN_UPDATED_AT triggers re-emission
        @Suppress("UNUSED_VARIABLE")
        val trigger = it[Keys.TOKEN_UPDATED_AT]
        encryptedPrefs.getString(EncryptedKeys.ACCESS_TOKEN, null)
    }

    val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }
    val repoOwner: Flow<String?> = context.dataStore.data.map { it[Keys.REPO_OWNER] }
    val repoName: Flow<String?> = context.dataStore.data.map { it[Keys.REPO_NAME] }

    val authType: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_TYPE] }

    val installationId: Flow<String?> = context.dataStore.data.map { it[Keys.INSTALLATION_ID] }

    /** Which voice engine to use: [VOICE_MODE_ON_DEVICE] (default) or [VOICE_MODE_CLOUD]. */
    val voiceMode: Flow<String> = context.dataStore.data.map {
        it[Keys.VOICE_MODE] ?: VOICE_MODE_ON_DEVICE
    }

    /** User's own OpenAI API key for cloud transcription (BYOK), encrypted at rest. */
    val openAiKey: Flow<String?> = context.dataStore.data.map {
        @Suppress("UNUSED_VARIABLE")
        val trigger = it[Keys.OPENAI_KEY_UPDATED_AT]
        encryptedPrefs.getString(EncryptedKeys.OPENAI_API_KEY, null)
    }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map {
        encryptedPrefs.getString(EncryptedKeys.ACCESS_TOKEN, null) != null
    }

    val hasRepo: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.REPO_OWNER] != null && it[Keys.REPO_NAME] != null
    }

    val onboardingShown: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ONBOARDING_SHOWN] == true
    }

    suspend fun saveAuth(token: String, username: String) {
        encryptedPrefs.edit().putString(EncryptedKeys.ACCESS_TOKEN, token).apply()
        context.dataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.AUTH_TYPE] = "pat"
            it[Keys.TOKEN_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun saveOAuthTokens(accessToken: String, username: String) {
        encryptedPrefs.edit().putString(EncryptedKeys.ACCESS_TOKEN, accessToken).apply()
        context.dataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.AUTH_TYPE] = "oauth"
            it[Keys.TOKEN_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun saveRepo(owner: String, name: String) {
        context.dataStore.edit {
            it[Keys.REPO_OWNER] = owner
            it[Keys.REPO_NAME] = name
        }
    }

    suspend fun saveInstallationId(id: String) {
        context.dataStore.edit {
            it[Keys.INSTALLATION_ID] = id
        }
    }

    suspend fun markOnboardingShown() {
        context.dataStore.edit {
            it[Keys.ONBOARDING_SHOWN] = true
        }
    }

    suspend fun setVoiceMode(mode: String) {
        context.dataStore.edit { it[Keys.VOICE_MODE] = mode }
    }

    suspend fun setOpenAiKey(key: String?) {
        if (key.isNullOrBlank()) {
            encryptedPrefs.edit().remove(EncryptedKeys.OPENAI_API_KEY).apply()
        } else {
            encryptedPrefs.edit().putString(EncryptedKeys.OPENAI_API_KEY, key.trim()).apply()
        }
        // Bump trigger so the openAiKey flow re-emits (encrypted prefs aren't observable).
        context.dataStore.edit { it[Keys.OPENAI_KEY_UPDATED_AT] = System.currentTimeMillis() }
    }

    /**
     * Sign out: revoke token and clear storage, but preserve installation_id
     * so returning users get the authorize URL instead of the install URL.
     */
    suspend fun signOut() {
        val prefs = context.dataStore.data.first()
        val savedInstallationId = prefs[Keys.INSTALLATION_ID]
        // Voice settings are device preferences / the user's own key, unrelated to
        // GitHub auth — preserve them across a disconnect (cleared only by clearAllData).
        val savedVoiceMode = prefs[Keys.VOICE_MODE]
        val savedOpenAiKey = encryptedPrefs.getString(EncryptedKeys.OPENAI_API_KEY, null)
        encryptedPrefs.edit().clear().apply()
        context.dataStore.edit { it.clear() }
        if (savedOpenAiKey != null) {
            encryptedPrefs.edit().putString(EncryptedKeys.OPENAI_API_KEY, savedOpenAiKey).apply()
        }
        context.dataStore.edit {
            if (savedInstallationId != null) it[Keys.INSTALLATION_ID] = savedInstallationId
            if (savedVoiceMode != null) it[Keys.VOICE_MODE] = savedVoiceMode
        }
    }

    /**
     * Full wipe: clear everything including installation_id (factory reset).
     * Used by "Delete All Data" in settings.
     */
    suspend fun clearAllData() {
        encryptedPrefs.edit().clear().apply()
        context.dataStore.edit { it.clear() }
    }

    /**
     * Clear stale installation_id when the GitHub App has been uninstalled
     * by the user from GitHub Settings.
     */
    suspend fun clearInstallationId() {
        context.dataStore.edit { it.remove(Keys.INSTALLATION_ID) }
    }
}
