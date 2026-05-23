package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    appState: AppState,
    onBack: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onUpdateDailyGoal: (Int) -> Unit,
    onUpdateTheme: (AppThemeMode) -> Unit,
    onUpdateTtsSettings: (Float, Float) -> Unit = { _, _ -> },
    onUpdateGeminiSettings: (String, String) -> Unit = { _, _ -> },
    onResetSelections: () -> Unit,
    onNavigateToJsonUpload: () -> Unit,
    onToggleBookmark: (McqField) -> Unit,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNotificationAction: (AppNotification) -> Unit = {},
    onNotificationDismiss: (String) -> Unit = {},
    onSetSimulationOverrides: (Int?, Int?, Int?, Int?, String?) -> Unit = { _, _, _, _, _ -> },
    onResetSimulationOverrides: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isEditingProfile by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(appState.displayName) }
    var tempEmail by remember { mutableStateOf(appState.email) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var isStudySpeedExpanded by remember { mutableStateOf(false) }
    var isAudioSettingsExpanded by remember { mutableStateOf(false) }

    var tempGeminiApiKey by remember { mutableStateOf(appState.geminiApiKey) }

    // Synchronize state if external state changes
    LaunchedEffect(appState.displayName, appState.email, appState.geminiApiKey) {
        tempName = appState.displayName
        tempEmail = appState.email
        tempGeminiApiKey = appState.geminiApiKey
    }

    val totalQuestions = appState.allQuestions.size
    val answeredCount = appState.selectedOptions.size
    val studyStats = appState.studyStats

    // Daily goal percentage computation
    val dailyProgressPercent = if (appState.dailyGoal > 0) {
        (answeredCount.toFloat() / appState.dailyGoal.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Achievements calculation
    val firstStepUnlocked = answeredCount >= 1
    val sharpShooterUnlocked = studyStats.correctCount >= 5
    val scholarUnlocked = answeredCount >= 10
    val goalSetterUnlocked = appState.dailyGoal != 10 // altered from default

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        
        // 1. Reduced Size User Profile Card at Top
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            AnimatedContent(
                targetState = isEditingProfile,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ProfileEditAnim"
            ) { editing ->
                if (editing) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Edit Profile Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = tempEmail,
                            onValueChange = { tempEmail = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { isEditingProfile = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (tempName.isNotBlank() && tempEmail.isNotBlank()) {
                                        onUpdateProfile(tempName, tempEmail)
                                        isEditingProfile = false
                                        Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Fields cannot be blank", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val initials = if (appState.isLoggedIn && tempName.isNotBlank()) {
                                tempName.split(" ").filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.uppercase().take(2)
                            } else "G"
                            
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!appState.isLoggedIn) {
                                        onNavigateToLogin()
                                    }
                                }
                        ) {
                            Text(
                                text = if (appState.isLoggedIn) appState.displayName else "Guest User (Tap to login)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (appState.isLoggedIn) appState.email else "Tap to sign in & sync progress",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (appState.isLoggedIn) {
                            IconButton(onClick = { isEditingProfile = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            IconButton(onClick = onLogout) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Log Out",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(onClick = onNavigateToLogin) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = "Log In",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Today's Progress Goal Box (with Settings button to toggle study speed collapsible)
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Today's Progress Goal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { isStudySpeedExpanded = !isStudySpeedExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Canvas Progress Ring
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val backgroundStrokeColor = MaterialTheme.colorScheme.surfaceVariant
                        val progressRingColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = backgroundStrokeColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = progressRingColor,
                                startAngle = -90f,
                                sweepAngle = dailyProgressPercent * 360f,
                                useCenter = false,
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(dailyProgressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Answered: $answeredCount / ${appState.dailyGoal}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = if (answeredCount >= appState.dailyGoal) {
                                "Daily target smashed!"
                            } else {
                                "${appState.dailyGoal - answeredCount} more to hit target."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isStudySpeedExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Configure Study Speed (Daily Goal: ${appState.dailyGoal})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Slider(
                            value = appState.dailyGoal.toFloat(),
                            onValueChange = { onUpdateDailyGoal(it.toInt()) },
                            valueRange = 5f..50f,
                            steps = 8,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5 (Casual)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text("50 (Elite)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        // 3. Bookmarks Card (Standard button with only icon, saving count only as number)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved Bookmarks",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Saved Bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${appState.bookmarkedQuestions.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = { if (appState.bookmarkedQuestions.isNotEmpty()) showBookmarksSheet = true },
                        enabled = appState.bookmarkedQuestions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Open Bookmarks",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 4. User Statistics Card (Standard simple button)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = "Statistics",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "User Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = { onNavigateToStatistics() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View Stats")
                }
            }
        }

        // 5. Theme Settings (Renamed to "Theme")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        Triple(AppThemeMode.LIGHT, Icons.Default.LightMode, "Light"),
                        Triple(AppThemeMode.DARK, Icons.Default.DarkMode, "Dark"),
                        Triple(AppThemeMode.SYSTEM, Icons.Default.Settings, "System")
                    )

                    options.forEach { (mode, icon, text) ->
                        val isSelected = appState.customTheme == mode
                        Button(
                            onClick = { onUpdateTheme(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // 5a. Dedicated Gemini API Settings Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Gemini API Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = tempGeminiApiKey,
                    onValueChange = { tempGeminiApiKey = it },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show"
                            )
                        }
                    }
                )
                if (tempGeminiApiKey != appState.geminiApiKey) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "You have unsaved changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Button(
                    onClick = {
                        onUpdateGeminiSettings(tempGeminiApiKey, appState.geminiVoice)
                        Toast.makeText(context, "Gemini API key saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = tempGeminiApiKey != appState.geminiApiKey,
                    modifier = Modifier.fillMaxWidth().testTag("save_gemini_key_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save API Key")
                }
            }
        }

        // 5b. Combined Collapsible Audio & Voice Settings
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAudioSettingsExpanded = !isAudioSettingsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Audio & Gemini Voice Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { isAudioSettingsExpanded = !isAudioSettingsExpanded }) {
                        Icon(
                            imageVector = if (isAudioSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAudioSettingsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (isAudioSettingsExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Native TTS Engine Settings Subsection
                    Text(
                        "Native TTS Engine Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("Speech Rate: ${String.format("%.1fx", appState.ttsSpeed)}", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = appState.ttsSpeed,
                                onValueChange = { onUpdateTtsSettings(appState.ttsPitch, it) },
                                valueRange = 0.5f..2.0f,
                                steps = 14
                            )
                        }
                        
                        Column {
                            Text("Voice Pitch: ${String.format("%.1fx", appState.ttsPitch)}", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = appState.ttsPitch,
                                onValueChange = { onUpdateTtsSettings(it, appState.ttsSpeed) },
                                valueRange = 0.5f..2.0f,
                                steps = 14
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Gemini Voice Settings Subsection
                    Text(
                        "Gemini Voice Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Select Voice:", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val voices = listOf("Aoede", "Charon", "Fenrir", "Kore", "Puck")
                            items(voices) { voice ->
                                androidx.compose.material3.FilterChip(
                                    selected = appState.geminiVoice == voice,
                                    onClick = { onUpdateGeminiSettings(appState.geminiApiKey, voice) },
                                    label = { Text(voice) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Database Tools & Storage Management (at bottom, above footer and diagnostics)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Database and Study Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Button(
                    onClick = onNavigateToJsonUpload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload JSON")
                }

                Button(
                    onClick = {
                        onResetSelections()
                        Toast.makeText(context, "Study progress has been reset", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Study Selections")
                }
            }
        }

        // 7. Earned Achievements Grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Earned Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AchievementChip(
                    title = "First Step",
                    unlocked = firstStepUnlocked,
                    iconSymbol = "🎓",
                    description = "Answered your first MCQ"
                )
                AchievementChip(
                    title = "Sharpshooter",
                    unlocked = sharpShooterUnlocked,
                    iconSymbol = "🎯",
                    description = "Corrected 5+ answers"
                )
                AchievementChip(
                    title = "MCQ Scholar",
                    unlocked = scholarUnlocked,
                    iconSymbol = "📚",
                    description = "Studied 10+ questions"
                )
                AchievementChip(
                    title = "Goal Setter",
                    unlocked = goalSetterUnlocked,
                    iconSymbol = "⚡",
                    description = "Customized your study speed"
                )
            }
        }

        // 8. Soma Diagnostic AI (Collapsible) - Moved to bottom of the page
        var isSomaExpanded by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isSomaExpanded = !isSomaExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology, 
                            contentDescription = null, 
                            modifier = Modifier.size(32.dp), 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Soma Diagnostic AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (appState.notifications.isNotEmpty()) 
                                    "${appState.notifications.size} active alerts" 
                                    else "All diagnostics green",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    IconButton(onClick = { isSomaExpanded = !isSomaExpanded }) {
                        Icon(
                            imageVector = if (isSomaExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isSomaExpanded) "Collapse" else "Expand"
                        )
                    }
                }
                
                AnimatedVisibility(
                    visible = isSomaExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        SmartNotificationCenter(
                            appState = appState,
                            onNotificationAction = onNotificationAction,
                            onNotificationDismiss = onNotificationDismiss,
                            onSetSimulationOverrides = onSetSimulationOverrides,
                            onResetSimulationOverrides = onResetSimulationOverrides
                        )
                    }
                }
            }
        }

        // Bottom spacer to compensate for potential safe system bottom pad
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Saved Bookmarks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showBookmarksSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(appState.bookmarkedQuestions) { question ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = question.subject ?: "General",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onToggleBookmark(question) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Remove Bookmark",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    text = question.question,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
                                )

                                // Highlight options
                                val colorScheme = MaterialTheme.colorScheme
                                question.options.forEach { option ->
                                    val isCorrect = option.startsWith(question.correct_answer + ".", ignoreCase = true)
                                                    || option.startsWith(question.correct_answer + ")", ignoreCase = true)
                                                    || option.startsWith(question.correct_answer + " ", ignoreCase = true)
                                                    || option.equals(question.correct_answer, ignoreCase = true)

                                    val optionBg = if (isCorrect) Color(0xFFE6F4F1) else Color.Transparent
                                    val optionBorderColor = if (isCorrect) Color(0xFF0D9488) else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    val optionTextColor = if (isCorrect) Color(0xFF0F766E) else colorScheme.onSurface

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(optionBg)
                                            .border(1.dp, optionBorderColor, RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = optionTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCorrect) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Correct Answer",
                                                tint = Color(0xFF0D9488),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (!question.explanation.isNullOrBlank()) {
                                    var showExplanationDetail by remember { mutableStateOf(false) }

                                    TextButton(
                                        onClick = { showExplanationDetail = !showExplanationDetail },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Icon(
                                            if (showExplanationDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (showExplanationDetail) "Hide Explanation" else "Show Explanation")
                                    }

                                    if (showExplanationDetail) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                HtmlText(html = question.explanation)
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

@Composable
fun AchievementChip(
    title: String,
    unlocked: Boolean,
    iconSymbol: String,
    description: String,
    modifier: Modifier = Modifier
) {
    var showExplanation by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clickable { showExplanation = !showExplanation }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (unlocked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = iconSymbol,
                    fontSize = 20.sp,
                    modifier = Modifier.alpha(if (unlocked) 1.0f else 0.4f)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (!unlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
            if (showExplanation) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
