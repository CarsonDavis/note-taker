package com.carsondavis.notetaker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.carsondavis.notetaker.speech.ListeningState
import com.carsondavis.notetaker.ui.components.SubmissionHistory
import com.carsondavis.notetaker.ui.components.TopicBar
import com.carsondavis.notetaker.ui.viewmodels.InputMode
import com.carsondavis.notetaker.ui.viewmodels.NoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NoteInputScreen(
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit = {},
    viewModel: NoteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    // Check/request permission on first composition
    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (already) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-start voice on resume, stop on pause
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (uiState.inputMode == InputMode.VOICE && uiState.permissionGranted && uiState.speechAvailable) {
            viewModel.startVoiceInput()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.stopVoiceInput()
    }

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            delay(1500)
            viewModel.clearSubmitSuccess()
        }
    }

    LaunchedEffect(uiState.submitQueued) {
        if (uiState.submitQueued) {
            delay(1500)
            viewModel.clearSubmitQueued()
        }
    }

    LaunchedEffect(uiState.submitError) {
        uiState.submitError?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TopicBar(
                topic = uiState.topic,
                isLoading = uiState.isTopicLoading,
                onSettingsClick = onSettingsClick,
                onBrowseClick = onBrowseClick
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                // Listening indicator
                if (uiState.inputMode == InputMode.VOICE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.listeningState == ListeningState.LISTENING)
                                Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (uiState.listeningState == ListeningState.LISTENING)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (uiState.listeningState) {
                                ListeningState.LISTENING -> "Listening..."
                                ListeningState.RESTARTING -> "Listening..."
                                ListeningState.IDLE -> "Mic idle"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.listeningState == ListeningState.LISTENING)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.noteText,
                    onValueChange = viewModel::updateNoteText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && uiState.inputMode == InputMode.VOICE) {
                                viewModel.switchToKeyboard()
                            }
                        },
                    placeholder = {
                        Text(
                            if (uiState.inputMode == InputMode.VOICE) "Listening..."
                            else "Type your note..."
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    readOnly = uiState.inputMode == InputMode.VOICE
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { viewModel.submit() },
                                enabled = uiState.noteText.isNotBlank() && !uiState.isSubmitting
                                        && !uiState.submitSuccess && !uiState.submitQueued,
                                colors = when {
                                    uiState.submitSuccess -> ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                    uiState.submitQueued -> ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                    else -> ButtonDefaults.buttonColors()
                                }
                            ) {
                                AnimatedContent(
                                    targetState = when {
                                        uiState.submitSuccess -> "success"
                                        uiState.submitQueued -> "queued"
                                        uiState.isSubmitting -> "submitting"
                                        else -> "idle"
                                    },
                                    transitionSpec = {
                                        (fadeIn() + scaleIn(initialScale = 0.8f))
                                            .togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                                    },
                                    label = "submitButton"
                                ) { state ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (state) {
                                            "submitting" -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Saving")
                                            }
                                            "success" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Sent!")
                                            }
                                            "queued" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Queued")
                                            }
                                            else -> {
                                                Text("Submit")
                                            }
                                        }
                                    }
                                }
                            }

                            // Mic button — show in keyboard mode when permission granted
                            if (uiState.inputMode == InputMode.KEYBOARD
                                && uiState.permissionGranted
                                && uiState.speechAvailable
                            ) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.startVoiceInput()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Switch to voice input",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (uiState.pendingCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${uiState.pendingCount} note${if (uiState.pendingCount != 1) "s" else ""} queued",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SubmissionHistory(items = uiState.submissions)
        }
    }
}
