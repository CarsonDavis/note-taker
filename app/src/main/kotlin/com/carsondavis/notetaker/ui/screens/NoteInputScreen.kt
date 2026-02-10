package com.carsondavis.notetaker.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsondavis.notetaker.ui.components.SubmissionHistory
import com.carsondavis.notetaker.ui.components.TopicBar
import com.carsondavis.notetaker.ui.viewmodels.NoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NoteInputScreen(
    onSettingsClick: () -> Unit,
    viewModel: NoteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            delay(1500)
            viewModel.clearSubmitSuccess()
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
                onSettingsClick = onSettingsClick
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.noteText,
                    onValueChange = viewModel::updateNoteText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Type your note...") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = uiState.noteText.isNotBlank() && !uiState.isSubmitting && !uiState.submitSuccess,
                        colors = if (uiState.submitSuccess) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        AnimatedContent(
                            targetState = when {
                                uiState.submitSuccess -> "success"
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
                                    else -> {
                                        Text("Submit")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SubmissionHistory(items = uiState.submissions)
        }
    }
}
