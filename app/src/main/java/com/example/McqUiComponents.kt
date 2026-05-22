package com.example

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch


enum class CanonicalSubject(
    val displayName: String,
    val icon: String,
    val keywords: List<String>
) {
    ANATOMY("Anatomy", "💀", listOf("anatom", "anant", "anat")),
    PHYSIOLOGY("Physiology", "🫁", listOf("physio", "physiology")),
    BIOCHEMISTRY("Biochemistry", "🧪", listOf("biochem", "biochemistry")),
    PATHOLOGY("Pathology", "🔬", listOf("patho", "pathology")),
    PHARMACOLOGY("Pharmacology", "💊", listOf("pharma", "pharmacology")),
    MICROBIOLOGY("Microbiology", "🦠", listOf("micro", "microbiology")),
    FMT("Forensic Medicine (FMT)", "🕵️", listOf("fmt", "forensic")),
    PSM("Community Medicine (PSM)", "🏡", listOf("psm", "community", "preventive")),
    OPHTHALMOLOGY("Ophthalmology", "👁️", listOf("ophth", "ophthalmology")),
    ENT("ENT", "👂", listOf("ent", "ear", "nose", "throat")),
    MEDICINE("Medicine", "🩺", listOf("medicine", "med")),
    SURGERY("Surgery", "🔪", listOf("surgery", "surg")),
    OBG("Obstetrics & Gynecology (OBG)", "🤰", listOf("obg", "obs", "gyne", "obstetrics", "gynecology")),
    PEDIATRICS("Pediatrics", "👶", listOf("pedia", "peds", "pediatrics")),
    ORTHOPEDICS("Orthopedics", "🦴", listOf("ortho", "orthop", "orthopedic", "orthopedics", "orthopaedic", "orthopaedics")),
    PSYCHIATRY("Psychiatry", "🧠", listOf("psyc", "psych", "psychiatry")),
    DERMATOLOGY("Dermatology", "🧴", listOf("derma", "dermatology")),
    RADIOLOGY("Radiology", "🩻", listOf("radio", "radiology")),
    ANESTHESIA("Anesthesia", "💉", listOf("anesthesia", "anaesthesia", "anest", "anaest")),
    GRAND_TESTS("Grand Tests & Mock Exams", "📝", listOf("grand test", "gt", "test series", "dams", "mock"))
}

private fun levenshtein(s1: String, s2: String): Int {
    val dp = IntArray(s2.length + 1) { it }
    for (i in 1..s1.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..s2.length) {
            val temp = dp[j]
            if (s1[i - 1] == s2[j - 1]) {
                dp[j] = prev
            } else {
                dp[j] = minOf(dp[j - 1], dp[j], prev) + 1
            }
            prev = temp
        }
    }
    return dp[s2.length]
}

fun categorizeSubject(rawName: String): CanonicalSubject? {
    val cleanName = rawName.lowercase()
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .trim()
    
    val tokens = cleanName.split(Regex("\\s+"))
        .filter { it !in listOf("pyq", "pqy", "eqb", "mcq", "qb", "qbank", "e", "bank", "old", "new", "test", "practice") }
    
    if (tokens.isEmpty()) return null

    for (category in CanonicalSubject.values()) {
        for (keyword in category.keywords) {
            for (token in tokens) {
                // Direct or token equality
                if (token == keyword) return category
                
                // Prefix or suffix-prefix matching (e.g., "peds" -> "pediatrics", "gyn" -> "gynecology")
                if (token.length >= 3 && (keyword.startsWith(token) || token.startsWith(keyword))) {
                    return category
                }
                
                // Levenshtein matching for spelling errors / typos
                if (token.length >= 4 && keyword.length >= 4) {
                    val dist = levenshtein(token, keyword)
                    if (dist <= 1 || (token.length >= 6 && dist <= 2)) {
                        return category
                    }
                }
            }
        }
    }
    
    // Substring fallback
    for (category in CanonicalSubject.values()) {
        for (keyword in category.keywords) {
            if (cleanName.contains(keyword)) {
                return category
            }
        }
    }
    
    return null
}

data class GroupedSubject(
    val canonical: CanonicalSubject?,
    val displayName: String,
    val icon: String,
    val rawMatches: List<Pair<String, Map<String, TopicStats>>>
)

fun groupSubjects(
    summary: Map<String, Map<String, TopicStats>>,
    query: String
): List<GroupedSubject> {
    val matches = mutableMapOf<CanonicalSubject?, MutableList<Pair<String, Map<String, TopicStats>>>>()
    
    for ((subject, topics) in summary) {
        // Evaluate if any topic name matches search query, or the subject name itself
        val matchesQuery = query.isBlank() || 
                           subject.contains(query, ignoreCase = true) ||
                           topics.keys.any { it.contains(query, ignoreCase = true) }
                           
        if (matchesQuery) {
            val category = categorizeSubject(subject)
            matches.getOrPut(category) { mutableListOf() }.add(subject to topics)
        }
    }
    
    val list = mutableListOf<GroupedSubject>()
    
    for (canonical in CanonicalSubject.values()) {
        val rawList = matches[canonical] ?: continue
        rawList.sortBy { it.first }
        list.add(
            GroupedSubject(
                canonical = canonical,
                displayName = canonical.displayName,
                icon = canonical.icon,
                rawMatches = rawList
            )
        )
    }
    
    val otherList = matches[null]
    if (otherList != null && otherList.isNotEmpty()) {
        otherList.sortBy { it.first }
        list.add(
            GroupedSubject(
                canonical = null,
                displayName = "Other Study Material",
                icon = "📁",
                rawMatches = otherList
            )
        )
    }
    
    return list
}

@Composable
fun SearchHighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    if (query.isBlank()) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            modifier = modifier
        )
        return
    }
    val index = text.indexOf(query, ignoreCase = true)
    if (index == -1) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            modifier = modifier
        )
        return
    }
    val highlightBackgroundColor = MaterialTheme.colorScheme.primaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    
    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        append(text.substring(0, index))
        pushStyle(
            androidx.compose.ui.text.SpanStyle(
                background = highlightBackgroundColor,
                color = highlightTextColor,
                fontWeight = FontWeight.Bold
            )
        )
        append(text.substring(index, index + query.length))
        pop()
        append(text.substring(index + query.length))
    }
    Text(
        text = annotatedString,
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun McqDashboard(
    summary: Map<String, Map<String, TopicStats>>,
    sourceSummary: Map<String, Int>,
    filterMode: String,
    onFilterModeChanged: (String) -> Unit,
    onSourceSelected: (String) -> Unit,
    studyStats: StudyStats,
    onCategorySelected: (String?, String?) -> Unit,
    onResetProgress: () -> Unit,
    onSearchClick: () -> Unit,
    dashboardSearchQuery: String = "",
    isDashboardSearchVisible: Boolean = false,
    onDashboardSearchQueryChanged: (String) -> Unit = {},
    onToggleDashboardSearch: (Boolean) -> Unit = {},
    onStartCustomSession: (Map<String, Set<String>>, ExamSettings) -> Unit = { _, _ -> },
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val totalQuestions = summary.values.sumOf { it.values.sumOf { t -> t.total } }
    var showCustomModulesDialog by remember { mutableStateOf(false) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    val groupedSubjects by produceState(initialValue = emptyList<GroupedSubject>(), summary, dashboardSearchQuery) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            groupSubjects(summary, dashboardSearchQuery)
        }
    }

    val effectivelyExpandedFolders = remember(expandedFolders, dashboardSearchQuery, groupedSubjects) {
        if (dashboardSearchQuery.isNotBlank()) {
            groupedSubjects.map { it.displayName }.toSet()
        } else {
            expandedFolders
        }
    }


    val filteredSummary = remember(summary, dashboardSearchQuery) {
        if (dashboardSearchQuery.isBlank()) summary
        else {
            summary.filter { (subject, topics) ->
                subject.contains(dashboardSearchQuery, ignoreCase = true) ||
                topics.keys.any { it.contains(dashboardSearchQuery, ignoreCase = true) }
            }
        }
    }

    val filteredSourceSummary = remember(sourceSummary, dashboardSearchQuery) {
        if (dashboardSearchQuery.isBlank()) sourceSummary
        else {
            sourceSummary.filter { (source, _) ->
                source.contains(dashboardSearchQuery, ignoreCase = true) ||
                formatSourceDisplayName(source).contains(dashboardSearchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (summary.isNotEmpty()) {
            item {
                CustomModulesCard(
                    availableSubjectsCount = summary.size,
                    onClick = { showCustomModulesDialog = true }
                )
            }
        } else {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Questions Found",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Go to Profile > Database to upload your question bank JSON files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (summary.isNotEmpty()) {
            item {
                Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDashboardSearchVisible) {
                    OutlinedTextField(
                        value = dashboardSearchQuery,
                        onValueChange = onDashboardSearchQueryChanged,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        placeholder = { Text("Filter subjects...", style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            IconButton(onClick = { onToggleDashboardSearch(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Filter", modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            if (filterMode == "source") "Filter by File" else "Filter by Subject",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onToggleDashboardSearch(true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Filter subjects",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary // highlighted to match user request better
                            )
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterMode == "subject",
                        onClick = { onFilterModeChanged("subject") },
                        label = { Text("Subject", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = filterMode == "source",
                        onClick = { onFilterModeChanged("source") },
                        label = { Text("File", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (filterMode == "source") {
            val sourcesList = filteredSourceSummary.toList().sortedWith(compareBy { (src, _) ->
                if (src == "Legacy / Untagged") 1 else 0
            })
            
            if (sourcesList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (dashboardSearchQuery.isNotBlank()) "No files match your filter." else "No file sources available.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(sourcesList) { (sourceName, count) ->
                    val isLegacy = sourceName == "Legacy / Untagged"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .clickable { onSourceSelected(sourceName) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLegacy) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLegacy) {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (isLegacy) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isLegacy) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (isLegacy) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = formatSourceDisplayName(sourceName),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isLegacy) "Pre-installed standard practice sets" else "Questions imported from file",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "$count Qs",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (groupedSubjects.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (dashboardSearchQuery.isNotBlank()) "No subjects match your filter." else "No subjects available.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                groupedSubjects.forEach { group ->
                    val isExpanded = effectivelyExpandedFolders.contains(group.displayName)
                    val totalSetsInGroup = group.rawMatches.size
                    val totalQsInGroup = group.rawMatches.sumOf { it.second.values.sumOf { t -> t.total } }
                    val finishedQsInGroup = group.rawMatches.sumOf { it.second.values.sumOf { t -> t.finished } }
                    
                    item(key = "group_${group.displayName}") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .clickable {
                                    if (dashboardSearchQuery.isBlank()) {
                                        expandedFolders = if (isExpanded) {
                                            expandedFolders - group.displayName
                                        } else {
                                            expandedFolders + group.displayName
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isExpanded) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isExpanded) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = group.icon,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(14.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        SearchHighlightedText(
                                            text = group.displayName,
                                            query = dashboardSearchQuery,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${totalSetsInGroup} ${if (totalSetsInGroup == 1) "folder" else "folders"} • ${totalQsInGroup} MCQ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (finishedQsInGroup > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                Text(
                                                    text = "$finishedQsInGroup done",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        group.rawMatches.forEach { (rawSubject, topics) ->
                                            val finishedRawQs = topics.values.sumOf { it.finished }
                                            val totalRawQs = topics.values.sumOf { it.total }
                                            
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onCategorySelected(rawSubject, null) },
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                        .fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Book,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            SearchHighlightedText(
                                                                text = rawSubject,
                                                                query = dashboardSearchQuery,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "${topics.size} topics • $totalRawQs MCQ",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (finishedRawQs > 0) {
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.padding(end = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "$finishedRawQs done",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.secondary,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Default.ChevronRight,
                                                            contentDescription = "Open Subject",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showCustomModulesDialog) {
        CustomModulesSelectionDialog(
            summary = summary,
            onDismiss = { showCustomModulesDialog = false },
            onStart = { modules, settings ->
                showCustomModulesDialog = false
                onStartCustomSession(modules, settings)
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SubjectCard(
    subject: String,
    totalTopics: Int,
    totalQuestions: Int,
    finishedQuestions: Int = 0,
    onSubjectSelected: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSubjectSelected() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "$totalTopics topics • $totalQuestions questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (finishedQuestions > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$finishedQuestions done",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun McqTopicList(
    subject: String,
    topicCounts: Map<String, TopicStats>,
    onTopicSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = "Subjects / $subject",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select a Topic",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        items(topicCounts.toList()) { (topic, stats) ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onTopicSelected(topic) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = topic,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${stats.total} questions total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (stats.finished > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${stats.finished} finished",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun McqFileTopicList(
    sourceName: String,
    allQuestions: List<McqField>,
    appState: AppState,
    onTopicSelected: (subject: String, topic: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val questionsInFile = remember(allQuestions, sourceName) {
        allQuestions.filter { q ->
            q.source_url == sourceName || (sourceName == "Legacy / Untagged" && q.source_url.isNullOrBlank())
        }
    }

    val subjectsInFile = remember(questionsInFile) {
        questionsInFile.mapNotNull { it.subject }.distinct().sorted()
    }

    var selectedSubjectFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val topicsInFile = remember(questionsInFile, selectedSubjectFilter, appState.selectedOptions) {
        questionsInFile
            .filter { q -> selectedSubjectFilter == null || q.subject == selectedSubjectFilter }
            .groupBy { q -> (q.subject ?: "Unknown Subject") to (q.topic ?: "Unknown Topic") }
            .map { (key, qList) ->
                val (sub, top) = key
                val finished = qList.count { q -> appState.selectedOptions.containsKey(q.question) }
                FileTopicItem(
                    subject = sub,
                    topic = top,
                    totalCount = qList.size,
                    finishedCount = finished
                )
            }
            .sortedWith(compareBy({ it.subject }, { it.topic }))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(end = 8.dp).size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text(
                            text = "Files / ${formatSourceDisplayName(sourceName)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Browse Topics in File",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (subjectsInFile.isNotEmpty()) {
                    Text(
                        text = "Filter by Subject:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedSubjectFilter == null,
                            onClick = { selectedSubjectFilter = null },
                            label = { Text("All Subjects") },
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        subjectsInFile.forEach { subject ->
                            FilterChip(
                                selected = selectedSubjectFilter == subject,
                                onClick = { selectedSubjectFilter = subject },
                                label = { Text(subject) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (topicsInFile.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No topics found matching current filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(topicsInFile.size) { index ->
                val item = topicsInFile[index]
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onTopicSelected(item.subject, item.topic) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.topic,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subject,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.totalCount} questions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.finishedCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${item.finishedCount} completed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Start topic",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

data class FileTopicItem(
    val subject: String,
    val topic: String,
    val totalCount: Int,
    val finishedCount: Int
)

@Composable
fun ModeSelectionDialog(
    topic: String,
    availableQuestions: Int,
    onDismiss: () -> Unit,
    onStart: (isExamMode: Boolean, questionCount: Int, timeLimitMinutes: Int) -> Unit
) {
    var isExamMode by remember { mutableStateOf(false) }
    var questionCountInput by remember { mutableStateOf(minOf(availableQuestions, 50).coerceAtLeast(1).toString()) }
    var timeLimitInput by remember { mutableStateOf(minOf(availableQuestions, 50).coerceAtLeast(1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Start Session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Topic: $topic",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Segmented control style for mode selection (using Radio Buttons or Chips for simplicity)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = !isExamMode,
                        onClick = { isExamMode = false },
                        label = { Text("Practice Mode") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = isExamMode,
                        onClick = { isExamMode = true },
                        label = { Text("Exam Mode") }
                    )
                }

                if (!isExamMode) {
                    Text(
                        text = "Practice questions at your own pace with immediate feedback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Inline settings for exam mode
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Exam Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        
                        OutlinedTextField(
                            value = questionCountInput,
                            onValueChange = { input -> 
                                if (input.all { char -> char.isDigit() }) {
                                    questionCountInput = input
                                    if (input.isNotEmpty()) {
                                        timeLimitInput = input
                                    }
                                }
                            },
                            label = { Text("Number of Questions", style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = timeLimitInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) timeLimitInput = it },
                            label = { Text("Time Limit (mins)", style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = questionCountInput.toIntOrNull() ?: 0
                    val limit = timeLimitInput.toIntOrNull() ?: 0
                    onStart(isExamMode, count, limit)
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun McqListMode(
    questions: List<McqField>,
    selectedOptions: Map<String, String>,
    bookmarkedQuestionTexts: Set<String>,
    onOptionSelected: (String, String) -> Unit,
    onToggleBookmark: (McqField) -> Unit,
    modifier: Modifier = Modifier
) {
    if (questions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No questions match your search.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(questions) { question ->
                McqItemCard(
                    question = question,
                    selectedOption = selectedOptions[question.question],
                    isBookmarked = bookmarkedQuestionTexts.contains(question.question),
                    onToggleBookmark = { onToggleBookmark(question) },
                    onOptionSelected = { option -> onOptionSelected(question.question, option) }
                )
            }
        }
    }
}

@Composable
fun ExamTimer(
    sessionStartTimeMs: Long,
    timeLimitMinutes: Int,
    modifier: Modifier = Modifier
) {
    if (sessionStartTimeMs <= 0L || timeLimitMinutes <= 0) return

    var remainingSeconds by remember { mutableStateOf(timeLimitMinutes * 60) }

    LaunchedEffect(sessionStartTimeMs, timeLimitMinutes) {
        while (true) {
            val elapsedMs = System.currentTimeMillis() - sessionStartTimeMs
            val elapsedSecs = (elapsedMs / 1000).toInt()
            val totalSecs = timeLimitMinutes * 60
            remainingSeconds = maxOf(0, totalSecs - elapsedSecs)
            if (remainingSeconds <= 0) {
                break
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeString = String.format("%02d:%02d", mins, secs)
    val isTimeCritical = remainingSeconds in 1..60

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Timer",
            tint = if (isTimeCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = timeString,
            style = MaterialTheme.typography.labelLarge,
            color = if (isTimeCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun McqQuizMode(
    question: McqField,
    index: Int,
    total: Int,
    selectedOption: String?,
    isBookmarked: Boolean,
    isExamMode: Boolean,
    onToggleBookmark: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    sessionStartTimeMs: Long = 0L,
    timeLimitMinutes: Int = 0,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 150f && index > 0) {
                            onPrev()
                        } else if (offsetX < -150f && index < total - 1) {
                            onNext()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        // Animated progress indicator at the top
        val progress by animateFloatAsState(
            targetValue = if (total > 0) ((index + 1).coerceAtMost(total).toFloat() / total) else 0f,
            label = "ProgressAnimation"
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Use a weight and vertical scroll for the question area
        Box(modifier = Modifier.weight(1f)) {
            McqItemCard(
                question = question,
                selectedOption = selectedOption,
                isBookmarked = isBookmarked,
                isExamMode = isExamMode,
                onToggleBookmark = onToggleBookmark,
                onOptionSelected = onOptionSelected,
                modifier = Modifier.fillMaxSize(),
                isScrollable = true,
                cleanStyle = true,
                headerContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Question ${index + 1} of $total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isExamMode) {
                            ExamTimer(
                                sessionStartTimeMs = sessionStartTimeMs,
                                timeLimitMinutes = timeLimitMinutes
                            )
                        }
                    }
                }
            )
        }

        // Bottom Navigation (subtle Previous, prominent Next)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPrev,
                enabled = index > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                Spacer(Modifier.width(8.dp))
                Text("Previous")
            }
            
            Button(
                onClick = onNext,
                enabled = index < total,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (index < total - 1) {
                    Text("Next", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                } else {
                    Text("Finish", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Check, contentDescription = "Finish")
                }
            }
        }
    }
}
@Composable
fun McqItemCard(
    question: McqField,
    selectedOption: String?,
    isBookmarked: Boolean = false,
    isExamMode: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = false,
    cleanStyle: Boolean = false,
    headerContent: @Composable (() -> Unit)? = null
) {
    val isRevealed = selectedOption != null
    val showFeedback = isRevealed && !isExamMode
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val viewModel: com.example.McqViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val coroutineScope = rememberCoroutineScope()

    if (isScrollable) {
        LaunchedEffect(question) {
            scrollState.scrollTo(0)
        }
    }

    val scrollModifier = if (isScrollable) {
        Modifier.verticalScroll(scrollState)
    } else Modifier

    val handleReveal = { opt: String ->
        if (!isRevealed || isExamMode) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onOptionSelected(opt)
        }
    }

    val contentBox = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            headerContent?.invoke()

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${question.subject ?: "General"} • ${question.topic ?: "General"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (onToggleBookmark != null) {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove Bookmark" else "Bookmark Question",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Question
            Text(
                text = question.question,
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Images
            if (!question.images.isNullOrBlank()) {
                val imageUrls = question.images.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    imageUrls.forEach { url ->
                        SubcomposeAsyncImage(
                            model = url,
                            contentDescription = "Question Image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            loading = {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                }
                            },
                            error = {
                                // Don't show anything if fails to load
                            }
                        )
                    }
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                question.options.forEach { option ->
                    val isCorrectAnswer = option.startsWith(question.correct_answer + ".", ignoreCase = true) 
                                    || option.startsWith(question.correct_answer + ")", ignoreCase = true)
                                    || option.startsWith(question.correct_answer + " ", ignoreCase = true)
                                    || option.equals(question.correct_answer, ignoreCase = true)

                    val isSelected = selectedOption == option

                    val backgroundColor by animateColorAsState(when {
                        !showFeedback && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        !showFeedback -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        isCorrectAnswer -> Color(0xFFE6F4F1) // soft teal background
                        isSelected && !isCorrectAnswer -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    })

                    val borderColor by animateColorAsState(when {
                        !showFeedback && isSelected -> MaterialTheme.colorScheme.primary
                        !showFeedback -> Color.Transparent
                        isCorrectAnswer -> Color(0xFF0D9488) // teal border
                        isSelected && !isCorrectAnswer -> MaterialTheme.colorScheme.error
                        else -> Color.Transparent
                    })

                    val textColor by animateColorAsState(when {
                        !showFeedback && isSelected -> MaterialTheme.colorScheme.primary
                        !showFeedback -> MaterialTheme.colorScheme.onSurface
                        isCorrectAnswer -> Color(0xFF0F766E) // darker teal for text
                        isSelected && !isCorrectAnswer -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    })

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(backgroundColor)
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable(enabled = (!isRevealed || isExamMode)) { handleReveal(option) }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (showFeedback) {
                            if (isCorrectAnswer) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Correct",
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (isSelected) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = "Incorrect",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Explanation
            if (showFeedback) {
                var isExplanationExpanded by rememberSaveable(question.question) { mutableStateOf(!question.explanation.isNullOrBlank()) }
                var aiExplanation by rememberSaveable(question.question) { mutableStateOf<String?>(null) }
                var isAiLoading by rememberSaveable(question.question) { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 1.dp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExplanationExpanded = !isExplanationExpanded }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Explanation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isExplanationExpanded) {
                            IconButton(onClick = {
                                if (aiExplanation == null && !isAiLoading) {
                                    isAiLoading = true
                                    coroutineScope.launch {
                                        aiExplanation = viewModel.getAiExplanation(
                                            question.question, question.options, question.correct_answer, question.topic, question.subject, question.explanation
                                        )
                                        isAiLoading = false
                                    }
                                }
                            }, modifier = Modifier.size(28.dp)) {
                                if (isAiLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Explanation", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = if (isExplanationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExplanationExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    AnimatedVisibility(visible = isExplanationExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            if (!question.explanation.isNullOrBlank()) {
                                HtmlText(html = question.explanation!!)
                            } else if (aiExplanation == null && !isAiLoading) {
                                Text("No official explanation provided. Click the AI spark icon above to generate a structured analysis.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            if (aiExplanation != null) {
                                if (!question.explanation.isNullOrBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        thickness = 1.dp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("AI Analysis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = aiExplanation ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier) {
        contentBox()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SessionReviewScreen(
    allQuestions: List<McqField>,
    appState: AppState,
    onReturn: () -> Unit
) {
    val selectedOptions = appState.selectedOptions
    val total = allQuestions.size
    val answeredCount = allQuestions.count { selectedOptions.containsKey(it.question) }
    val unansweredCount = total - answeredCount
    var correctCount = 0
    var wrongCount = 0
    
    val results = remember(allQuestions, selectedOptions) {
        allQuestions.associate { q ->
            val opt = selectedOptions[q.question]
            val isCorrect = if (opt != null) {
                opt.startsWith(q.correct_answer + ".", ignoreCase = true) 
                        || opt.startsWith(q.correct_answer + ")", ignoreCase = true)
                        || opt.startsWith(q.correct_answer + " ", ignoreCase = true)
                        || opt.equals(q.correct_answer, ignoreCase = true)
            } else null
            
            q.question to isCorrect
        }
    }
    
    results.values.forEach { 
        if (it == true) correctCount++
        else if (it == false) wrongCount++
    }
    
    val scorePercent = if (answeredCount > 0) (correctCount * 100) / answeredCount else 0
    val durationMs = if (appState.examSettings.sessionStartTimeMs > 0) System.currentTimeMillis() - appState.examSettings.sessionStartTimeMs else 0
    val avgTimeMs = if (answeredCount > 0) durationMs / answeredCount else 0
    val avgSeconds = avgTimeMs / 1000
    
    var filterMode by rememberSaveable { mutableStateOf("All") }
    
    val filteredQuestions = remember(filterMode, allQuestions, results) {
        val filtered = allQuestions.filter { q ->
            val res = results[q.question]
            when (filterMode) {
                "Correct" -> res == true
                "Wrong" -> res == false
                "Unanswered" -> res == null
                else -> true
            }
        }
        
        if (filterMode == "All") {
            filtered.sortedBy { q ->
                when (results[q.question]) {
                    false -> 0 // Incorrect
                    null -> 1  // Unanswered
                    true -> 2  // Correct
                }
            }
        } else {
            filtered
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // High level stats section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Session Complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("Correct", "$correctCount")
                    StatItem("Wrong", "$wrongCount")
                    StatItem("Unanswered", "$unansweredCount")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("Accuracy", "$scorePercent%")
                    StatItem("Marks", "$correctCount / $total") 
                    StatItem("Avg Time", "${avgSeconds}s / Q")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReturn, shape = RoundedCornerShape(20.dp)) {
                    Text("Return to Dashboard")
                }
            }
        }
        
        stickyHeader {
            ScrollableTabRow(
                selectedTabIndex = listOf("All", "Correct", "Wrong", "Unanswered").indexOf(filterMode),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("All", "Correct", "Wrong", "Unanswered").forEach { mode ->
                    Tab(
                        selected = filterMode == mode,
                        onClick = { filterMode = mode },
                        text = { Text(mode, fontWeight = if(filterMode == mode) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }
        
        items(filteredQuestions.size) { index ->
            val q = filteredQuestions[index]
            Box(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                McqItemCard(
                    question = q,
                    selectedOption = selectedOptions[q.question] ?: "unanswered_dummy_value",
                    isBookmarked = appState.bookmarkedQuestionTexts.contains(q.question),
                    isExamMode = false, // Force feedback display
                    cleanStyle = true,
                    onToggleBookmark = null,
                    onOptionSelected = { }
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartNotificationCenter(
    appState: AppState,
    onNotificationAction: (AppNotification) -> Unit,
    onNotificationDismiss: (String) -> Unit,
    onSetSimulationOverrides: (Int?, Int?, Int?, Int?, String?) -> Unit,
    onResetSimulationOverrides: () -> Unit
) {
    var showSimulationPanel by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Soma Diagnostic AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (appState.notifications.isNotEmpty()) Color(0xFFD97706) else Color(0xFF059669)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appState.notifications.isNotEmpty()) 
                                    "${appState.notifications.size} active alerts" 
                                    else "All diagnostics green",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showSimulationPanel = !showSimulationPanel }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (showSimulationPanel) Icons.Default.Close else Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (showSimulationPanel) "Hide Sim" else "Simulate",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications List
            if (appState.notifications.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🩺 Diagnostics Stable!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Either you're a flawless medical prodigy, or we have no diagnostic alerts yet. Tap 'Simulate' to test hilarious clinical scenarios!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        if (appState.dismissedNotificationIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onResetSimulationOverrides) {
                                Text("Restore Dismissed Alerts", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appState.notifications.forEach { notification ->
                        key(notification.id) {
                            NotificationItemRow(
                                notification = notification,
                                onAction = { onNotificationAction(notification) },
                                onDismiss = { onNotificationDismiss(notification.id) }
                            )
                        }
                    }
                }
            }

            // Collapsible Simulation Card
            AnimatedVisibility(
                visible = showSimulationPanel,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Diagnostic Simulation Panel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Instantly test all 10-15 different notification combinations. Change streaks, simulated elapsed times, and subject warnings:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Selection buttons in FlowRow
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SimulationBtn(
                            label = "🎯 Goal Done (15 solved)",
                            onClick = { onSetSimulationOverrides(null, null, 15, null, null) }
                        )
                        SimulationBtn(
                            label = "🐌 Goal Slacking (2 solved)",
                            onClick = { onSetSimulationOverrides(null, null, 2, null, null) }
                        )
                        SimulationBtn(
                            label = "📉 Streak Broken (0 days)",
                            onClick = { onSetSimulationOverrides(0, null, null, null, null) }
                        )
                        SimulationBtn(
                            label = "💨 Fragile Streak (2 days)",
                            onClick = { onSetSimulationOverrides(2, null, null, null, null) }
                        )
                        SimulationBtn(
                            label = "🔥 High Streak (9 days)",
                            onClick = { onSetSimulationOverrides(9, null, null, null, null) }
                        )
                        SimulationBtn(
                            label = "🚀 Just Active (0 hrs)",
                            onClick = { onSetSimulationOverrides(null, 0, null, null, null) }
                        )
                        SimulationBtn(
                            label = "🤨 Distracted (6 hrs)",
                            onClick = { onSetSimulationOverrides(null, 6, null, null, null) }
                        )
                        SimulationBtn(
                            label = "📚 Dusty (30 hrs)",
                            onClick = { onSetSimulationOverrides(null, 30, null, null, null) }
                        )
                        SimulationBtn(
                            label = "🧑‍🎨 Art Major (120 hrs)",
                            onClick = { onSetSimulationOverrides(null, 120, null, null, null) }
                        )
                        SimulationBtn(
                            label = "🩸 Pathology (30% acc)",
                            onClick = { onSetSimulationOverrides(null, null, null, 30, "Pathology") }
                        )
                        SimulationBtn(
                            label = "💊 Pharmacology (42% acc)",
                            onClick = { onSetSimulationOverrides(null, null, null, 42, "Pharmacology") }
                        )
                        SimulationBtn(
                            label = "👶 ObGyn (55% accuracy)",
                            onClick = { onSetSimulationOverrides(null, null, null, 55, "Gynaecology") }
                        )
                        SimulationBtn(
                            label = "🔬 Untouched Micro",
                            onClick = { onSetSimulationOverrides(null, null, null, null, "Microbiology") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onResetSimulationOverrides
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulationBtn(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun NotificationItemRow(
    notification: AppNotification,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val urgencyColor = when (notification.urgency) {
        NotificationUrgency.LOW -> Color(0xFF10B981)
        NotificationUrgency.MEDIUM -> Color(0xFFF59E0B)
        NotificationUrgency.HIGH -> Color(0xFFEF4444)
    }

    val iconValue = when (notification.category) {
        "goal" -> Icons.Default.CheckCircle
        "streak" -> Icons.Default.LocalFireDepartment
        "last_active" -> Icons.Default.Schedule
        "subject" -> Icons.Default.MedicalServices
        else -> Icons.Default.Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(urgencyColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = iconValue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Alert",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = notification.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            if (notification.actionLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = urgencyColor.copy(alpha = 0.12f),
                            contentColor = urgencyColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = notification.actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomModulesCard(
    availableSubjectsCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Custom Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Combine $availableSubjectsCount subjects & topics into customized sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomModulesSelectionDialog(
    summary: Map<String, Map<String, TopicStats>>,
    onDismiss: () -> Unit,
    onStart: (modules: Map<String, Set<String>>, settings: ExamSettings) -> Unit
) {
    data class SavedCustomModuleProfile(val id: Int, val name: String, val modules: Map<String, Set<String>>)
    
    fun serializeProfile(profile: SavedCustomModuleProfile): String {
        val root = org.json.JSONObject()
        root.put("id", profile.id)
        root.put("name", profile.name)
        val map = org.json.JSONObject()
        for ((k, v) in profile.modules) {
            val array = org.json.JSONArray()
            v.forEach { array.put(it) }
            map.put(k, array)
        }
        root.put("modules", map)
        return root.toString()
    }

    fun parseProfile(json: String): SavedCustomModuleProfile? {
        return try {
            val root = org.json.JSONObject(json)
            val id = root.getInt("id")
            val name = root.getString("name")
            val mapObj = root.getJSONObject("modules")
            val map = mutableMapOf<String, Set<String>>()
            val keys = mapObj.keys()
            while(keys.hasNext()) {
                val key = keys.next()
                val arr = mapObj.getJSONArray(key)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                map[key] = set
            }
            SavedCustomModuleProfile(id, name, map)
        } catch(e: Exception) { null }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("custom_modules", android.content.Context.MODE_PRIVATE) }
    
    val allSelectable = remember(summary) { summary.mapValues { it.value.keys.toSet() } }
    var selectedModules by remember { mutableStateOf(allSelectable) }
    var isExamMode by remember { mutableStateOf(false) }
    var questionCountInput by remember { mutableStateOf("25") }
    var timeLimitInput by remember { mutableStateOf("30") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedSubjects by remember { mutableStateOf(setOf<String>()) }
    
    var savedClubs by remember { 
        mutableStateOf(
            (1..3).mapNotNull { id ->
                val json = sharedPrefs.getString("club_$id", null)
                if (json != null) parseProfile(json) else null
            }.sortedBy { it.id }
        )
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var newClubName by remember { mutableStateOf("") }
    
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Current Club") },
            text = {
                OutlinedTextField(
                    value = newClubName,
                    onValueChange = { newClubName = it },
                    label = { Text("Club Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newClubName.isNotBlank()) {
                        val newId = (savedClubs.maxOfOrNull { it.id } ?: 0) + 1
                        val replaceId = if (savedClubs.size >= 3) savedClubs.minByOrNull { it.id }?.id ?: 1 else newId
                        val newClub = SavedCustomModuleProfile(replaceId, newClubName, selectedModules)
                        val newList = savedClubs.filter { it.id != replaceId } + newClub
                        savedClubs = newList.sortedBy { it.id }
                        sharedPrefs.edit().putString("club_$replaceId", serializeProfile(newClub)).apply()
                        showSaveDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    val filteredSummary = remember(summary, searchQuery) {
        if (searchQuery.isBlank()) summary
        else {
            summary.mapNotNull { (sub, tops) ->
                if (sub.contains(searchQuery, ignoreCase = true)) {
                    sub to tops
                } else {
                    val filteredTops = tops.filterKeys { it.contains(searchQuery, ignoreCase = true) }
                    if (filteredTops.isNotEmpty()) sub to filteredTops else null
                }
            }.toMap()
        }
    }

    @Composable
    fun HighlightedText(
        text: String,
        query: String,
        style: androidx.compose.ui.text.TextStyle,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        color: Color = Color.Unspecified
    ) {
        if (query.isBlank()) {
            Text(
                text = text,
                style = style,
                fontWeight = fontWeight,
                color = color,
                modifier = modifier
            )
            return
        }
        val index = text.indexOf(query, ignoreCase = true)
        if (index == -1) {
            Text(
                text = text,
                style = style,
                fontWeight = fontWeight,
                color = color,
                modifier = modifier
            )
            return
        }
        val highlightBackgroundColor = MaterialTheme.colorScheme.primaryContainer
        val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        
        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
            append(text.substring(0, index))
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    background = highlightBackgroundColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            )
            append(text.substring(index, index + query.length))
            pop()
            append(text.substring(index + query.length))
        }
        Text(
            text = annotatedString,
            style = style,
            fontWeight = fontWeight,
            color = color,
            modifier = modifier
        )
    }

    LaunchedEffect(searchQuery, filteredSummary) {
        if (searchQuery.isNotBlank()) {
            expandedSubjects = expandedSubjects + filteredSummary.keys
        }
    }

    val SelectionPane: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxHeight()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search topics and subjects") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { selectedModules = allSelectable },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Select All")
                }
                TextButton(
                    onClick = { selectedModules = emptyMap() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                filteredSummary.forEach { (subject, topics) ->
                    item {
                        val subjectSelectedTopics = selectedModules[subject] ?: emptySet()
                        val allTopicsInSubject = allSelectable[subject] ?: emptySet()
                        val isAllSelected = subjectSelectedTopics.containsAll(topics.keys) && topics.isNotEmpty()
                        val isPartiallySelected = !isAllSelected && subjectSelectedTopics.any { it in topics.keys }
                        val isExpanded = expandedSubjects.contains(subject)
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        expandedSubjects = if (isExpanded) expandedSubjects - subject else expandedSubjects + subject
                                    }.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { expandedSubjects = if (isExpanded) expandedSubjects - subject else expandedSubjects + subject },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand/Collapse"
                                        )
                                    }
                                    androidx.compose.material3.TriStateCheckbox(
                                        state = if (isAllSelected) androidx.compose.ui.state.ToggleableState.On else if (isPartiallySelected) androidx.compose.ui.state.ToggleableState.Indeterminate else androidx.compose.ui.state.ToggleableState.Off,
                                        onClick = {
                                            val newMap = selectedModules.toMutableMap()
                                            if (isAllSelected) {
                                                newMap[subject] = newMap[subject]?.minus(topics.keys) ?: emptySet()
                                                if (newMap[subject]?.isEmpty() == true) newMap.remove(subject)
                                            } else {
                                                newMap[subject] = (newMap[subject] ?: emptySet()) + topics.keys
                                            }
                                            selectedModules = newMap
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    HighlightedText(
                                        text = subject,
                                        query = searchQuery,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        topics.forEach { (topic, stats) ->
                                            val isTopicSelected = subjectSelectedTopics.contains(topic)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    val newMap = selectedModules.toMutableMap()
                                                    val curSet = newMap[subject] ?: emptySet()
                                                    val newSet = if (isTopicSelected) curSet - topic else curSet + topic
                                                    if (newSet.isEmpty()) newMap.remove(subject) else newMap[subject] = newSet
                                                    selectedModules = newMap
                                                }.padding(start = 48.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isTopicSelected,
                                                    onCheckedChange = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    HighlightedText(
                                                        text = topic,
                                                        query = searchQuery,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(text = "${stats.total} questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val ConfigPane: @Composable () -> Unit = {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Mode Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isExamMode,
                        onClick = { isExamMode = false },
                        label = { Text("Practice") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isExamMode,
                        onClick = { isExamMode = true },
                        label = { Text("Exam/Mock") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (isExamMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Mock Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        
                        OutlinedTextField(
                            value = questionCountInput,
                            onValueChange = { input -> 
                                if (input.all { char -> char.isDigit() }) questionCountInput = input
                            },
                            label = { Text("Number of Questions", style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = timeLimitInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) timeLimitInput = it },
                            label = { Text("Time Limit (mins)", style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                androidx.compose.material3.HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Saved Clubs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { newClubName = ""; showSaveDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Current")
                    }
                }
                
                if (savedClubs.isEmpty()) {
                    Text(
                        "No saved clubs yet. Select modules and save them for quick access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    savedClubs.forEach { club ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectedModules = club.modules }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(club.name, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = {
                                    val newList = savedClubs.filter { it.id != club.id }
                                    savedClubs = newList
                                    sharedPrefs.edit().remove("club_${club.id}").apply()
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false)) 
                
                Button(
                    onClick = {
                        if (selectedModules.isEmpty()) return@Button
                        val count = if (isExamMode) (questionCountInput.toIntOrNull() ?: 25) else 0
                        val limit = if (isExamMode) (timeLimitInput.toIntOrNull() ?: 30) else 0
                        onStart(selectedModules, ExamSettings(isExamMode, count, limit))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = selectedModules.isNotEmpty()
                ) {
                    Text("Launch Session", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth > 600.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Custom Modules",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (isTablet) {
                        Row(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) { SelectionPane() }
                            androidx.compose.material3.VerticalDivider(modifier = Modifier.fillMaxHeight())
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) { ConfigPane() }
                        }
                    } else {
                        Column(modifier = Modifier.weight(1.5f)) { SelectionPane() }
                        Surface(shadowElevation = 8.dp) {
                            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)) { ConfigPane() }
                        }
                    }
                }
            }
        }
    }
}

