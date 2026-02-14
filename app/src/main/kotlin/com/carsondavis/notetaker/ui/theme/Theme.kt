package com.carsondavis.notetaker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    secondary = Blue80,
    tertiary = Green80,
    background = DarkPurple10,
    surface = DarkPurple15,
    surfaceVariant = DarkPurple30,
    surfaceDim = DarkPurple10,
    surfaceContainer = DarkPurple20,
    surfaceContainerLow = DarkPurple15,
    surfaceContainerHigh = DarkPurple25,
    surfaceContainerHighest = DarkPurple30
)

@Composable
fun NoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
