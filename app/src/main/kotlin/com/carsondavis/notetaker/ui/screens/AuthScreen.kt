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
import com.carsondavis.notetaker.ui.viewmodels.AuthViewModel

@Composable
private fun StepHeader(stepNumber: Int, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text(
            text = "$stepNumber",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

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

    LaunchedEffect(uiState.isSetupComplete) {
        if (uiState.isSetupComplete) {
            onAuthComplete()
        }
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
                text = "Your voice notes are saved as markdown files in a GitHub repository you own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Step 1: Fork the repo
                    StepHeader(1, "Fork the Notes Repo")
                    OutlinedButton(
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

                    Spacer(modifier = Modifier.height(24.dp))

                    // Step 2: Repo field
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepHeader(2, "Enter Your Repository")
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
                    OutlinedTextField(
                        value = uiState.repo,
                        onValueChange = { viewModel.updateRepo(it) },
                        placeholder = { Text("owner/repo or GitHub URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Step 3: Generate PAT
                    StepHeader(3, "Generate a Personal Access Token")
                    OutlinedButton(
                        onClick = { showPatDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate Token on GitHub")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Step 4: Token field
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepHeader(4, "Paste Your Token")
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

                    Spacer(modifier = Modifier.height(24.dp))

                    // Continue button
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

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
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
