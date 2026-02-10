package com.carsondavis.notetaker.ui.components

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markwon = remember(context) { Markwon.create(context) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 15f
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier
    )
}
