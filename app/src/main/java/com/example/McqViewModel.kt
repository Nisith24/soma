package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

data class AppState(
    val uiState: McqUiState = McqUiState.Success(emptyList()),
    val searchQuery: String = "",
    val viewMode: String = "quiz",
    val quizIndex: Int = 0,
    val selectedSubject: String? = null,
    val customModules: Map<String, Set<String>> = emptyMap(),
    val selectedTopic: String? = null,
    val selectedSource: String? = null,
    val filterMode: String = "subject", // "subject" or "source"
    val selectedOptions: Map<String, String> = emptyMap(),
    val categorySummary: Map<String, Map<String, TopicStats>> = emptyMap(),
    val sourceSummary: Map<String, Int> = emptyMap(),
    val pendingTopicForDialog: String? = null,
    val examSettings: ExamSettings = ExamSettings(),
    val studyStats: StudyStats = StudyStats(),
    val allQuestions: List<McqField> = emptyList(),
    val bookmarkedQuestions: List<McqField> = emptyList(),
    val bookmarkedQuestionTexts: Set<String> = emptySet(),
    val isSearchVisible: Boolean = false,
    val dashboardSearchQuery: String = "",
    val isDashboardSearchVisible: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isFirebaseEnabled: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val displayName: String = "Nisith Praveen",
    val email: String = "nisithpraveen@gmail.com",
    val dailyGoal: Int = 10,
    val customTheme: AppThemeMode = AppThemeMode.LIGHT,
    val notifications: List<AppNotification> = emptyList(),
    val dismissedNotificationIds: Set<String> = emptySet(),
    val streakCount: Int = 0,
    val lastActiveTime: Long = 0L,
    val answersToday: Int = 0,
    // Simulation overrides
    val simStreak: Int? = null,
    val simHoursAgo: Int? = null,
    val simAnswersToday: Int? = null,
    val simPharmaAccuracy: Int? = null,
    val simLowestSubject: String? = null,
    val importProgress: Float? = null
)

class McqViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy {
        androidx.room.Room.databaseBuilder(
            application,
            AppDatabase::class.java, "mcq_database"
        ).fallbackToDestructiveMigration(true).build()
    }
    
    private val repository by lazy { com.example.data.McqRepository(application, db) }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var firebaseAuth: FirebaseAuth? = null

    init {
        loadProfile()
        observeData()
        initFirebase()
    }

    suspend fun getAiExplanation(questionText: String, options: List<String>, correctAnswer: String, topic: String?, subject: String?, existingExplanation: String?): String {
        // Try DB first
        val cached = repository.getAiExplanation(questionText)
        if (cached != null) return cached

        // Call Gemini
        val generated = com.example.gemini.fetchMedicalExplanation(questionText, options, correctAnswer, topic, subject, existingExplanation)
        
        // Cache if successful
        if (!generated.startsWith("Error") && !generated.startsWith("API Key")) {
            repository.insertAiExplanation(com.example.local.AiExplanationEntity(questionText, generated))
        }
        return generated
    }

    private fun initFirebase() {
        try {
            var initialized = false
            try {
                // Attempt standard initialization with compiled resources from google-services.json
                FirebaseApp.initializeApp(getApplication())
                initialized = true
                android.util.Log.d("Firebase", "Successfully initialized Firebase natively using google-services.json")
            } catch (e: Exception) {
                android.util.Log.d("Firebase", "Native initialization failed, trying programmatic fallback: ${e.localizedMessage}")
            }

            if (!initialized) {
                val apiKey = BuildConfig.FIREBASE_API_KEY
                val projectId = BuildConfig.FIREBASE_PROJECT_ID
                val appId = BuildConfig.FIREBASE_APP_ID

                if (apiKey.isNotBlank() && !apiKey.contains("PLACEHOLDER") &&
                    projectId.isNotBlank() && !projectId.contains("PLACEHOLDER") &&
                    appId.isNotBlank() && !appId.contains("PLACEHOLDER")) {
                    
                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setApplicationId(appId)
                        .build()
                    
                    try {
                        FirebaseApp.initializeApp(getApplication(), options)
                        initialized = true
                        android.util.Log.d("Firebase", "Successfully initialized Firebase programmatically using BuildConfig")
                    } catch (e: Exception) {
                        android.util.Log.e("Firebase", "Programmatic fallback initialization failed", e)
                    }
                }
            }

            if (initialized) {
                firebaseAuth = FirebaseAuth.getInstance()
                val currentUser = firebaseAuth?.currentUser
                
                _state.update {
                    it.copy(
                        isFirebaseEnabled = true,
                        isLoggedIn = currentUser != null,
                        displayName = currentUser?.displayName ?: it.displayName,
                        email = currentUser?.email ?: it.email
                    )
                }
                
                firebaseAuth?.addAuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user != null) {
                        val newName = user.displayName ?: _state.value.displayName
                        val newEmail = user.email ?: _state.value.email
                        repository.saveProfile(newName, newEmail)
                        _state.update {
                            it.copy(
                                isLoggedIn = true,
                                displayName = newName,
                                email = newEmail
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoggedIn = false
                            )
                        }
                    }
                }
            } else {
                android.util.Log.d("Firebase", "Firebase is not configured. Clean fallback to local simulation mode.")
                _state.update { it.copy(isFirebaseEnabled = false) }
            }
        } catch (e: Throwable) {
            android.util.Log.e("Firebase", "Failed to init Firebase", e)
            _state.update { it.copy(isFirebaseEnabled = false) }
        }
    }

    private fun getTodayDateString(): String {
        return java.time.LocalDate.now().toString()
    }

    private fun getYesterdayDateString(): String {
        return java.time.LocalDate.now().minusDays(1).toString()
    }

    private fun loadProfile() {
        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        val lastDate = repository.getLastAnsweredDate()
        
        var currentStreak = repository.getStreakCount()
        var currentAnswers = repository.getAnswersToday()
        
        if (lastDate != today && lastDate != yesterday && lastDate.isNotEmpty()) {
            currentStreak = 0
            currentAnswers = 0
            repository.saveStreakCount(0)
            repository.saveAnswersToday(0)
        } else if (lastDate != today) {
            currentAnswers = 0
            repository.saveAnswersToday(0)
        }

        val lastActive = repository.getLastActiveTime()
        val savedOptions = repository.getSavedOptions()
        
        _state.update {
            it.copy(
                displayName = repository.getDisplayName(),
                email = repository.getEmail(),
                dailyGoal = repository.getDailyGoal(),
                customTheme = repository.getCustomTheme(),
                streakCount = currentStreak,
                answersToday = currentAnswers,
                lastActiveTime = if (lastActive == 0L) System.currentTimeMillis() else lastActive,
                selectedOptions = savedOptions
            )
        }
        rebuildNotifications()
    }

    private fun observeData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteQuestionsBySource(null)
            }
            repository.getAllQuestions().collect { fields ->
                processQuestions(fields)
            }
        }

        viewModelScope.launch {
            repository.getAllBookmarks().collect { fields ->
                _state.update { 
                    it.copy(
                        bookmarkedQuestions = fields,
                        bookmarkedQuestionTexts = fields.map { b -> b.question }.toSet()
                    )
                }
                rebuildNotifications()
            }
        }
    }

    private suspend fun processQuestions(fields: List<McqField>) {
        val currentOptions = _state.value.selectedOptions
        val (categorySummary, sourceSummary) = withContext(Dispatchers.Default) {
            val catSum = calculateCategorySummary(fields, currentOptions)
            val srcSum = fields.groupBy { it.source_url ?: "Legacy / Untagged" }.mapValues { it.value.size }
            catSum to srcSum
        }
        
        _state.update {
            it.copy(
                allQuestions = fields,
                categorySummary = categorySummary,
                sourceSummary = sourceSummary,
                uiState = McqUiState.Success(fields)
            )
        }
        rebuildNotifications()
        applyFilters()
    }

    private fun formatRelativeTime(lastActiveTime: Long, simHoursAgo: Int?): String {
        if (simHoursAgo != null) {
            return when {
                simHoursAgo == 0 -> "just now"
                simHoursAgo == 1 -> "1 hour ago"
                simHoursAgo < 24 -> "$simHoursAgo hours ago"
                simHoursAgo < 48 -> "yesterday"
                else -> "${simHoursAgo / 24} days ago"
            }
        }
        if (lastActiveTime == 0L) return "a while ago"
        val diffMs = System.currentTimeMillis() - lastActiveTime
        val diffSecs = diffMs / 1000
        val diffMins = diffSecs / 60
        val diffHours = diffMins / 60
        val diffDays = diffHours / 24

        return when {
            diffSecs < 60 -> "just now"
            diffMins < 60 -> "$diffMins minutes ago"
            diffHours < 24 -> "$diffHours hours ago"
            diffDays == 1L -> "yesterday"
            diffDays < 7L -> "$diffDays days ago"
            else -> "last week"
        }
    }

    fun rebuildNotifications() {
        val current = _state.value
        val list = mutableListOf<AppNotification>()

        val streak = current.simStreak ?: current.streakCount
        val solved = current.simAnswersToday ?: current.answersToday
        val goal = current.dailyGoal
        val lastActiveTime = current.lastActiveTime
        val simHoursAgo = current.simHoursAgo

        // 1. Goal
        if (solved >= goal) {
            val msgs = listOf(
                AppNotification("goal_win1", "Goal Smashed! 🎉", "Great job hitting $solved/$goal MCQs!", "goal", NotificationUrgency.LOW, "Keep Going", "practice_subject"),
                AppNotification("goal_win2", "Elitle Status 💪", "Boom! $solved/$goal completed.", "goal", NotificationUrgency.LOW, "See Analytics", "see_stats"),
                AppNotification("goal_win3", "Target Hit 🏥", "Goal achieved ($solved/$goal)!", "goal", NotificationUrgency.LOW, "Review Bookmarks", "view_bookmarks")
            )
            list.add(msgs[(solved + goal) % 3])
        } else {
            val msgs = listOf(
                AppNotification("goal_slack1", "Stethoscopes are Sighing 🩺", "Only $solved/$goal MCQs today.", "goal", NotificationUrgency.MEDIUM, "Practice", "practice_subject"),
                AppNotification("goal_slack2", "Snail Pace 🐌", "At $solved/$goal today, pick up speed!", "goal", NotificationUrgency.HIGH, "Let's Go", "practice_subject"),
                AppNotification("goal_slack3", "Medical Tuition 💸", "Just $solved/$goal? Start studying!", "goal", NotificationUrgency.HIGH, "Solve 1", "practice_subject")
            )
            list.add(msgs[(solved + goal) % 3])
        }

        // 2. Streak
        if (streak == 0) {
            list.add(AppNotification("streak_0", "Streak Flatlined! 📉", "Momentum lost. Start again!", "streak", NotificationUrgency.HIGH, "Revive", "practice_subject"))
        } else if (streak in 1..2) {
            list.add(AppNotification("streak_small", "Stamina Check 🏃", "Building a $streak-day streak!", "streak", NotificationUrgency.MEDIUM, "Build It", "practice_subject"))
        } else {
            list.add(AppNotification("streak_large", "Legendary Streak! 🔥", "$streak days on fire!", "streak", NotificationUrgency.LOW, "View Stats", "see_stats"))
        }

        // 3. Time intervals
        val hours = simHoursAgo ?: ((System.currentTimeMillis() - lastActiveTime) / 3600000).toInt()
        val rTime = formatRelativeTime(lastActiveTime, simHoursAgo)
        when {
            hours <= 1 -> list.add(AppNotification("time_1", "Warmed Up 🚀", "Last active: $rTime. Resume!", "last_active", NotificationUrgency.LOW, "Resume", "practice_subject"))
            hours in 2..18 -> list.add(AppNotification("time_2", "Distracted? 🤨", "Last active: $rTime. Return!", "last_active", NotificationUrgency.MEDIUM, "Come Back", "practice_subject"))
            else -> list.add(AppNotification("time_3", "Dusty Memory 📚", "Last active: $rTime. Study!", "last_active", NotificationUrgency.HIGH, "Study Now", "practice_subject"))
        }

        // 4. Subject 
        if (current.allQuestions.isNotEmpty()) {
            val subMap = mutableMapOf<String, Pair<Int, Int>>()
            current.allQuestions.forEach { q ->
                current.selectedOptions[q.question]?.let { sel ->
                    val s = q.subject ?: "Legacy"
                    val (oldC, oldT) = subMap[s] ?: Pair(0, 0)
                    subMap[s] = Pair(oldC + (if(sel.startsWith(q.correct_answer, true)) 1 else 0), oldT + 1)
                }
            }
            val lowSub = current.simLowestSubject?.let { Triple(it, current.simPharmaAccuracy ?: 35, 5) }
                ?: subMap.map { Triple(it.key, it.value.first * 100 / it.value.second, it.value.second) }.minByOrNull { it.second }
            if (lowSub != null && lowSub.second < 65) {
                list.add(AppNotification("subj_weak", "Weakness: ${lowSub.first}", "Accuracy at ${lowSub.second}%.", "subject", NotificationUrgency.HIGH, "Practice", "practice_subject", lowSub.first))
            }
        } else {
            list.add(AppNotification("no_q", "Empty State", "Import JSONs to start.", "general", NotificationUrgency.HIGH, "Import", "set_goal"))
        }

        val bookmarksCount = current.bookmarkedQuestionTexts.size
        if (bookmarksCount > 2) list.add(AppNotification("bmarks", "Hoarding?", "You have $bookmarksCount bookmarks.", "general", NotificationUrgency.MEDIUM, "Review", "view_bookmarks"))

        val finalNotifications = list.filter { it.id !in current.dismissedNotificationIds }
        _state.update { it.copy(notifications = finalNotifications) }
    }

    fun dismissNotification(id: String) {
        _state.update { it.copy(dismissedNotificationIds = it.dismissedNotificationIds + id) }
        rebuildNotifications()
    }

    fun handleNotificationAction(
        notification: AppNotification, 
        onNavigateToProfile: () -> Unit, 
        onNavigateToStats: () -> Unit
    ) {
        when (notification.actionType) {
            "practice_subject" -> {
                val targetSub = notification.actionParam ?: _state.value.categorySummary.keys.shuffled().firstOrNull()
                if (targetSub != null) {
                    selectCategory(targetSub)
                }
            }
            "view_bookmarks" -> {
                onNavigateToProfile()
            }
            "see_stats" -> {
                onNavigateToStats()
            }
            "set_goal" -> {
                onNavigateToProfile()
            }
            "reset_progress" -> {
                clearSelections()
            }
        }
        dismissNotification(notification.id)
    }

    fun setSimulationOverrides(
        streak: Int?, 
        hoursAgo: Int?, 
        answersToday: Int?, 
        pharmaAcc: Int?,
        lowestSub: String? = null
    ) {
        _state.update {
            it.copy(
                simStreak = streak,
                simHoursAgo = hoursAgo,
                simAnswersToday = answersToday,
                simPharmaAccuracy = pharmaAcc,
                simLowestSubject = lowestSub
            )
        }
        rebuildNotifications()
    }

    fun resetSimulationOverrides() {
        _state.update {
            it.copy(
                simStreak = null,
                simHoursAgo = null,
                simAnswersToday = null,
                simPharmaAccuracy = null,
                simLowestSubject = null,
                dismissedNotificationIds = emptySet()
            )
        }
        rebuildNotifications()
    }

    private fun trackAnswerActivity() {
        val today = getTodayDateString()
        val lastDate = repository.getLastAnsweredDate()
        val yesterday = getYesterdayDateString()

        var currentAnswers = repository.getAnswersToday()
        var currentStreak = repository.getStreakCount()

        if (lastDate != today) {
            if (lastDate == yesterday) {
                currentStreak += 1
            } else if (lastDate.isEmpty()) {
                currentStreak = 1
            } else {
                currentStreak = 1
            }
            currentAnswers = 1
            repository.saveLastAnsweredDate(today)
            repository.saveStreakCount(currentStreak)
            repository.saveAnswersToday(currentAnswers)
        } else {
            currentAnswers += 1
            repository.saveAnswersToday(currentAnswers)
        }

        repository.saveLastActiveTime(System.currentTimeMillis())

        _state.update {
            it.copy(
                answersToday = currentAnswers,
                streakCount = currentStreak,
                lastActiveTime = System.currentTimeMillis()
            )
        }
        rebuildNotifications()
    }

    fun updateProfile(name: String, email: String) {
        repository.saveProfile(name, email)
        _state.update { it.copy(displayName = name, email = email) }
        
        try {
            val auth = firebaseAuth
            if (auth != null && state.value.isFirebaseEnabled) {
                val user = auth.currentUser
                if (user != null) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user.updateProfile(profileUpdates)
                    if (email != user.email && email.isNotBlank()) {
                        try {
                            user.verifyBeforeUpdateEmail(email)
                        } catch (e: Throwable) {
                            android.util.Log.e("FirebaseProfile", "Failed to update email synchronously", e)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("FirebaseProfile", "Failed to process Firebase profile updates", e)
        }
        rebuildNotifications()
    }

    fun setDailyGoal(goal: Int) {
        repository.saveDailyGoal(goal)
        _state.update { it.copy(dailyGoal = goal) }
        rebuildNotifications()
    }

    fun setThemeMode(theme: AppThemeMode) {
        repository.saveTheme(theme)
        _state.update { it.copy(customTheme = theme) }
    }

    fun googleWebClientId(): String {
        val buildConfigId = try { BuildConfig.GOOGLE_WEB_CLIENT_ID } catch (e: Throwable) { "" }
        if (buildConfigId.isNotBlank() && !buildConfigId.contains("PLACEHOLDER")) {
            return buildConfigId
        }
        return "823802113339-v124su9537t83fe9voptv93lh3aqgdpr.apps.googleusercontent.com"
    }

    fun signUp(emailInput: String, passwordInput: String, nameInput: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null || !state.value.isFirebaseEnabled) {
            // Local Simulation fallback
            viewModelScope.launch {
                _state.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
                delay(1200)
                _state.update { 
                    it.copy(
                        isAuthLoading = false,
                        isLoggedIn = true,
                        displayName = nameInput,
                        email = emailInput
                    )
                }
                onResult(true, null)
            }
            return
        }

        _state.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        auth.createUserWithEmailAndPassword(emailInput, passwordInput)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(nameInput)
                        .build()
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                        repository.saveProfile(nameInput, emailInput)
                        _state.update {
                            it.copy(
                                isAuthLoading = false,
                                isLoggedIn = true,
                                displayName = nameInput,
                                email = emailInput
                            )
                        }
                        onResult(true, null)
                    } ?: run {
                        repository.saveProfile(nameInput, emailInput)
                        _state.update {
                            it.copy(
                                isAuthLoading = false,
                                isLoggedIn = true,
                                displayName = nameInput,
                                email = emailInput
                            )
                        }
                        onResult(true, null)
                    }
                } else {
                    val error = task.exception?.localizedMessage ?: "Registration failed."
                    _state.update {
                        it.copy(
                            isAuthLoading = false,
                            authErrorMessage = error
                        )
                    }
                    onResult(false, error)
                }
            }
    }

    fun signIn(emailInput: String, passwordInput: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null || !state.value.isFirebaseEnabled) {
            // Local Simulation fallback
            viewModelScope.launch {
                _state.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
                delay(1200)
                _state.update { 
                    it.copy(
                        isAuthLoading = false,
                        isLoggedIn = true,
                        displayName = "Mock Nisith Praveen",
                        email = emailInput
                    )
                }
                onResult(true, null)
            }
            return
        }

        _state.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        auth.signInWithEmailAndPassword(emailInput, passwordInput)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    val newName = user?.displayName ?: "User"
                    val newEmail = user?.email ?: emailInput
                    repository.saveProfile(newName, newEmail)
                    _state.update {
                        it.copy(
                            isAuthLoading = false,
                            isLoggedIn = true,
                            displayName = newName,
                            email = newEmail
                        )
                    }
                    onResult(true, null)
                } else {
                    val error = task.exception?.localizedMessage ?: "Login failed."
                    _state.update {
                        it.copy(
                            isAuthLoading = false,
                            authErrorMessage = error
                        )
                    }
                    onResult(false, error)
                }
            }
    }

    fun signInWithGoogle(idTokenInput: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null || !state.value.isFirebaseEnabled || idTokenInput.isBlank()) {
            // Local simulation fallback
            viewModelScope.launch {
                _state.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
                delay(1200)
                _state.update { 
                    it.copy(
                        isAuthLoading = false,
                        isLoggedIn = true,
                        displayName = "Mock Google User",
                        email = "google-user@example.com"
                    )
                }
                onResult(true, null)
            }
            return
        }

        _state.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        try {
            val credential = GoogleAuthProvider.getCredential(idTokenInput, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        val newName = user?.displayName ?: "Google User"
                        val newEmail = user?.email ?: ""
                        repository.saveProfile(newName, newEmail)
                        
                        _state.update {
                            it.copy(
                                isAuthLoading = false,
                                isLoggedIn = true,
                                displayName = newName,
                                email = newEmail
                            )
                        }
                        onResult(true, null)
                    } else {
                        val error = task.exception?.localizedMessage ?: "Google Sign-In failed."
                        _state.update {
                            it.copy(
                                isAuthLoading = false,
                                authErrorMessage = error
                            )
                        }
                        onResult(false, error)
                    }
                }
        } catch (e: Throwable) {
            val error = e.localizedMessage ?: "Invalid Google credentials format."
            _state.update {
                it.copy(
                    isAuthLoading = false,
                    authErrorMessage = error
                )
            }
            onResult(false, error)
        }
    }

    fun login() {
        _state.update { it.copy(isLoggedIn = true) }
    }

    fun logout() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Throwable) {
            android.util.Log.e("FirebaseLogout", "Failed to sign out from Firebase", e)
        }
        _state.update { it.copy(isLoggedIn = false) }
    }

    fun selectOption(questionText: String, option: String) {
        // Track the response times, streak logging, etc.
        trackAnswerActivity()

        _state.update { currentState ->
            val newOptions = currentState.selectedOptions + (questionText to option)
            repository.saveSelectedOptions(newOptions)
            val stats = computeStats(currentState.allQuestions, newOptions)
            val catSum = calculateCategorySummary(currentState.allQuestions, newOptions)

            currentState.copy(
                selectedOptions = newOptions, 
                studyStats = stats,
                categorySummary = catSum
            )
        }
        rebuildNotifications()
    }

    private fun calculateCategorySummary(
        questions: List<McqField>, 
        options: Map<String, String>
    ): Map<String, Map<String, TopicStats>> {
        return questions.groupBy { it.subject ?: "General" }
            .mapValues { entry -> 
                entry.value.groupBy { it.topic ?: "Untracked" }.mapValues { topicEntry ->
                    TopicStats(
                        total = topicEntry.value.size,
                        finished = topicEntry.value.count { options.containsKey(it.question) }
                    )
                }
            }
    }

    private fun computeStats(all: List<McqField>, selected: Map<String, String>): StudyStats {
        if (selected.isEmpty()) return StudyStats()
        val correct = all.count { q ->
            selected[q.question]?.let { isCorrect(q, it) } == true
        }
        val percent = (correct * 100) / selected.size
        return StudyStats(selected.size, correct, percent)
    }

    private fun isCorrect(question: McqField, selectedOption: String): Boolean {
        val corr = question.correct_answer
        if (corr.isBlank()) return false
        return selectedOption.startsWith("$corr.", ignoreCase = true)
            || selectedOption.startsWith("$corr)", ignoreCase = true)
            || selectedOption.startsWith("$corr ", ignoreCase = true)
            || selectedOption.equals(corr, ignoreCase = true)
    }

    fun toggleBookmark(question: McqField) {
        viewModelScope.launch {
            val isBookmarked = _state.value.bookmarkedQuestionTexts.contains(question.question)
            repository.toggleBookmark(question, isBookmarked)
        }
    }

    fun clearSelections() {
        repository.saveSelectedOptions(emptyMap())
        _state.update { it.copy(selectedOptions = emptyMap(), studyStats = StudyStats(), categorySummary = calculateCategorySummary(it.allQuestions, emptyMap())) }
    }

    fun deleteQuestionsBySource(sourceName: String) {
        viewModelScope.launch {
            try {
                if (sourceName == "Legacy / Untagged") {
                    repository.deleteQuestionsBySource(null)
                } else {
                    repository.deleteQuestionsBySource(sourceName)
                }
            } catch (e: Exception) {
                android.util.Log.e("McqViewModel", "Failed to delete source: $sourceName", e)
            }
        }
    }

    private fun getFileSize(context: android.content.Context, uri: android.net.Uri): Long {
        var size = -1L
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (index >= 0) size = cursor.getLong(index)
                    }
                }
            } catch (e: java.lang.Exception) {}
        }
        if (size <= 0) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    size = fd.length
                }
            } catch (e: java.lang.Exception) {}
        }
        return size
    }

    class ProgressInputStream(
        private val delegate: java.io.InputStream,
        private val totalBytes: Long,
        private val onProgress: (Float) -> Unit
    ) : java.io.InputStream() {
        private var bytesRead = 0L
        private var lastPercent = -1

        override fun read(): Int {
            val result = delegate.read()
            if (result != -1) {
                bytesRead++
                notifyProgress()
            }
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val result = delegate.read(b, off, len)
            if (result != -1) {
                bytesRead += result
                notifyProgress()
            }
            return result
        }

        private fun notifyProgress() {
            if (totalBytes > 0) {
                val progress = bytesRead.toFloat() / totalBytes
                val percent = (progress * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(progress)
                }
            }
        }

        override fun close() {
            delegate.close()
        }
    }

    fun loadMultipleJsonsFromUris(context: android.content.Context, uris: List<android.net.Uri>) {
        _state.update { it.copy(uiState = McqUiState.Loading, importProgress = 0.0f) }
        viewModelScope.launch {
            try {
                val totalBytes = withContext(Dispatchers.IO) {
                    uris.sumOf { getFileSize(context, it).coerceAtLeast(0L) }
                }
                
                var accumulatedBytesRead = 0L

                val allFields = withContext(Dispatchers.IO) {
                    val mediaDir = java.io.File(context.filesDir, "local_media")
                    val streams = uris.mapNotNull { uri ->
                        try {
                            val originalStream = context.contentResolver.openInputStream(uri)
                            val fileSize = getFileSize(context, uri).coerceAtLeast(0L)
                            val name = FileUtils.getFileName(context, uri) ?: "imported_questions"
                            if (originalStream != null) {
                                val trackingStream = ProgressInputStream(originalStream, fileSize) { fileProgress ->
                                    val currentFileRead = (fileProgress * fileSize).toLong()
                                    val overallReadBytes = accumulatedBytesRead + currentFileRead
                                    val readRatio = if (totalBytes > 0) overallReadBytes.toFloat() / totalBytes else 1.0f
                                    val progressVal = readRatio * 0.45f
                                    _state.update { it.copy(importProgress = progressVal.coerceIn(0.0f, 0.45f)) }
                                }
                                val finalStream = object : java.io.FilterInputStream(trackingStream) {
                                    override fun close() {
                                        super.close()
                                        accumulatedBytesRead += fileSize
                                    }
                                }
                                name to finalStream
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                    JsonStreamParser.parseMultiple(streams, mediaDir)
                }

                if (allFields.isEmpty()) {
                    _state.update { it.copy(uiState = McqUiState.Error("No valid questions found in selected files."), importProgress = null) }
                } else {
                    _state.update { it.copy(importProgress = 0.50f) }
                    val chunks = allFields.chunked(2500)
                    val totalChunks = chunks.size
                    chunks.forEachIndexed { index, chunk ->
                        withContext(Dispatchers.IO) {
                            repository.insertQuestions(chunk)
                        }
                        val dbProgress = 0.50f + ((index + 1).toFloat() / totalChunks) * 0.50f
                        _state.update { it.copy(importProgress = dbProgress.coerceIn(0.50f, 1.0f)) }
                    }
                    _state.update { it.copy(uiState = McqUiState.Success(allFields), importProgress = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(uiState = McqUiState.Error("Import fail: ${e.localizedMessage}"), importProgress = null) }
            }
        }
    }

    fun loadJson(jsonString: String, sourceName: String? = null) {
        // Fallback or deprecated if needed somewhere else, but better to support stream
        _state.update { it.copy(uiState = McqUiState.Loading) }
        viewModelScope.launch {
            try {
                java.io.ByteArrayInputStream(jsonString.toByteArray(Charsets.UTF_8)).use { stream ->
                    val allFields = withContext(Dispatchers.IO) {
                        JsonStreamParser.parseMultiple(listOf((sourceName ?: "Imported") to stream))
                    }
                    repository.insertQuestions(allFields)
                }
            } catch (e: Exception) {
                _state.update { it.copy(uiState = McqUiState.Error("Import fail: ${e.localizedMessage}")) }
            }
        }
    }

    private fun fieldsWithSource(fields: List<McqField>, sourceName: String?): List<McqField> {
        if (sourceName == null) return fields
        return fields.map {
            if (it.source_url.isNullOrBlank()) it.copy(source_url = sourceName) else it
        }
    }

    private var filterJob: Job? = null
    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(200)
            applyFilters()
        }
    }

    fun setViewMode(mode: String) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun setSearchVisible(visible: Boolean) {
        _state.update { it.copy(isSearchVisible = visible) }
        if (!visible) onSearchQueryChanged("")
    }

    fun setDashboardSearchVisible(visible: Boolean) {
        _state.update { it.copy(isDashboardSearchVisible = visible) }
        if (!visible) onDashboardSearchQueryChanged("")
    }

    fun onDashboardSearchQueryChanged(query: String) {
        _state.update { it.copy(dashboardSearchQuery = query) }
    }

    fun selectCategory(subject: String?, topic: String? = null) {
        _state.update { it.copy(selectedSubject = subject, selectedTopic = topic, selectedSource = null, customModules = emptyMap()) }
        applyFilters()
    }

    fun selectCategoryInSource(subject: String?, topic: String? = null, source: String?) {
        _state.update { it.copy(selectedSubject = subject, selectedTopic = topic, selectedSource = source, customModules = emptyMap()) }
        applyFilters()
    }

    fun showTopicDialog(topic: String?) {
        _state.update { it.copy(pendingTopicForDialog = topic) }
    }

    fun startTopicSession(subject: String?, topic: String?, settings: ExamSettings, keepSource: Boolean = false) {
        val updatedSettings = settings.copy(sessionStartTimeMs = System.currentTimeMillis())
        _state.update { 
            it.copy(
                selectedSubject = subject, 
                selectedTopic = topic, 
                selectedSource = if (keepSource) it.selectedSource else null,
                customModules = emptyMap(),
                pendingTopicForDialog = null,
                examSettings = updatedSettings,
                quizIndex = 0
            ) 
        }
        applyFilters()
    }

    fun startCustomSession(modules: Map<String, Set<String>>, settings: ExamSettings) {
        val updatedSettings = settings.copy(sessionStartTimeMs = System.currentTimeMillis())
        _state.update { 
            it.copy(
                selectedSubject = null, 
                selectedTopic = null, 
                selectedSource = null,
                customModules = modules,
                examSettings = updatedSettings,
                quizIndex = 0
            ) 
        }
        applyFilters()
    }

    fun selectSource(source: String?) {
        _state.update { it.copy(selectedSource = source, selectedSubject = null, selectedTopic = null, customModules = emptyMap()) }
        applyFilters()
    }

    fun setFilterMode(mode: String) {
        _state.update { it.copy(filterMode = mode) }
    }

    fun resetNavigation() {
        _state.update { it.copy(selectedSubject = null, selectedTopic = null, selectedSource = null, customModules = emptyMap(), quizIndex = 0) }
        applyFilters()
    }

    fun nextQuizQuestion() {
        val max = (_state.value.uiState as? McqUiState.Success)?.questions?.size ?: 0
        if (_state.value.quizIndex < max) {
            _state.update { it.copy(quizIndex = it.quizIndex + 1) }
        }
    }

    fun previousQuizQuestion() {
        if (_state.value.quizIndex > 0) {
            _state.update { it.copy(quizIndex = it.quizIndex - 1) }
        }
    }

    private fun applyFilters() {
        viewModelScope.launch(Dispatchers.Default) {
            val current = _state.value
            val currentOptions = current.selectedOptions
            val filtered = current.allQuestions.filter { q ->
                val matchesQuery = current.searchQuery.isBlank() || 
                    q.question.contains(current.searchQuery, ignoreCase = true) ||
                    q.subject?.contains(current.searchQuery, ignoreCase = true) == true ||
                    q.topic?.contains(current.searchQuery, ignoreCase = true) == true
                
                val matchesSubject = when {
                    current.customModules.isNotEmpty() -> {
                        val subName = q.subject ?: "General"
                        val topName = q.topic ?: "Untracked"
                        val topicsForSub = current.customModules[subName]
                        topicsForSub != null && topicsForSub.contains(topName)
                    }
                    current.selectedSubject != null -> q.subject == current.selectedSubject
                    else -> true
                }
                val matchesTopic = current.selectedTopic == null || q.topic == current.selectedTopic
                val matchesSource = current.selectedSource == null || 
                    (current.selectedSource == "Legacy / Untagged" && q.source_url.isNullOrBlank()) ||
                    (q.source_url == current.selectedSource)
                
                matchesQuery && matchesSubject && matchesTopic && matchesSource
            }

            val finalFiltered = if ((current.selectedTopic != null || current.customModules.isNotEmpty()) && current.examSettings.isExamMode) {
                val count = current.examSettings.questionCount
                if (count > 0 && count < filtered.size) {
                    filtered.shuffled().take(count)
                } else {
                    filtered.shuffled()
                }
            } else {
                filtered
            }

            val catSum = calculateCategorySummary(filtered, currentOptions)
            val srcSum = filtered.groupBy { it.source_url ?: "Legacy / Untagged" }.mapValues { it.value.size }

            _state.update { 
                it.copy(
                    uiState = McqUiState.Success(finalFiltered),
                    categorySummary = catSum,
                    sourceSummary = srcSum,
                    quizIndex = 0
                ) 
            }
        }
    }
}

