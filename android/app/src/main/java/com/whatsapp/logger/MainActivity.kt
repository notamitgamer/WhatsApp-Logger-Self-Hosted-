package com.whatsapp.logger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.whatsapp.logger.di.AppContainer
import com.whatsapp.logger.ui.navigation.AppNavHost
import com.whatsapp.logger.ui.theme.Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.getInstance(application)
        setContent {
            Theme(container.settingsRepository) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AppNavHost()
                }
            }
        }

        // Initialize app - check backend URL, auth, lock screen
        // The AppNavHost will observe auth state and handle initialization
        lifecycleScope.launch {
            // AppContainer is initialized in MainApplication.onCreate()
            // AuthViewModel initialization happens via AppNavHost composition
        }
    }
}