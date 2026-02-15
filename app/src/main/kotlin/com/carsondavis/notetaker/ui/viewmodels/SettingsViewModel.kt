package com.carsondavis.notetaker.ui.viewmodels

import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.auth.OAuthTokenExchanger
import com.carsondavis.notetaker.data.local.PendingNoteDao
import com.carsondavis.notetaker.data.local.SubmissionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SettingsUiState(
    val username: String = "",
    val repoFullName: String = "",
    val isAssistantDefault: Boolean = false,
    val authType: String = "", // "pat", "oauth", or ""
    val isSigningOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val oAuthTokenExchanger: OAuthTokenExchanger,
    private val encryptedPrefs: SharedPreferences,
    private val submissionDao: SubmissionDao,
    private val pendingNoteDao: PendingNoteDao,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeAuth()
        checkAssistantRole()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            combine(
                authManager.username,
                authManager.repoOwner,
                authManager.repoName,
                authManager.authType
            ) { username, owner, name, authType ->
                data class AuthInfo(val username: String?, val owner: String?, val name: String?, val authType: String?)
                AuthInfo(username, owner, name, authType)
            }.collect { info ->
                _uiState.update {
                    it.copy(
                        username = info.username ?: "",
                        repoFullName = if (info.owner != null && info.name != null) "${info.owner}/${info.name}" else "",
                        authType = info.authType ?: ""
                    )
                }
            }
        }
    }

    fun checkAssistantRole() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        _uiState.update { it.copy(isAssistantDefault = isDefault) }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            revokeOAuthTokenIfNeeded()
            authManager.signOut()
            _uiState.update { it.copy(isSigningOut = false) }
            onComplete()
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            revokeOAuthTokenIfNeeded()
            workManager.cancelAllWork()
            pendingNoteDao.deleteAll()
            submissionDao.deleteAll()
            authManager.signOut()
            _uiState.update { it.copy(isSigningOut = false) }
            onComplete()
        }
    }

    private suspend fun revokeOAuthTokenIfNeeded() {
        val authType = authManager.authType.first() ?: return
        if (authType != "oauth") return
        val token = encryptedPrefs.getString("access_token", null) ?: return
        withTimeoutOrNull(5_000L) {
            oAuthTokenExchanger.revokeToken(token)
        }
    }
}
