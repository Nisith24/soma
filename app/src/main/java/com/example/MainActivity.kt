package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: McqViewModel = viewModel()
            val appState by viewModel.state.collectAsStateWithLifecycle()
            val isDark = when (appState.customTheme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                McqViewerApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McqViewerApp(viewModel: McqViewModel) {
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val hideOuterTopBar = currentRoute?.contains("Auth") == true || currentRoute?.contains("JsonUpload") == true
            if (!hideOuterTopBar) {
                val titleOverride = when {
                    currentRoute?.contains("Profile") == true -> "User Profile"
                    currentRoute?.contains("Statistics") == true -> "Performance Analytics"
                    else -> null
                }
                McqTopAppBar(
                    appState = appState,
                    scrollBehavior = scrollBehavior,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onSearchToggle = viewModel::setSearchVisible,
                    onBack = {
                        if (appState.selectedTopic != null) {
                            viewModel.selectCategoryInSource(appState.selectedSubject, null, appState.selectedSource)
                        } else if (appState.selectedSubject != null || appState.selectedSource != null || appState.searchQuery.isNotBlank()) {
                            viewModel.resetNavigation()
                        } else {
                            if (!navController.popBackStack()) {
                                viewModel.resetNavigation()
                            }
                        }
                    },
                    onViewModeToggle = { viewModel.setViewMode(if (appState.viewMode == "list") "quiz" else "list") },
                    onProfileClick = { 
                        if (appState.isLoggedIn) navController.navigate(Screen.Profile) 
                        else navController.navigate(Screen.Auth) 
                    },
                    titleOverride = titleOverride,
                    showActions = currentRoute?.contains("Home") == true
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Home> {
                HomeScreen(
                    viewModel = viewModel, 
                    appState = appState, 
                    onImport = { navController.navigate(Screen.JsonUpload) },
                    onSearchToggle = { viewModel.setSearchVisible(it) },
                    onNavigateToProfile = { 
                        if (appState.isLoggedIn) navController.navigate(Screen.Profile) 
                        else navController.navigate(Screen.Auth) 
                    },
                    onNavigateToStats = { navController.navigate(Screen.Statistics) }
                )
            }
            composable<Screen.Profile> {
                ProfileScreen(
                    appState = appState,
                    onBack = { navController.popBackStack() },
                    onNavigateToStatistics = { navController.navigate(Screen.Statistics) },
                    onUpdateProfile = viewModel::updateProfile,
                    onUpdateDailyGoal = viewModel::setDailyGoal,
                    onUpdateTheme = viewModel::setThemeMode,
                    onResetSelections = viewModel::clearSelections,
                    onNavigateToJsonUpload = { navController.navigate(Screen.JsonUpload) },
                    onToggleBookmark = viewModel::toggleBookmark,
                    onLogout = { 
                        viewModel.logout()
                        navController.navigate(Screen.Home) { 
                            popUpTo(0) 
                        }
                    },
                    onNotificationAction = { notification ->
                        viewModel.handleNotificationAction(
                            notification = notification,
                            onNavigateToProfile = { /* Already on profile screen */ },
                            onNavigateToStats = { navController.navigate(Screen.Statistics) }
                        )
                    },
                    onNotificationDismiss = viewModel::dismissNotification,
                    onSetSimulationOverrides = viewModel::setSimulationOverrides,
                    onResetSimulationOverrides = viewModel::resetSimulationOverrides
                )
            }
            composable<Screen.JsonUpload> {
                JsonUploadScreen(
                    viewModel = viewModel,
                    appState = appState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Auth> {
                AuthScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.popBackStack()
                        navController.navigate(Screen.Profile)
                    }
                )
            }
            composable<Screen.Statistics> {
                UserStatisticsScreen(appState = appState)
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: McqViewModel, 
    appState: AppState, 
    onImport: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    val isDeepNavigation = appState.selectedTopic != null || appState.selectedSubject != null || appState.selectedSource != null || appState.searchQuery.isNotBlank() || appState.customModules.isNotEmpty()
    BackHandler(enabled = isDeepNavigation) {
        if (appState.selectedTopic != null) {
            viewModel.selectCategoryInSource(appState.selectedSubject, null, appState.selectedSource)
        } else {
            viewModel.resetNavigation()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = appState.uiState) {
            is McqUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is McqUiState.Success -> {
                if (appState.selectedSubject == null && appState.selectedSource == null && appState.searchQuery.isBlank() && appState.customModules.isEmpty()) {
                    McqDashboard(
                        summary = appState.categorySummary,
                        sourceSummary = appState.sourceSummary,
                        filterMode = appState.filterMode,
                        onFilterModeChanged = viewModel::setFilterMode,
                        onSourceSelected = viewModel::selectSource,
                        studyStats = appState.studyStats,
                        onCategorySelected = viewModel::selectCategory,
                        onResetProgress = viewModel::clearSelections,
                        onSearchClick = { onSearchToggle(true) },
                        dashboardSearchQuery = appState.dashboardSearchQuery,
                        isDashboardSearchVisible = appState.isDashboardSearchVisible,
                        onDashboardSearchQueryChanged = viewModel::onDashboardSearchQueryChanged,
                        onToggleDashboardSearch = viewModel::setDashboardSearchVisible,
                        onStartCustomSession = viewModel::startCustomSession,
                        appState = appState
                    )
                } else if (appState.selectedSource != null && appState.selectedTopic == null && appState.searchQuery.isBlank() && appState.customModules.isEmpty()) {
                    McqFileTopicList(
                        sourceName = appState.selectedSource,
                        allQuestions = appState.allQuestions,
                        appState = appState,
                        onTopicSelected = { subject, topic ->
                            viewModel.selectCategoryInSource(subject, topic, appState.selectedSource)
                            viewModel.showTopicDialog(topic)
                        },
                        onBack = viewModel::resetNavigation
                    )
                } else if (appState.selectedSubject != null && appState.selectedTopic == null && appState.searchQuery.isBlank() && appState.customModules.isEmpty()) {
                    McqTopicList(
                        subject = appState.selectedSubject,
                        topicCounts = appState.categorySummary[appState.selectedSubject] ?: emptyMap(),
                        onTopicSelected = { viewModel.showTopicDialog(it) },
                        onBack = viewModel::resetNavigation
                    )
                } else {
                    QuizOrListContent(viewModel, appState, state.questions)
                }
            }
            is McqUiState.Error -> ErrorState(state.message)
        }
        
        if (appState.pendingTopicForDialog != null) {
            val maxQ = appState.categorySummary[appState.selectedSubject]?.get(appState.pendingTopicForDialog)?.total ?: 50
            
            ModeSelectionDialog(
                topic = appState.pendingTopicForDialog,
                availableQuestions = maxQ,
                onDismiss = { viewModel.showTopicDialog(null) },
                onStart = { isExamMode, count, timeLimit ->
                    viewModel.startTopicSession(
                        subject = appState.selectedSubject,
                        topic = appState.pendingTopicForDialog,
                        settings = ExamSettings(
                            isExamMode = isExamMode,
                            questionCount = count,
                            timeLimitMinutes = timeLimit
                        ),
                        keepSource = (appState.selectedSource != null)
                    )
                }
            )
        }
    }
}

@Composable
fun QuizOrListContent(viewModel: McqViewModel, appState: AppState, questions: List<McqField>) {
    if (appState.viewMode == "list") {
        McqListMode(
            questions = questions,
            selectedOptions = appState.selectedOptions,
            bookmarkedQuestionTexts = appState.bookmarkedQuestionTexts,
            onToggleBookmark = viewModel::toggleBookmark,
            onOptionSelected = viewModel::selectOption
        )
    } else if (questions.isNotEmpty()) {
        val index = appState.quizIndex.coerceIn(0, questions.size)
        if (index < questions.size) {
            val current = questions[index]
            McqQuizMode(
                question = current,
                index = index,
                total = questions.size,
                selectedOption = appState.selectedOptions[current.question],
                isBookmarked = appState.bookmarkedQuestionTexts.contains(current.question),
                isExamMode = appState.examSettings.isExamMode,
                sessionStartTimeMs = appState.examSettings.sessionStartTimeMs,
                timeLimitMinutes = appState.examSettings.timeLimitMinutes,
                onToggleBookmark = { viewModel.toggleBookmark(current) },
                onOptionSelected = { viewModel.selectOption(current.question, it) },
                onNext = viewModel::nextQuizQuestion,
                onPrev = viewModel::previousQuizQuestion
            )
        } else {
            SessionReviewScreen(questions, appState, viewModel::resetNavigation)
        }
    }
}



@Composable
fun ErrorState(message: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        Text(message)
    }
}
