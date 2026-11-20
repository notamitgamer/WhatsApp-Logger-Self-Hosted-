package com.whatsapp.logger.ui.screen.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.filled.ArrowForward
import androidx.compose.material3.icons.filled.CheckCircle
import androidx.compose.material3.icons.filled.Error
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material.icons.filled.Info as DefaultInfo
import androidx.compose.material.icons.filled.CheckCircle as DefaultCheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.keyboard.KeyboardType
import androidx.compose.ui.input.keyboard.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.R
import com.whatsapp.logger.viewmodel.SetupViewModel
import com.whatsapp.logger.viewmodel.SetupViewModel.SetupState
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ContentScale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onComplete: () -> Unit
) {
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(setupState) {
        if (setupState is SetupState.Success) {
            onComplete()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            painterResource(id = R.drawable.ic_logo).also { painter ->
                Image(
                    painter = painter,
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(96.dp)
                )
            }
        }

        Spacer(modifier = Modifier.padding(16.dp))

        // Title
        Text(
            text = "Welcome to WhatsApp Logger",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "Enter your Render backend URL to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.padding(32.dp))

        // Input Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { newUrl ->
                        url = newUrl
                        viewModel.updateUrl(newUrl)
                    },
                    label = { Text("Backend URL") },
                    placeholder = { Text("https://your-app.onrender.com") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Url,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = NoTrailingSlashTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = setupState is SetupState.Error,
                    supportingText = {
                        when (setupState) {
                            is SetupState.Error -> Text(setupState.message, color = MaterialTheme.colorScheme.error)
                            is SetupState.Validating -> Text("Validating...", color = MaterialTheme.colorScheme.primary)
                            else -> null
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.padding(16.dp))

                Button(
                    onClick = {
                        if (url.isNotBlank()) {
                            viewModel.validateAndSave(url)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = url.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Continue")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.padding(24.dp))

        // Info text
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InfoRow(
                icon = DefaultInfo,
                text = "Your backend URL is stored securely on this device only"
            )
            InfoRow(
                icon = DefaultCheckCircle,
                text = "No data leaves your backend server"
            )
            InfoRow(
                icon = DefaultCheckCircle,
                text = "Open source - build it yourself from Actions tab"
            )
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

class NoTrailingSlashTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.TransformedText {
        val filtered = text.text.removeTrailingSlash()
        return androidx.compose.ui.text.TransformedText(
            androidx.compose.ui.text.AnnotatedString(filtered),
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset
                override fun transformedToOriginal(offset: Int): Int = offset
            }
        )
    }

    private fun String.removeTrailingSlash(): String = if (endsWith("/")) substring(0, length - 1) else this
}