package com.carsondavis.notetaker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.carsondavis.notetaker.ui.theme.Blue40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicBar(
    topic: String?,
    isLoading: Boolean,
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit = {}
) {
    val displayText = when {
        isLoading -> "..."
        topic.isNullOrBlank() -> "No topic set"
        else -> topic
    }
    val textColor = if (topic.isNullOrBlank() && !isLoading) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background)
        )
        TopAppBar(
            title = {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
            },
            actions = {
                IconButton(onClick = onBrowseClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Browse notes"
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Blue40
            )
        )
    }
}
