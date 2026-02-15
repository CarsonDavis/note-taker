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
    }

    private object EncryptedKeys {
        const val ACCESS_TOKEN = "access_token"
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

    suspend fun signOut() {
        encryptedPrefs.edit().clear().apply()
        context.dataStore.edit { it.clear() }
    }
}
