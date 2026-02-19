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
import kotlinx.coroutines.flow.first
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
    val showPatFlow: Boolean = false,
    val showInstallHint: Boolean = false,
    val showRepoSelection: Boolean = false,
    val availableRepos: List<com.carsondavis.notetaker.data.api.InstallationRepo> = emptyList()
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

    /** Cached at init so startOAuthFlow() stays non-suspend */
    private var cachedInstallationId: String? = null

    /** If the install URL was tried but produced no callback (app already installed),
     *  the next tap falls back to the authorize URL. */
    private var triedInstallUrl = false

    /** Pending OAuth state held in memory while user picks a repo (not persisted). */
    private var pendingOAuthToken: String? = null
    private var pendingUsername: String? = null
    private var pendingInstallationId: Long? = null

    init {
        observeOAuthCallback()
        viewModelScope.launch {
            cachedInstallationId = authManager.installationId.first()
        }
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
        val challenge = OAuthConfig.generateCodeChallenge(verifier)
        val state = OAuthConfig.generateState()

        // Save to SavedStateHandle (survives process death)
        savedStateHandle["oauth_verifier"] = verifier
        savedStateHandle["oauth_state"] = state

        _uiState.update { it.copy(isOAuthInProgress = true, error = null, showInstallHint = false) }

        // Returning user (has installation_id or install URL already tried without callback)
        //   → authorize URL (re-authorizes without re-installing)
        // First-time user → install URL (chains to authorization automatically)
        return if (cachedInstallationId != null || triedInstallUrl) {
            Uri.parse(OAuthConfig.AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("client_id", OAuthConfig.CLIENT_ID)
                .appendQueryParameter("redirect_uri", OAuthConfig.REDIRECT_URI_HTTPS)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build()
        } else {
            triedInstallUrl = true
            Uri.parse(OAuthConfig.APP_INSTALL_URL)
        }
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

        // Validate state when present (authorize flow returns it).
        // Install flow omits state — PKCE still protects against code interception.
        if (state.isNotEmpty() && state != expectedState) {
            _uiState.update {
                it.copy(isOAuthInProgress = false, error = "OAuth state mismatch. Please try again.")
            }
            return
        }

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
                    // No installation found — either stale installation_id or
                    // authorize URL was tried without the app being installed.
                    // Reset to install URL for next attempt.
                    authManager.clearInstallationId()
                    cachedInstallationId = null
                    triedInstallUrl = false
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            isOAuthInProgress = false,
                            error = "GitJot isn't connected to this account. Tap \"Sign in with GitHub\" to set it up."
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
                            error = "No repositories are connected. Go back to Step 1 and fork the template repo, then tap \"Sign in with GitHub\" and select it during installation."
                        )
                    }
                    return@launch
                }

                if (repos.repositories.size > 1) {
                    // Multiple repos — let user choose
                    pendingOAuthToken = accessToken
                    pendingUsername = user.login
                    pendingInstallationId = installation.id
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            isOAuthInProgress = false,
                            showRepoSelection = true,
                            availableRepos = repos.repositories
                        )
                    }
                    return@launch
                }

                val repo = repos.repositories.first()

                // Save everything
                authManager.saveOAuthTokens(accessToken, user.login)
                authManager.saveRepo(repo.owner.login, repo.name)
                authManager.saveInstallationId(installation.id.toString())
                cachedInstallationId = installation.id.toString()

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
        if (_uiState.value.isValidating) return  // callback is being processed
        val wasInProgress = _uiState.value.isOAuthInProgress
        _uiState.update {
            it.copy(
                isOAuthInProgress = false,
                showInstallHint = wasInProgress && triedInstallUrl
            )
        }
    }

    fun selectRepo(owner: String, name: String) {
        val token = pendingOAuthToken ?: return
        val username = pendingUsername ?: return
        val installationId = pendingInstallationId ?: return

        viewModelScope.launch {
            authManager.saveOAuthTokens(token, username)
            authManager.saveRepo(owner, name)
            authManager.saveInstallationId(installationId.toString())
            cachedInstallationId = installationId.toString()

            pendingOAuthToken = null
            pendingUsername = null
            pendingInstallationId = null

            savedStateHandle.remove<String>("oauth_verifier")
            savedStateHandle.remove<String>("oauth_state")

            _uiState.update {
                it.copy(
                    showRepoSelection = false,
                    availableRepos = emptyList(),
                    isSetupComplete = true
                )
            }
        }
    }

    fun cancelRepoSelection() {
        pendingOAuthToken = null
        pendingUsername = null
        pendingInstallationId = null
        _uiState.update {
            it.copy(
                showRepoSelection = false,
                availableRepos = emptyList()
            )
        }
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
