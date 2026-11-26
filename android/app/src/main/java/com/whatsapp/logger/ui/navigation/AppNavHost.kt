package com.whatsapp.logger.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.whatsapp.logger.di.AppContainer
import com.whatsapp.logger.ui.screen.auth.AuthScreen
import com.whatsapp.logger.ui.screen.chat.ChatScreen
import com.whatsapp.logger.ui.screen.chatlist.ChatListScreen
import com.whatsapp.logger.ui.screen.lock.LockScreen
import com.whatsapp.logger.ui.screen.logs.LogsScreen
import com.whatsapp.logger.viewmodel.LogsViewModel
import com.whatsapp.logger.ui.screen.settings.SettingsScreen
import com.whatsapp.logger.ui.screen.setup.SetupScreen
import com.whatsapp.logger.viewmodel.AuthViewModel
import com.whatsapp.logger.viewmodel.AuthViewModel.AuthState
import com.whatsapp.logger.viewmodel.ChatListViewModel
import com.whatsapp.logger.viewmodel.ChatViewModel
import com.whatsapp.logger.viewmodel.LockViewModel
import com.whatsapp.logger.viewmodel.SettingsViewModel
import com.whatsapp.logger.viewmodel.SetupViewModel

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val container = remember { AppContainer.getInstance(context.applicationContext as Application) }
    val navController = rememberNavController()

    // One AuthViewModel for the whole app, passed down explicitly.
    val authViewModel: AuthViewModel = viewModel(factory = container.viewModelFactory)
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { authViewModel.initializeApp() }

    // The auth state decides which screen is on top.
    LaunchedEffect(authState) {
        val route = when (authState) {
            is AuthState.SetupRequired -> "setup"
            is AuthState.LoginRequired -> "auth"
            is AuthState.LockRequired -> "lock"
            is AuthState.Authenticated -> "chatlist"
            else -> null
        }
        if (route != null) {
            navController.navigate(route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash",
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) + androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320)) { it / 10 } },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { it / 10 } }
    ) {
        composable("splash") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.whatsapp.logger.R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }
        }
        composable("setup") {
            val vm: SetupViewModel = viewModel(factory = container.viewModelFactory)
            SetupScreen(viewModel = vm, onComplete = { authViewModel.initializeApp() })
        }
        composable("auth") {
            AuthScreen(viewModel = authViewModel, onLoginSuccess = { authViewModel.onLoginSuccess() })
        }
        composable("lock") {
            val vm: LockViewModel = viewModel(factory = container.viewModelFactory)
            LockScreen(viewModel = vm, onUnlocked = { authViewModel.onLockUnlocked() }, onForgotPin = { authViewModel.logout() })
        }
        composable("chatlist") {
            val vm: ChatListViewModel = viewModel(factory = container.viewModelFactory)
            ChatListScreen(
                viewModel = vm,
                onChatClick = { chatId, jump ->
                    navController.navigate("chat/" + android.net.Uri.encode(chatId) + if (jump != null) "?jump=" + android.net.Uri.encode(jump) else "")
                },
                onSettingsClick = { navController.navigate("settings") },
                onOpenLogs = { navController.navigate("logs") }
            )
        }
        composable(
            route = "chat/{chatId}?jump={jump}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("jump") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            val chatId = entry.arguments?.getString("chatId").orEmpty()
            val vm: ChatViewModel = viewModel(factory = container.viewModelFactory)
            LaunchedEffect(chatId) { vm.openChat(chatId) }
            ChatScreen(viewModel = vm, onBack = { navController.popBackStack() }, initialJumpId = entry.arguments?.getString("jump"))
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel(factory = container.viewModelFactory)
            SettingsScreen(
                viewModel = vm,
                onLogout = { authViewModel.logout() },
                onChangeBackendUrl = { authViewModel.changeBackend() },
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate("logs") },
                onOpenPage = { navController.navigate("page/$it") }
            )
        }
        composable("page/{key}") { entry ->
            val key = entry.arguments?.getString("key")
            if (key == "license") {
                com.whatsapp.logger.ui.screen.about.LicenseScreen(onBack = { navController.popBackStack() })
            } else {
                com.whatsapp.logger.ui.screen.about.LiveDocScreen(
                    doc = com.whatsapp.logger.ui.screen.about.LiveDoc.fromRoute(key),
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("logs") {
            val vm: LogsViewModel = viewModel(factory = container.viewModelFactory)
            LogsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
