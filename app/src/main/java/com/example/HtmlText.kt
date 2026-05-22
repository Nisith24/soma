package com.example

import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

private fun Int.toHexColor() = String.format("#%06X", 0xFFFFFF and this)

private fun formatExplanationHtml(
    html: String,
    primaryHex: String,
    secondaryHex: String,
    tertiaryHex: String,
    errorHex: String
): String {
    var result = html

    if (!result.contains("<br", ignoreCase = true) && !result.contains("<p", ignoreCase = true)) {
         result = result.replace("\n", "<br>")
    }

    val replacements = mapOf(
        "Solution:" to primaryHex,
        "Highyeild:" to secondaryHex,
        "Highyield:" to secondaryHex,
        "Random:" to tertiaryHex,
        "Extraedge:" to errorHex,
        "Explanation for Incorrect Options:-" to errorHex,
        "Explanation for Incorrect Options:" to errorHex,
        "Explanation:" to primaryHex
    )

    for ((keyword, color) in replacements) {
        val regex = Regex("(?i)(?:<br\\s*/?>|\\s)*($keyword)")
        // Add double breaks before, style it, and a single break after
        result = result.replace(regex, "<br><br><b><font color='$color'>$1</font></b><br>")
    }

    val optionRegex = Regex("(?i)(?:<br\\s*/?>|\\s)*(Option:\\s*[A-E]\\.?)")
    result = result.replace(optionRegex, "<br><br><b>$1</b> ")

    // Clean up leading breaks
    result = result.replace(Regex("^(?:<br\\s*/?>|\\s)*"), "")

    // Ensure HTML has paragraph spacing visually instead of single lines where possible
    result = result.replace("<br><br><br>", "<br><br>")

    return result
}

@Composable
fun HtmlText(html: String?, modifier: Modifier = Modifier) {
    if (html.isNullOrBlank()) return

    val actualHtml = if (html.startsWith("file://")) {
        try {
            val path = html.substring("file://".length)
            val file = java.io.File(path)
            if (file.exists()) {
                file.readText(Charsets.UTF_8)
        } else {
                html
            }
        } catch (e: Exception) {
            html
        }
    } else {
        html
    }

    if (actualHtml.isBlank()) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    
    val primaryHex = MaterialTheme.colorScheme.primary.toArgb().toHexColor()
    val secondaryHex = MaterialTheme.colorScheme.secondary.toArgb().toHexColor()
    val tertiaryHex = MaterialTheme.colorScheme.tertiary.toArgb().toHexColor()
    val errorHex = MaterialTheme.colorScheme.error.toArgb().toHexColor()

    val formattedHtml = formatExplanationHtml(
        html = actualHtml,
        primaryHex = primaryHex,
        secondaryHex = secondaryHex,
        tertiaryHex = tertiaryHex,
        errorHex = errorHex
    )

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 16f
                setLineSpacing(0f, 1.4f) // Improved line spacing for readability
                linksClickable = true
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                
                // Ensure proper layout matching
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.text = HtmlCompat.fromHtml(formattedHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}

