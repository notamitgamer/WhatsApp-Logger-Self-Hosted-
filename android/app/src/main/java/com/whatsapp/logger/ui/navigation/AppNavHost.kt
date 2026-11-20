package com.whatsapp.logger.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.navigate
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navHost
import androidx.navigation.compose.getString
import com.whatsapp.logger.di.AppContainer
import com.whatsapp.logger.ui.screen.auth.AuthScreen
import com.whatsapp.logger.ui.screen.chat.ChatScreen
import com.whatsapp.logger.ui.screen.chatlist.ChatListScreen
import com.whatsapp.logger.ui.screen.lock.LockScreen
import com.whatsapp.logger.ui.screen.settings.SettingsScreen
import com.whatsapp.logger.ui.screen.setup.SetupScreen
import com.whatsapp.logger.viewmodel.AuthViewModel
import com.whatsapp.logger.viewmodel.AuthViewModel.AuthState
import com.whatsapp.logger.viewmodel.ChatListViewModel
import com.whatsapp.logger.viewmodel.ChatViewModel
import com.whatsapp.logger.viewmodel.LockViewModel
import com.whatsapp.logger.viewmodel.SettingsViewModel
import com.whatsapp.logger.viewmodel.SetupViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@Composable
fun AppNavHost() {
    val container = AppContainer.getInstance(LocalContext.current.applicationContext as android.app.Application)
    val navController = rememberNavController()
    val authViewModel = viewModel<AuthViewModel>(factory = container.viewModelFactory)
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    navHost(navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }

        when (authState) {
            is AuthState.SetupRequired -> {
                composable("setup") {
                    val setupViewModel = viewModel<SetupViewModel>(factory = container.viewModelFactory)
                    SetupScreen(
                        viewModel = setupViewModel,
                        onComplete = { navController.navigate("auth") { popUpTo("setup") { inclusive = true } } }
                    )
                }
            }
            is AuthState.LoginRequired -> {
                composable("auth") {
                    val authViewModel = viewModel<AuthViewModel>(factory = container.viewModelFactory)
                    AuthScreen(
                        viewModel = authViewModel,
                        onLoginSuccess = { navController.navigate("lock") { popUpTo("auth") { inclusive = true } } }
                    )
                }
            }
            is AuthState.LockRequired -> {
                composable("lock") {
                    val lockViewModel = viewModel<LockViewModel>(factory = container.viewModelFactory)
                    LockScreen(
                        viewModel = lockViewModel,
                        onUnlocked = { navController.navigate("main") { popUpTo("lock") { inclusive = true } } }
                    )
                }
            }
            is AuthState.Authenticated -> {
                navigation(startDestination = "chatlist", route = "main") {
                    composable("chatlist") {
                        val chatListViewModel = viewModel<ChatListViewModel>(factory = container.viewModelFactory)
                        ChatListScreen(
                            viewModel = chatListViewModel,
                            onChatClick = { chatId ->
                                navController.navigate("chat/$chatId")
                            },
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }
                    composable(
                        route = "chat/{chatId}",
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                    ) {
                        val chatId = getString("chatId")!!
                        val chatViewModel = viewModel<ChatViewModel>(factory = container.viewModelFactory)
                        chatViewModel.openChat(chatId)
                        ChatScreen(
                            viewModel = chatViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        val settingsViewModel = viewModel<SettingsViewModel>(factory = container.viewModelFactory)
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onLogout = {
                                authViewModel.logout()
                                navController.navigate("auth") {
                                    popUpTo("main") { inclusive = true }
                                }
                            },
                            onChangeBackendUrl = {
                                authViewModel.logout()
                                navController.navigate("setup") {
                                    popUpTo("main") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
            else -> {
                composable("splash") {
                    SplashScreen(onNavigate = { route ->
                        navController.navigate(route) { popUpTo("splash") { inclusive = true } }
                    })
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onNavigate: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        onNavigate("setup") // Will be redirected based on auth state
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "WhatsApp Logger",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}