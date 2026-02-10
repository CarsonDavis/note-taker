package com.carsondavis.notetaker.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth")

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USERNAME = stringPreferencesKey("username")
        val REPO_OWNER = stringPreferencesKey("repo_owner")
        val REPO_NAME = stringPreferencesKey("repo_name")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }
    val repoOwner: Flow<String?> = context.dataStore.data.map { it[Keys.REPO_OWNER] }
    val repoName: Flow<String?> = context.dataStore.data.map { it[Keys.REPO_NAME] }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ACCESS_TOKEN] != null
    }

    val hasRepo: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.REPO_OWNER] != null && it[Keys.REPO_NAME] != null
    }

    suspend fun saveAuth(token: String, username: String) {
        context.dataStore.edit {
            it[Keys.ACCESS_TOKEN] = token
            it[Keys.USERNAME] = username
        }
    }

    suspend fun saveRepo(owner: String, name: String) {
        context.dataStore.edit {
            it[Keys.REPO_OWNER] = owner
            it[Keys.REPO_NAME] = name
        }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.clear() }
    }
}
