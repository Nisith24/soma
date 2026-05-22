package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McqTopAppBar(
    appState: AppState,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchQueryChanged: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onViewModeToggle: () -> Unit,
    onProfileClick: () -> Unit,
    titleOverride: String? = null,
    showActions: Boolean = true
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            TitleContent(
                appState = appState,
                titleOverride = titleOverride,
                searchQuery = appState.searchQuery,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchClose = { onSearchToggle(false) }
            )
        },
        navigationIcon = {
            NavigationIcon(
                appState = appState, 
                onBack = {
                    if (appState.isSearchVisible) onSearchToggle(false)
                    else onBack()
                }, 
                hasOverride = titleOverride != null
            )
        },
        actions = {
            if (showActions) {
                ActionsContent(
                    appState = appState,
                    viewMode = appState.viewMode,
                    uiState = appState.uiState,
                    onViewModeToggle = onViewModeToggle,
                    onProfileClick = onProfileClick,
                    onSearchToggle = { onSearchToggle(true) }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun TitleContent(
    appState: AppState,
    titleOverride: String?,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchClose: () -> Unit
) {
    if (appState.isSearchVisible && titleOverride == null) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            placeholder = { Text("Search everything...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                } else {
                    IconButton(onClick = onSearchClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )
        return
    }

    if (titleOverride != null) {
        Text(titleOverride, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        return
    }

    val selectedSubject = appState.selectedSubject
    val selectedTopic = appState.selectedTopic

    when {
        selectedSubject != null && selectedTopic == null && searchQuery.isBlank() -> {
            Text(
                text = selectedSubject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        selectedTopic != null && searchQuery.isBlank() -> {
            Column {
                Text(
                    text = selectedTopic,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedSubject ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        appState.selectedSource != null && searchQuery.isBlank() -> {
            Column {
                Text(
                    text = formatSourceDisplayName(appState.selectedSource),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Source File / Origin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        else -> {
            Text(
                text = "Soma",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NavigationIcon(appState: AppState, onBack: () -> Unit, hasOverride: Boolean) {
    val shouldShowBack = hasOverride ||
            appState.selectedTopic != null ||
            appState.selectedSource != null ||
            appState.selectedSubject != null ||
            appState.searchQuery.isNotBlank()

    if (shouldShowBack) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
private fun ActionsContent(
    appState: AppState,
    viewMode: String,
    uiState: McqUiState,
    onViewModeToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchToggle: () -> Unit
) {
    val isDeepNavigation = appState.selectedTopic != null || appState.selectedSource != null || appState.searchQuery.isNotBlank()
    
    if (!appState.isSearchVisible && !isDeepNavigation && appState.selectedSubject == null) {
        IconButton(onClick = onSearchToggle) {
            Icon(
                Icons.Default.Search, 
                contentDescription = "Search",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    if (uiState is McqUiState.Success && isDeepNavigation) {
        IconButton(onClick = onViewModeToggle) {
            Icon(
                if (viewMode == "list") Icons.Default.Quiz else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (viewMode == "list") "Switch to Quiz Mode" else "Switch to List Mode"
            )
        }
    }
    IconButton(onClick = onProfileClick) {
        Icon(
            Icons.Default.AccountCircle, 
            contentDescription = "Profile",
            modifier = Modifier.size(28.dp)
        )
    }
}
