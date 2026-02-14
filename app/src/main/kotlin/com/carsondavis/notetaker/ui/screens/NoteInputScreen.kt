package com.carsondavis.notetaker.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    scrollbarAlpha: Animatable<Float, *>
): Modifier = drawWithContent {
    drawContent()
    val viewportHeight = size.height
    val contentHeight = scrollState.maxValue + viewportHeight
    if (contentHeight > viewportHeight && scrollbarAlpha.value > 0f) {
        val scrollbarHeight = (viewportHeight / contentHeight) * viewportHeight
        val scrollbarY = (scrollState.value.toFloat() / scrollState.maxValue) * (viewportHeight - scrollbarHeight)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.4f * scrollbarAlpha.value),
            topLeft = Offset(size.width - 8.dp.toPx(), scrollbarY),
            size = Size(6.dp.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
    }
}

@Composable
fun NoteInputScreen(
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit = {},
    viewModel: NoteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // TextFieldState for the new BasicTextField API — gives us access to ScrollState
    val textFieldState = rememberTextFieldState()
    val textFieldScrollState = rememberScrollState()
    val scrollbarAlpha = remember { Animatable(0f) }

    // Sync ViewModel → TextFieldState (speech input, clear after submit)
    LaunchedEffect(uiState.noteText) {
        if (textFieldState.text.toString() != uiState.noteText) {
            textFieldState.setTextAndPlaceCursorAtEnd(uiState.noteText)
        }
    }

    // Sync TextFieldState → ViewModel (user typing)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { text ->
                viewModel.updateNoteText(text)
            }
    }

    // Animate scrollbar visibility
    LaunchedEffect(textFieldScrollState.value) {
        if (textFieldScrollState.maxValue > 0) {
            scrollbarAlpha.snapTo(1f)
            delay(1000)
            scrollbarAlpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    // Onboarding dialog
    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOnboarding() },
            title = { Text("Instant Note Capture") },
            text = {
                Text(
                    "You can launch GitJot by long-pressing your phone's side button.\n\n" +
                            "To enable this, set GitJot as your default digital assistant. " +
                            "This replaces Google Assistant for the long-press shortcut."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissOnboarding()
                    val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Set Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOnboarding() }) {
                    Text("Maybe Later")
                }
            }
        )
    }

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
        topBar = {
            TopicBar(
                topic = uiState.topic,
                isLoading = uiState.isTopicLoading,
                onSettingsClick = onSettingsClick,
                onBrowseClick = onBrowseClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Text field area — grows to fill available space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                val isListening = uiState.inputMode == InputMode.VOICE
                        && uiState.listeningState == ListeningState.LISTENING
                val interactionSource = remember { MutableInteractionSource() }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScrollbar(textFieldScrollState, scrollbarAlpha)
                    ) {
                        OutlinedTextField(
                            state = textFieldState,
                            modifier = Modifier
                                .fillMaxSize()
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
                            lineLimits = TextFieldLineLimits.MultiLine(),
                            scrollState = textFieldScrollState,
                            interactionSource = interactionSource,
                            colors = if (isListening) OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.primary
                            ) else OutlinedTextFieldDefaults.colors()
                        )
                    }

                    // Listening indicator
                    if (uiState.inputMode == InputMode.VOICE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.listeningState == ListeningState.LISTENING)
                                    Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (uiState.listeningState == ListeningState.LISTENING)
                                    MaterialTheme.colorScheme.primary
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
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit button + mic
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = uiState.noteText.isNotBlank() && !uiState.isSubmitting
                                && !uiState.submitSuccess && !uiState.submitQueued,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(36.dp),
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
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Saving", style = MaterialTheme.typography.titleLarge)
                                    }
                                    "success" -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sent!", style = MaterialTheme.typography.titleLarge)
                                    }
                                    "queued" -> {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Queued", style = MaterialTheme.typography.titleLarge)
                                    }
                                    else -> {
                                        Text("Submit", style = MaterialTheme.typography.titleLarge)
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
                            },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Switch to voice input",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                if (uiState.pendingCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.pendingCount} note${if (uiState.pendingCount != 1) "s" else ""} queued",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SubmissionHistory(items = uiState.submissions)
        }
    }
}
