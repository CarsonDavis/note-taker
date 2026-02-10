package com.carsondavis.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.api.GitHubRepo
import com.carsondavis.notetaker.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// TODO: Replace with your GitHub OAuth App Client ID
const val GITHUB_CLIENT_ID = "Ov23liLi2uorJRemy1Zb"

enum class AuthStep { WELCOME, DEVICE_CODE, SELECT_REPO }

data class AuthUiState(
    val step: AuthStep = AuthStep.WELCOME,
    val userCode: String = "",
    val verificationUri: String = "https://github.com/login/device",
    val error: String? = null,
    val username: String = "",
    val repos: List<GitHubRepo> = emptyList(),
    val selectedRepo: GitHubRepo? = null,
    val isLoadingRepos: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: GitHubApi,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    fun startDeviceFlow() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                val response = api.requestDeviceCode(
                    clientId = GITHUB_CLIENT_ID
                )
                _uiState.update {
                    it.copy(
                        step = AuthStep.DEVICE_CODE,
                        userCode = response.userCode,
                        verificationUri = response.verificationUri
                    )
                }
                startPolling(response.deviceCode, response.interval)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to start auth: ${e.message}")
                }
            }
        }
    }

    private fun startPolling(deviceCode: String, interval: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val delayMs = (interval * 1000L).coerceAtLeast(5000L)
            while (true) {
                delay(delayMs)
                try {
                    val response = api.pollAccessToken(
                        clientId = GITHUB_CLIENT_ID,
                        deviceCode = deviceCode
                    )
                    when {
                        response.accessToken != null -> {
                            val user = api.getUser("Bearer ${response.accessToken}")
                            authManager.saveAuth(response.accessToken, user.login)
                            _uiState.update {
                                it.copy(
                                    step = AuthStep.SELECT_REPO,
                                    username = user.login,
                                    isLoadingRepos = true
                                )
                            }
                            loadRepos(response.accessToken)
                            return@launch
                        }
                        response.error == "authorization_pending" -> {
                            // Keep polling
                        }
                        response.error == "slow_down" -> {
                            delay(5000)
                        }
                        response.error == "expired_token" -> {
                            _uiState.update {
                                it.copy(
                                    step = AuthStep.WELCOME,
                                    error = "Code expired. Please try again."
                                )
                            }
                            return@launch
                        }
                        else -> {
                            _uiState.update {
                                it.copy(error = response.errorDescription ?: response.error)
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(error = "Polling error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun loadRepos(token: String) {
        try {
            val repos = api.getUserRepos(auth = "Bearer $token")
            _uiState.update {
                it.copy(repos = repos, isLoadingRepos = false)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoadingRepos = false,
                    error = "Failed to load repos: ${e.message}"
                )
            }
        }
    }

    fun selectRepo(repo: GitHubRepo) {
        _uiState.update { it.copy(selectedRepo = repo) }
    }

    fun confirmRepo() {
        val repo = _uiState.value.selectedRepo ?: return
        viewModelScope.launch {
            val parts = repo.fullName.split("/")
            authManager.saveRepo(parts[0], parts[1])
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
