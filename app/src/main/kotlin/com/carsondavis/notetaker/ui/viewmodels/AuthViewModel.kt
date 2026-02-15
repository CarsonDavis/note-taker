package com.carsondavis.notetaker.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.api.GitHubInstallationApi
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.auth.OAuthCallbackHolder
import com.carsondavis.notetaker.data.auth.OAuthConfig
import com.carsondavis.notetaker.data.auth.OAuthTokenExchanger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class AuthUiState(
    val token: String = "",
    val repo: String = "",
    val isValidating: Boolean = false,
    val isSetupComplete: Boolean = false,
    val error: String? = null,
    val isOAuthInProgress: Boolean = false,
    val showPatFlow: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: GitHubApi,
    private val installationApi: GitHubInstallationApi,
    private val authManager: AuthManager,
    private val callbackHolder: OAuthCallbackHolder,
    private val tokenExchanger: OAuthTokenExchanger,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeOAuthCallback()
    }

    private fun observeOAuthCallback() {
        viewModelScope.launch {
            callbackHolder.callback.filterNotNull().collect { data ->
                handleOAuthCallback(data.code, data.state)
                callbackHolder.clear()
            }
        }
    }

    // --- OAuth flow ---

    fun startOAuthFlow(): Uri {
        val verifier = OAuthConfig.generateCodeVerifier()
        val state = OAuthConfig.generateState()

        // Save to SavedStateHandle (survives process death)
        savedStateHandle["oauth_verifier"] = verifier
        savedStateHandle["oauth_state"] = state

        _uiState.update { it.copy(isOAuthInProgress = true, error = null) }

        // The install URL chains to OAuth authorization automatically when
        // "Request user authorization during installation" is enabled on the GitHub App.
        return Uri.parse(OAuthConfig.APP_INSTALL_URL)
    }

    private fun handleOAuthCallback(code: String, state: String) {
        val expectedState = savedStateHandle.get<String>("oauth_state")
        val verifier = savedStateHandle.get<String>("oauth_verifier")

        if (expectedState == null || verifier == null) {
            _uiState.update {
                it.copy(isOAuthInProgress = false, error = "OAuth session expired. Please try again.")
            }
            return
        }

        // Note: GitHub App installation flow doesn't return state parameter,
        // so we skip state validation for the install flow. PKCE still protects
        // against code interception.

        _uiState.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            try {
                // Exchange code for token
                val tokenResponse = tokenExchanger.exchangeCode(code, verifier)
                val accessToken = tokenResponse.accessToken

                // Get username
                val user = api.getUser("Bearer $accessToken")

                // Discover installation and repo
                val installations = installationApi.getInstallations("Bearer $accessToken")
                if (installations.installations.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            isOAuthInProgress = false,
                            error = "No GitHub App installation found. Please install GitJot on a repository."
                        )
                    }
                    return@launch
                }

                val installation = installations.installations.first()
                val repos = installationApi.getInstallationRepos(
                    "Bearer $accessToken",
                    installation.id
                )

                if (repos.repositories.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            isOAuthInProgress = false,
                            error = "No repositories found. Please select a repository when installing GitJot."
                        )
                    }
                    return@launch
                }

                val repo = repos.repositories.first()

                // Save everything
                authManager.saveOAuthTokens(accessToken, user.login)
                authManager.saveRepo(repo.owner.login, repo.name)
                authManager.saveInstallationId(installation.id.toString())

                // Clear saved OAuth state
                savedStateHandle.remove<String>("oauth_verifier")
                savedStateHandle.remove<String>("oauth_state")

                _uiState.update {
                    it.copy(
                        isValidating = false,
                        isOAuthInProgress = false,
                        isSetupComplete = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        isOAuthInProgress = false,
                        error = "Sign-in failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelOAuthFlow() {
        _uiState.update { it.copy(isOAuthInProgress = false) }
    }

    // --- PAT fallback flow ---

    fun showPatFlow() {
        _uiState.update { it.copy(showPatFlow = true, error = null) }
    }

    fun hidePatFlow() {
        _uiState.update { it.copy(showPatFlow = false, error = null) }
    }

    fun updateToken(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun updateRepo(repo: String) {
        _uiState.update { it.copy(repo = repo, error = null) }
    }

    fun parseRepo(input: String): Pair<String, String>? {
        val trimmed = input.trim()
            .removeSuffix("/")
            .removeSuffix(".git")
            .removeSuffix("/")

        val urlRegex = Regex("""(?:https?://)?github\.com/([^/]+)/([^/]+)""")
        urlRegex.find(trimmed)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            if (owner.isNotBlank() && repo.isNotBlank()) return owner to repo
        }

        val parts = trimmed.split("/")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return parts[0] to parts[1]
        }

        return null
    }

    fun submit() {
        val token = _uiState.value.token.trim()
        val repoInput = _uiState.value.repo.trim()

        if (token.isBlank()) {
            _uiState.update { it.copy(error = "Token is required") }
            return
        }

        if (repoInput.isBlank()) {
            _uiState.update { it.copy(error = "Repository is required") }
            return
        }

        val parsed = parseRepo(repoInput)
        if (parsed == null) {
            _uiState.update { it.copy(error = "Enter as owner/repo or paste the full GitHub URL") }
            return
        }

        val (owner, repo) = parsed
        _uiState.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            try {
                val user = try {
                    api.getUser("Bearer $token")
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        _uiState.update {
                            it.copy(isValidating = false, error = "Personal access token is invalid")
                        }
                        return@launch
                    }
                    throw e
                }

                try {
                    api.getRepository("Bearer $token", owner, repo)
                } catch (e: HttpException) {
                    if (e.code() == 404) {
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = "Repository not found — check the name and token permissions"
                            )
                        }
                        return@launch
                    }
                    throw e
                }

                authManager.saveAuth(token, user.login)
                authManager.saveRepo(owner, repo)
                _uiState.update { it.copy(isValidating = false, isSetupComplete = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "Network error: ${e.message}"
                    )
                }
            }
        }
    }
}
