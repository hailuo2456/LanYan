package com.lanyan.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lanyan.chat.data.model.ThemeMode
import com.lanyan.chat.ui.MainViewModel
import com.lanyan.chat.ui.screens.ChatScreen
import com.lanyan.chat.ui.screens.MemoryScreen
import com.lanyan.chat.ui.screens.SettingsScreen
import com.lanyan.chat.ui.components.SplashOverlay
import com.lanyan.chat.ui.theme.LanYanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = application as LanYanApplication
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(application.container))
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val darkTheme = when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                }
            }
            LanYanTheme(themeMode = state.settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "chat",
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(120)) },
                    ) {
                        composable("chat") {
                            ChatScreen(
                                state = state,
                                onSend = mainViewModel::sendMessage,
                                onStop = mainViewModel::stopGeneration,
                                onNewChat = mainViewModel::newConversation,
                                onSelectConversation = mainViewModel::selectConversation,
                                onRenameConversation = mainViewModel::renameConversation,
                                onDeleteConversation = mainViewModel::deleteConversation,
                                onSetConversationAvatar = mainViewModel::saveConversationAvatar,
                                onClearConversationAvatar = mainViewModel::clearConversationAvatar,
                                onSaveConversationPrompt = mainViewModel::updateConversationPrompt,
                                onSetDraftAvatar = mainViewModel::setDraftAvatar,
                                onClearDraftAvatar = mainViewModel::clearDraftAvatar,
                                onSaveDraftPrompt = mainViewModel::updateDraftPrompt,
                                onSelectDraftModel = mainViewModel::selectDraftModel,
                                onCreateGroup = mainViewModel::createGroup,
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenMemories = { navController.navigate("memories") },
                                onEnsureSuggestions = mainViewModel::ensureSuggestions,
                                onRefreshSuggestions = mainViewModel::refreshSuggestions,
                                onDismissNotice = mainViewModel::dismissNotice,
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onSave = mainViewModel::saveSettings,
                                onTest = mainViewModel::testConnection,
                                        onThemeChange = mainViewModel::setThemeMode,
                                        onGroupModeChange = mainViewModel::setGroupMode,
                                onSaveAvatar = mainViewModel::saveCroppedAvatar,
                                onRemoveAvatar = mainViewModel::removeAvatar,
                                onDismissNotice = mainViewModel::dismissNotice,
                            )
                        }
                        composable("memories") {
                            MemoryScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onToggle = mainViewModel::setMemoryEnabled,
                                onAdd = mainViewModel::addMemory,
                                onUpdate = mainViewModel::updateMemory,
                                onDelete = mainViewModel::deleteMemory,
                                onClear = mainViewModel::clearMemories,
                                onClearSummary = mainViewModel::clearSummary,
                                onDismissNotice = mainViewModel::dismissNotice,
                            )
                        }
                    }
                    if (state.showIntro) {
                        SplashOverlay(onFinished = mainViewModel::onIntroFinished)
                    }
                }
            }
        }
    }
}
