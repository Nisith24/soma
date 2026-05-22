package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Structured blocks representing parsed elements of the medical explanation
sealed class HtmlBlock {
    data class Paragraph(val text: String) : HtmlBlock()
    data class Header(val level: Int, val text: String) : HtmlBlock()
    data class BulletList(val items: List<String>, val isOrdered: Boolean = false) : HtmlBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : HtmlBlock()
}

data class OptionBreakdown(
    val optionName: String,
    val content: String
)

@Composable
fun HtmlText(html: String?, modifier: Modifier = Modifier) {
    if (html.isNullOrBlank()) return

    val actualHtml = remember(html) {
        if (html.startsWith("file://")) {
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
    }

    if (actualHtml.isBlank()) return

    // Clean and split the medical rationale into concept-summary and individual option analyses
    val (conceptSummary, optionBreakdowns) = remember(actualHtml) {
        parseMedicalExplanation(actualHtml)
    }

    // Convert the html/text of the concept summary into structured presentation blocks
    val summaryBlocks = remember(conceptSummary) {
        parseHtmlToBlocks(conceptSummary)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Concept Summary Section (if present)
        if (summaryBlocks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "KEY MEDICAL CONCEPT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                
                HtmlBlockGroupRenderer(summaryBlocks)
            }
        }

        // 2. Option Breakdown Section
        if (optionBreakdowns.isNotEmpty()) {
            Text(
                text = "OPTION ANALYSIS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 12.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                optionBreakdowns.forEach { breakdown ->
                    val breakdownBlocks = remember(breakdown.content) {
                        parseHtmlToBlocks(breakdown.content)
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = breakdown.optionName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        HtmlBlockGroupRenderer(breakdownBlocks)
                    }
                }
            }
        }
        
        // 3. High-yield Callout Box / Tip
        val highYieldTip = remember(conceptSummary) {
            extractHighYieldTip(conceptSummary)
        }
        if (highYieldTip != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "HIGH-YIELD CLINICAL FACT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = highYieldTip,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun HtmlBlockGroupRenderer(blocks: List<HtmlBlock>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        blocks.forEach { block ->
            when (block) {
                is HtmlBlock.Header -> {
                    val annotatedHeader = buildMedicalAnnotatedString(block.text)
                    Column(
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = annotatedHeader,
                            style = when (block.level) {
                                1, 2 -> MaterialTheme.typography.titleLarge
                                3 -> MaterialTheme.typography.titleMedium
                                else -> MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                is HtmlBlock.Paragraph -> {
                    val annotated = buildMedicalAnnotatedString(block.text)
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is HtmlBlock.BulletList -> {
                    HtmlListRenderer(block)
                }
                is HtmlBlock.Table -> {
                    HtmlTableRenderer(block)
                }
            }
        }
    }
}

@Composable
fun HtmlListRenderer(list: HtmlBlock.BulletList) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
    ) {
        list.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (list.isOrdered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp)
                )
                val annotatedItem = buildMedicalAnnotatedString(item)
                Text(
                    text = annotatedItem,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HtmlTableRenderer(table: HtmlBlock.Table) {
    if (table.headers.isEmpty() && table.rows.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Horizontally scrollable table container - protects small/narrow displays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Headers Row
                if (table.headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        table.headers.forEach { header ->
                            Box(
                                modifier = Modifier.widthIn(min = 100.dp, max = 180.dp)
                            ) {
                                val annotatedHeader = buildMedicalAnnotatedString(header)
                                Text(
                                    text = annotatedHeader,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Data Rows
                table.rows.forEachIndexed { index, rowCells ->
                    val rowBg = if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                    Row(
                        modifier = Modifier
                            .background(
                                color = rowBg,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowCells.forEach { cellText ->
                            Box(
                                modifier = Modifier.widthIn(min = 100.dp, max = 180.dp)
                            ) {
                                // Cell content might contain tiny inline tags
                                val annotatedVal = buildMedicalAnnotatedString(cellText)
                                Text(
                                    text = annotatedVal,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun cleanParagraphText(text: String): String {
    // Keep inline styling tags (e.g., <b> or <i>) because buildMedicalAnnotatedString handles them.
    // Strip loose block containers or structural tags to avoid pollution.
    return text
        .replace(Regex("(?i)</?p>"), "")
        .replace(Regex("(?i)</?div>"), "")
        .replace(Regex("(?i)</?table>"), "")
        .replace(Regex("(?i)</?tr>"), "")
        .replace(Regex("(?i)</?td>"), "")
        .replace(Regex("(?i)</?th>"), "")
        .replace(Regex("(?i)</?ul>"), "")
        .replace(Regex("(?i)</?ol>"), "")
        .replace(Regex("(?i)</?li>"), "")
        .trim()
}

// Scans text block for block level HTML structures and tokenizes them
fun parseHtmlToBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var currentIndex = 0
    val length = html.length
    
    val tagKeywords = listOf("<table", "<h1", "<h2", "<h3", "<h4", "<h5", "<h6", "<ul", "<ol")
    
    while (currentIndex < length) {
        // Find earliest block tag
        var earliestTagIdx = -1
        var selectedKeyword = ""
        
        for (kw in tagKeywords) {
            val idx = html.indexOf(kw, currentIndex, ignoreCase = true)
            if (idx != -1) {
                if (earliestTagIdx == -1 || idx < earliestTagIdx) {
                    earliestTagIdx = idx
                    selectedKeyword = kw
                }
            }
        }
        
        if (earliestTagIdx == -1) {
            // No more block level tags. Append everything remaining as a text paragraph
            val remaining = html.substring(currentIndex).trim()
            if (remaining.isNotEmpty()) {
                blocks.add(HtmlBlock.Paragraph(remaining))
            }
            break
        }
        
        // Add preceding text as a paragraph blocks
        if (earliestTagIdx > currentIndex) {
            val preceding = html.substring(currentIndex, earliestTagIdx).trim()
            if (preceding.isNotEmpty()) {
                blocks.add(HtmlBlock.Paragraph(preceding))
            }
        }
        
        // Handle the matched block tag
        val tagEndIdx = html.indexOf('>', earliestTagIdx)
        if (tagEndIdx == -1) {
            val remaining = html.substring(earliestTagIdx).trim()
            if (remaining.isNotEmpty()) {
                blocks.add(HtmlBlock.Paragraph(remaining))
            }
            break
        }
        
        // Match tag elements to closing bounds
        val closingTag = when (selectedKeyword) {
            "<table" -> "</table>"
            "<h1" -> "</h1>"
            "<h2" -> "</h2>"
            "<h3" -> "</h3>"
            "<h4" -> "</h4>"
            "<h5" -> "</h5>"
            "<h6" -> "</h6>"
            "<ul" -> "</ul>"
            "<ol" -> "</ol>"
            else -> ""
        }
        
        val closingIdx = html.indexOf(closingTag, tagEndIdx + 1, ignoreCase = true)
        if (closingIdx == -1) {
            // No closing tag is found - skip parsing and proceed
            currentIndex = tagEndIdx + 1
            continue
        }
        
        val contentInTag = html.substring(tagEndIdx + 1, closingIdx)
        
        when (selectedKeyword) {
            "<table" -> {
                val tableBlock = parseTable(contentInTag)
                if (tableBlock != null) {
                    blocks.add(tableBlock)
                } else {
                    blocks.add(HtmlBlock.Paragraph(contentInTag))
                }
            }
            "<ul" -> {
                val list = parseList(contentInTag, isOrdered = false)
                if (list != null) {
                    blocks.add(list)
                }
            }
            "<ol" -> {
                val list = parseList(contentInTag, isOrdered = true)
                if (list != null) {
                    blocks.add(list)
                }
            }
            else -> {
                val level = selectedKeyword[2].toString().toIntOrNull() ?: 4
                blocks.add(HtmlBlock.Header(level, contentInTag))
            }
        }
        
        currentIndex = closingIdx + closingTag.length
    }
    
    // Clean and validate tokens
    return blocks.map { block ->
        when (block) {
            is HtmlBlock.Paragraph -> {
                HtmlBlock.Paragraph(cleanParagraphText(block.text))
            }
            is HtmlBlock.Header -> {
                HtmlBlock.Header(block.level, cleanParagraphText(block.text))
            }
            else -> block
        }
    }.filter {
        when (it) {
            is HtmlBlock.Paragraph -> it.text.isNotBlank()
            is HtmlBlock.Header -> it.text.isNotBlank()
            else -> true
        }
    }
}

fun parseTable(tableHtml: String): HtmlBlock.Table? {
    val trRegex = Regex("(?i)<tr>(.*?)</tr>")
    val thRegex = Regex("(?i)<th>(.*?)</th>")
    val tdRegex = Regex("(?i)<td>(.*?)</td>")
    
    val rows = mutableListOf<List<String>>()
    val headers = mutableListOf<String>()
    
    val rowMatches = trRegex.findAll(tableHtml)
    for (rowMatch in rowMatches) {
        val rowContent = rowMatch.groups[1]?.value ?: ""
        
        // Identify Table Header elements
        val thMatches = thRegex.findAll(rowContent).map { it.groups[1]?.value ?: "" }.toList()
        if (thMatches.isNotEmpty()) {
            headers.addAll(thMatches)
        } else {
            val tdMatches = tdRegex.findAll(rowContent).map { it.groups[1]?.value ?: "" }.toList()
            if (tdMatches.isNotEmpty()) {
                rows.add(tdMatches)
            }
        }
    }
    
    if (headers.isEmpty() && rows.isEmpty()) return null
    return HtmlBlock.Table(headers, rows)
}

fun parseList(listContent: String, isOrdered: Boolean): HtmlBlock.BulletList? {
    val liRegex = Regex("(?i)<li>(.*?)</li>")
    val items = liRegex.findAll(listContent).map { it.groups[1]?.value ?: "" }.filter { it.isNotBlank() }.toList()
    if (items.isEmpty()) return null
    return HtmlBlock.BulletList(items, isOrdered)
}

fun parseMedicalExplanation(html: String?): Pair<String, List<OptionBreakdown>> {
    if (html.isNullOrBlank()) return Pair("", emptyList())

    // Standardize newline formatting
    val standardizedHtml = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .trim()

    // Detect option markers inside the text (e.g. Option A, Option 1, Option A., etc.)
    val optionRegex = Regex("""(?i)\bOption(?:\s+class=[^>]*)?\s+([A-D]|[1-4]|\([a-d]\))\b[:\-\s.]*""")
    val matches = optionRegex.findAll(standardizedHtml).toList()

    if (matches.isEmpty()) {
        return Pair(standardizedHtml, emptyList())
    }

    val conceptSummary = standardizedHtml.substring(0, matches.first().range.first).trim()

    val optionBreakdowns = mutableListOf<OptionBreakdown>()
    for (i in matches.indices) {
        val currentMatch = matches[i]
        val optionLabel = currentMatch.value.trim().removeSuffix(":").removeSuffix("-").removeSuffix(".").trim()
            .replace(Regex("\\s+"), " ")

        val startIdx = currentMatch.range.last + 1
        val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else standardizedHtml.length

        if (startIdx < endIdx) {
            val optionText = standardizedHtml.substring(startIdx, endIdx).trim()
            if (optionText.isNotBlank()) {
                optionBreakdowns.add(OptionBreakdown(optionLabel, optionText))
            }
        }
    }

    if (conceptSummary.isEmpty() && optionBreakdowns.isEmpty()) {
        return Pair(standardizedHtml, emptyList())
    }

    return Pair(conceptSummary, optionBreakdowns)
}

@Composable
fun buildMedicalAnnotatedString(rawText: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface

    return remember(rawText, primaryColor, secondaryColor, onSurface) {
        val builder = AnnotatedString.Builder()
        
        // Decode HTML entities safely to prevent formatting errors
        val decodedText = rawText
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("(?i)<p>"), "")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")

        // Track and map styled segments derived from inline tags
        data class StyledSpan(val start: Int, val end: Int, val style: SpanStyle)
        val styleSpans = mutableListOf<StyledSpan>()

        val textBuilder = StringBuilder()
        val tagRegex = Regex("""</?([a-zA-Z1-6]+)(?:\s+[^>]*)?>""")
        val matches = tagRegex.findAll(decodedText).toList()
        
        var lastIdx = 0
        val boldStack = mutableListOf<Int>()
        val italicStack = mutableListOf<Int>()
        val underlineStack = mutableListOf<Int>()

        for (match in matches) {
            // Append preceding plain text
            if (match.range.first > lastIdx) {
                textBuilder.append(decodedText.substring(lastIdx, match.range.first))
            }
            
            val fullTag = match.value
            val tagName = match.groupValues[1].lowercase()
            val isClosing = fullTag.startsWith("</")
            val currentPos = textBuilder.length
            
            when (tagName) {
                "b", "strong" -> {
                    if (isClosing) {
                        if (boldStack.isNotEmpty()) {
                            val start = boldStack.removeAt(boldStack.size - 1)
                            styleSpans.add(StyledSpan(start, currentPos, SpanStyle(fontWeight = FontWeight.Bold)))
                        }
                    } else {
                        boldStack.add(currentPos)
                    }
                }
                "i", "em" -> {
                    if (isClosing) {
                        if (italicStack.isNotEmpty()) {
                            val start = italicStack.removeAt(italicStack.size - 1)
                            styleSpans.add(StyledSpan(start, currentPos, SpanStyle(fontStyle = FontStyle.Italic)))
                        }
                    } else {
                        italicStack.add(currentPos)
                    }
                }
                "u" -> {
                    if (isClosing) {
                        if (underlineStack.isNotEmpty()) {
                            val start = underlineStack.removeAt(underlineStack.size - 1)
                            styleSpans.add(StyledSpan(start, currentPos, SpanStyle(textDecoration = TextDecoration.Underline)))
                        }
                    } else {
                        underlineStack.add(currentPos)
                    }
                }
            }
            lastIdx = match.range.last + 1
        }
        
        if (lastIdx < decodedText.length) {
            textBuilder.append(decodedText.substring(lastIdx))
        }

        // Catch and terminate any unclosed styles cleanly at terminal character
        val finalPos = textBuilder.length
        boldStack.forEach { start ->
            styleSpans.add(StyledSpan(start, finalPos, SpanStyle(fontWeight = FontWeight.Bold)))
        }
        italicStack.forEach { start ->
            styleSpans.add(StyledSpan(start, finalPos, SpanStyle(fontStyle = FontStyle.Italic)))
        }
        underlineStack.forEach { start ->
            styleSpans.add(StyledSpan(start, finalPos, SpanStyle(textDecoration = TextDecoration.Underline)))
        }

        val plainText = textBuilder.toString()
        builder.append(plainText)

        // Apply HTML styled spans
        styleSpans.forEach { span ->
            if (span.start < finalPos && span.end <= finalPos && span.start < span.end) {
                builder.addStyle(span.style, span.start, span.end)
            }
        }

        // Apply high-yield color indicators for top-tier clinical keywords
        val highYieldPhrases = listOf(
            "drug of choice", "drugs of choice", "treatment of choice", "investigation of choice",
            "gold-standard", "gold standard", "most common cause", "most common source", 
            "most common site", "first line", "first-line", "pathognomonic", "clinical triad", 
            "diagnostic criteria", "diagnostic of choice", "hallmark", "associated with", "mechanism of action"
        )

        highYieldPhrases.forEach { phrase ->
            var startIndex = plainText.indexOf(phrase, ignoreCase = true)
            while (startIndex >= 0) {
                builder.addStyle(
                    SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    ),
                    startIndex,
                    startIndex + phrase.length
                )
                startIndex = plainText.indexOf(phrase, startIndex + phrase.length, ignoreCase = true)
            }
        }

        // Stylize vital diagnostics and clinical acronyms safely
        val abbreviations = listOf("MRI", "CT", "EKG", "ECG", "CSF", "HIV", "EEG", "ARDS", "GCS", "COPD")
        abbreviations.forEach { ab ->
            var startIndex = plainText.indexOf(ab)
            while (startIndex >= 0) {
                val isBeforeBoundary = startIndex == 0 || !plainText[startIndex - 1].isLetter()
                val isAfterBoundary = startIndex + ab.length == plainText.length || !plainText[startIndex + ab.length].isLetter()
                if (isBeforeBoundary && isAfterBoundary) {
                    builder.addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = secondaryColor
                        ),
                        startIndex,
                        startIndex + ab.length
                    )
                }
                startIndex = plainText.indexOf(ab, startIndex + ab.length)
            }
        }

        builder.toAnnotatedString()
    }
}

fun extractHighYieldTip(text: String): String? {
    if (text.isBlank()) return null
    
    val markers = listOf(
        "Important Note:", "Note:", "Key Note:", "Remember:", "Clinical Correlation:", 
        "Key Point:", "Clinical Pearl:", "clinical significance:"
    )
    
    for (marker in markers) {
        val idx = text.indexOf(marker, ignoreCase = true)
        if (idx >= 0) {
            val contentStart = idx + marker.length
            val sub = text.substring(contentStart).trim()
            
            val periods = sub.indexOf('.')
            val newlines = sub.indexOf('\n')
            val semicolons = sub.indexOf(';')
            val indices = listOf(periods, newlines, semicolons).filter { it >= 0 }
            val endIdx = if (indices.isNotEmpty()) indices.minOrNull() ?: -1 else -1
            
            val extracted = if (endIdx >= 0) sub.substring(0, endIdx).trim() else sub
            if (extracted.length in 10..150) {
                return "$marker $extracted."
            }
        }
    }
    
    // Fallback on "drug of choice" keywords
    val docIndex = text.indexOf("drug of choice", ignoreCase = true)
    if (docIndex >= 0) {
        var start = docIndex
        while (start > 0 && text[start] != '.' && text[start] != '\n') {
            start--
        }
        if (text[start] == '.' || text[start] == '\n') start++
        
        var end = docIndex
        while (end < text.length && text[end] != '.' && text[end] != '\n') {
            end++
        }
        val sentence = text.substring(start, end).trim()
        if (sentence.length in 15..150) {
            return sentence
        }
    }
    
    return null
}
