package com.carsondavis.notetaker.ui.viewmodels

import android.app.role.RoleManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val username: String = "",
    val repoFullName: String = "",
    val isAssistantDefault: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager
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
                authManager.repoName
            ) { username, owner, name ->
                Triple(username, owner, name)
            }.collect { (username, owner, name) ->
                _uiState.update {
                    it.copy(
                        username = username ?: "",
                        repoFullName = if (owner != null && name != null) "$owner/$name" else ""
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

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }
}
