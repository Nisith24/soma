package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiMilestonesScreen(
    appState: AppState,
    onBack: () -> Unit,
    onNotificationAction: (AppNotification) -> Unit = {},
    onNotificationDismiss: (String) -> Unit = {},
    onSetSimulationOverrides: (Int?, Int?, Int?, Int?, String?) -> Unit = { _, _, _, _, _ -> },
    onResetSimulationOverrides: () -> Unit = {},
    isBottomSheet: Boolean = false
) {
    val answeredCount = appState.selectedOptions.size
    val studyStats = appState.studyStats

    // Achievements calculation
    val firstStepUnlocked = answeredCount >= 1
    val sharpShooterUnlocked = studyStats.correctCount >= 5
    val scholarUnlocked = answeredCount >= 10
    val goalSetterUnlocked = appState.dailyGoal != 10 // altered from default

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // Soma AI Diagnostics
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology, 
                            contentDescription = null, 
                            modifier = Modifier.size(32.dp), 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Soma Diagnostic AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (appState.notifications.isNotEmpty()) 
                                    "${appState.notifications.size} active alerts" 
                                    else "All diagnostics green",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))

                    SmartNotificationCenter(
                        appState = appState,
                        onNotificationAction = onNotificationAction,
                        onNotificationDismiss = onNotificationDismiss,
                        onSetSimulationOverrides = onSetSimulationOverrides,
                        onResetSimulationOverrides = onResetSimulationOverrides
                    )
                }
            }
            
            // Earned Achievements Grid
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Milestones & Achievements",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (isBottomSheet) {
        Content(modifier = Modifier.fillMaxWidth())
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Soma AI & Milestones") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            Content(modifier = Modifier.padding(innerPadding))
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
    Card(
        modifier = modifier.width(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (unlocked) {
                    Text(iconSymbol, fontSize = 24.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
