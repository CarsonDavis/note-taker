package com.carsondavis.notetaker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.carsondavis.notetaker.ui.viewmodels.AuthViewModel

@Composable
fun AuthScreen(
    onAuthComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tokenVisible by remember { mutableStateOf(false) }
    var showPatDialog by remember { mutableStateOf(false) }
    var showTokenHelpDialog by remember { mutableStateOf(false) }
    var showRepoHelpDialog by remember { mutableStateOf(false) }
    var showOAuthHelpDialog by remember { mutableStateOf(false) }
    var showForkHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSetupComplete) {
        if (uiState.isSetupComplete) {
            onAuthComplete()
        }
    }

    // Reset OAuth spinner when user returns from browser without completing flow
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.cancelOAuthFlow()
    }

    // Fork help dialog
    if (showForkHelpDialog) {
        AlertDialog(
            onDismissRequest = { showForkHelpDialog = false },
            title = { Text("About the Notes Repo") },
            text = {
                Text(
                    "The template repo comes with a pre-configured folder structure and a Claude Code agent that processes your raw voice notes.\n\n" +
                            "The agent cleans up speech-to-text artifacts, sorts notes into topic folders (books, podcasts, personal, etc.), and maintains indexes \u2014 all as plain markdown in a repo you own.\n\n" +
                            "Forking gives you your own private copy to start capturing into."
                )
            },
            confirmButton = {
                TextButton(onClick = { showForkHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // PAT instructions dialog
    if (showPatDialog) {
        AlertDialog(
            onDismissRequest = { showPatDialog = false },
            title = { Text("Create a Personal Access Token") },
            text = {
                Text(
                    "On the next page:\n\n" +
                            "1. Give the token a name (e.g. \"GitJot\")\n" +
                            "2. Under Repository access, select \"Only select repositories\" and pick your notes repo\n" +
                            "3. Under Repository permissions, find Contents and select \"Read and write\"\n" +
                            "4. Click Generate token and copy it"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPatDialog = false
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/settings/personal-access-tokens/new")
                    )
                    context.startActivity(intent)
                }) {
                    Text("Open GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Token help dialog
    if (showTokenHelpDialog) {
        AlertDialog(
            onDismissRequest = { showTokenHelpDialog = false },
            title = { Text("About Your Token") },
            text = {
                Text(
                    "Your token is stored only on this device. It's sent directly to the GitHub API and nowhere else. You can revoke it anytime from GitHub Settings."
                )
            },
            confirmButton = {
                TextButton(onClick = { showTokenHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Repo help dialog
    if (showRepoHelpDialog) {
        AlertDialog(
            onDismissRequest = { showRepoHelpDialog = false },
            title = { Text("About the Repository") },
            text = {
                Text(
                    "This is the GitHub repository where your notes are stored as markdown files. You can name it anything. Enter as owner/repo or paste the full GitHub URL."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRepoHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // OAuth permissions help dialog
    if (showOAuthHelpDialog) {
        AlertDialog(
            onDismissRequest = { showOAuthHelpDialog = false },
            title = { Text("What am I agreeing to?") },
            text = {
                Text(
                    "You're installing the GitJot GitHub App on one repository you choose. This gives GitJot:\n\n" +
                            "\u2022 Read and write files in that one repo (to save your notes)\n\n" +
                            "That's it. GitJot cannot access your other repos, your profile, your email, or anything else.\n\n" +
                            "You can revoke access anytime from GitHub Settings \u2192 Applications \u2192 Installed GitHub Apps."
                )
            },
            confirmButton = {
                TextButton(onClick = { showOAuthHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "GitJot Setup",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your voice notes, saved to Git, organized by AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Card 1: Fork
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "1.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Your Notes Repo",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showForkHelpDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "About the notes repo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Fork the template repo to your GitHub account. This is where your notes will be stored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/CarsonDavis/gitjot-notes/fork")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fork on GitHub")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Connect
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "2.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connect Your Repo",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showOAuthHelpDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "What am I agreeing to?",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!uiState.showPatFlow) {
                        // OAuth content
                        Text(
                            text = "Sign in and select the repo you just forked. GitJot only gets access to that one repo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val uri = viewModel.startOAuthFlow()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            enabled = !uiState.isValidating && !uiState.isOAuthInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isOAuthInProgress || uiState.isValidating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing in...")
                            } else {
                                Text("Sign in with GitHub")
                            }
                        }

                        // Error display
                        if (uiState.error != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { viewModel.showPatFlow() },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text(
                                "Or connect with a Personal Access Token",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        // PAT content (replaces OAuth)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Repository",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { showRepoHelpDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Repository help",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.repo,
                            onValueChange = { viewModel.updateRepo(it) },
                            placeholder = { Text("owner/repo or GitHub URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Personal Access Token",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showPatDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Token on GitHub")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Paste Token",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { showTokenHelpDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Token help",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.token,
                            onValueChange = { viewModel.updateToken(it) },
                            placeholder = { Text("ghp_...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (tokenVisible) "Hide token" else "Show token"
                                    )
                                }
                            }
                        )

                        // Error display
                        if (uiState.error != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.submit() },
                            enabled = !uiState.isValidating && uiState.token.isNotBlank() && uiState.repo.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isValidating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Continue")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { viewModel.hidePatFlow() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                "Back to GitHub sign-in",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://youtu.be/sNow-kcrxRo")
                )
                context.startActivity(intent)
            }) {
                Text("Need help? Watch the setup walkthrough")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
