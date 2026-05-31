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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.carsondavis.notetaker.ui.theme.Blue40

/**
 * Top bar for the note input screen: Browse + Settings navigation icons only.
 *
 * Previously displayed the repo's sticky topic (`.current_topic`). That was
 * removed in M44 — notes now carry their own context for the processing agent,
 * so a single global topic is no longer meaningful.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTopBar(
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit = {}
) {
    Column {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background)
        )
        TopAppBar(
            title = {},
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
