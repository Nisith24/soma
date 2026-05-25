package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: McqViewModel = viewModel()
            val appState by viewModel.state.collectAsStateWithLifecycle()
            val isDark = when (appState.customTheme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> true
            }

            MyApplicationTheme(appTheme = appState.customTheme, darkTheme = isDark) {
                val context = LocalContext.current
                val ttsManager = remember(context) { com.example.TtsManager(context) }
                val voiceOrchestrator = remember(context) { com.example.voice.VoiceOrchestrator(context) }
                DisposableEffect(ttsManager, voiceOrchestrator) {
                    onDispose { 
                        ttsManager.shutdown()
                        voiceOrchestrator.release()
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.LocalTtsManager provides ttsManager,
                    com.example.LocalVoiceOrchestrator provides voiceOrchestrator
                ) {
                    McqViewerApp(viewModel)
                }
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
    
    val ttsManager = com.example.LocalTtsManager.current
    
    // Stop audio when internal category selection states change or navigation occurs
    LaunchedEffect(
        currentRoute,
        appState.viewMode,
        appState.quizIndex,
        appState.selectedTopic,
        appState.selectedSubject,
        appState.selectedSource
    ) {
        ttsManager?.stop()
    }

    // Stop audio if the user goes to the phone's home screen or backgrounds the app
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ttsManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                ttsManager?.stop()
                viewModel.commitSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val hideOuterTopBar = currentRoute?.contains("Auth") == true || currentRoute?.contains("JsonUpload") == true || currentRoute?.contains("Profile") == true || currentRoute?.contains("AiMilestones") == true
            if (!hideOuterTopBar) {
                val titleOverride = when {
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
                        navController.navigate(Screen.Profile()) 
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
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300)) { it / 8 } },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(300)) { -it / 8 } },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300)) { -it / 8 } },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(300)) { it / 8 } }
        ) {
            composable<Screen.Home> {
                HomeScreen(
                    viewModel = viewModel, 
                    appState = appState, 
                    onImport = { navController.navigate(Screen.Profile(initialSheet = "settings")) },
                    onSearchToggle = { viewModel.setSearchVisible(it) },
                    onNavigateToProfile = { 
                        navController.navigate(Screen.Profile()) 
                    },
                    onNavigateToStats = { navController.navigate(Screen.Profile(initialSheet = "statistics")) }
                )
            }
            composable<Screen.Profile> { backStackEntry ->
                val profileRoute = backStackEntry.toRoute<Screen.Profile>()
                ProfileScreen(
                    viewModel = viewModel,
                    appState = appState,
                    initialSheet = profileRoute.initialSheet,
                    onBack = { navController.popBackStack() },
                    onUpdateProfile = viewModel::updateProfile,
                    onUpdateDailyGoal = viewModel::setDailyGoal,
                    onUpdateTheme = viewModel::setThemeMode,
                    onUpdateTtsSettings = viewModel::updateTtsSettings,
                    onUpdateGeminiSettings = { apiKey, voice ->
                        viewModel.setGeminiApiKey(apiKey)
                        viewModel.setGeminiVoice(voice)
                    },
                    onResetSelections = viewModel::clearSelections,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onToggleHandsFreeMode = viewModel::toggleHandsFreeMode,
                    onLogout = { 
                        viewModel.logout()
                        navController.navigate(Screen.Home) { 
                            popUpTo(0) 
                        }
                    },
                    onNavigateToLogin = { navController.navigate(Screen.Auth) },
                    onNotificationAction = { notification ->
                        viewModel.handleNotificationAction(
                            notification = notification,
                            onNavigateToProfile = { /* Already on profile screen */ },
                            onNavigateToStats = { /* Will open stats bottom sheet on profile screen */ }
                        )
                    },
                    onNotificationDismiss = viewModel::dismissNotification,
                    onSetSimulationOverrides = viewModel::setSimulationOverrides,
                    onResetSimulationOverrides = viewModel::resetSimulationOverrides
                )
            }
            composable<Screen.Auth> {
                AuthScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.popBackStack()
                        navController.navigate(Screen.Profile())
                    }
                )
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
                handsFreeModeEnabled = appState.handsFreeModeEnabled,
                onToggleHandsFreeMode = viewModel::toggleHandsFreeMode,
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
            Box(modifier = Modifier.fillMaxSize()) {
                val isSessionMode = appState.examSettings.isExamMode || appState.customModules.isNotEmpty() || appState.examSettings.questionCount > 0
                val handsFreeState = com.example.voice.rememberHandsFreeState(
                    enabled = appState.handsFreeModeEnabled,
                    question = current,
                    selectedOption = if (isSessionMode) appState.sessionSelectedOptions[current.question] else appState.selectedOptions[current.question],
                    onOptionSelected = { viewModel.selectOption(current.question, it) },
                    onNext = viewModel::nextQuizQuestion,
                    onPrev = viewModel::previousQuizQuestion,
                    onDisableHandsFree = { viewModel.toggleHandsFreeMode(false) },
                    ttsSpeed = appState.ttsSpeed,
                    ttsPitch = appState.ttsPitch
                )
                
                McqQuizMode(
                    question = current,
                    index = index,
                    total = questions.size,
                    selectedOption = if (isSessionMode) appState.sessionSelectedOptions[current.question] else appState.selectedOptions[current.question],
                    isBookmarked = appState.bookmarkedQuestionTexts.contains(current.question),
                    isExamMode = appState.examSettings.isExamMode,
                    sessionStartTimeMs = appState.examSettings.sessionStartTimeMs,
                    timeLimitMinutes = appState.examSettings.timeLimitMinutes,
                    handsFreeModeEnabled = appState.handsFreeModeEnabled,
                    handsFreeState = handsFreeState,
                    onToggleHandsFreeMode = viewModel::toggleHandsFreeMode,
                    onToggleBookmark = { viewModel.toggleBookmark(current) },
                    onOptionSelected = { viewModel.selectOption(current.question, it) },
                    onNext = viewModel::nextQuizQuestion,
                    onPrev = viewModel::previousQuizQuestion
                )
            }
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
