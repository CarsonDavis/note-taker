package com.carsondavis.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class AuthUiState(
    val token: String = "",
    val repo: String = "",
    val isValidating: Boolean = false,
    val isSetupComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: GitHubApi,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateToken(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun updateRepo(repo: String) {
        _uiState.update { it.copy(repo = repo, error = null) }
    }

    /**
     * Parses owner/repo from various input formats:
     * - "owner/repo"
     * - "https://github.com/owner/repo"
     * - "github.com/owner/repo"
     * Strips trailing "/" and ".git"
     */
    fun parseRepo(input: String): Pair<String, String>? {
        val trimmed = input.trim()
            .removeSuffix("/")
            .removeSuffix(".git")
            .removeSuffix("/")

        // Try URL patterns: https://github.com/owner/repo or github.com/owner/repo
        val urlRegex = Regex("""(?:https?://)?github\.com/([^/]+)/([^/]+)""")
        urlRegex.find(trimmed)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            if (owner.isNotBlank() && repo.isNotBlank()) return owner to repo
        }

        // Try direct owner/repo format
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
                // Step 1: Validate token
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

                // Step 2: Validate repository
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

                // Both valid — save and proceed
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
