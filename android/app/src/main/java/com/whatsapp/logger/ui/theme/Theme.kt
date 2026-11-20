package com.whatsapp.logger.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.icons.filled.Group
import androidx.compose.material3.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.data.prefs.AppTheme
import com.whatsapp.logger.data.prefs.SettingsRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle

@Composable
private fun lexendFontFamily(): FontFamily {
    return FontFamily(
        Font(fontResource(com.whatsapp.logger.R.font.lexend_light), FontWeight.Light),
        Font(fontResource(com.whatsapp.logger.R.font.lexend_regular), FontWeight.Normal),
        Font(fontResource(com.whatsapp.logger.R.font.lexend_medium), FontWeight.Medium),
        Font(fontResource(com.whatsapp.logger.R.font.lexend_semibold), FontWeight.SemiBold),
        Font(fontResource(com.whatsapp.logger.R.font.lexend_bold), FontWeight.Bold)
    )
}

@Composable
fun Theme(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit
) {
    val theme by settingsRepository.theme.collectAsStateWithLifecycle()
    val fontFamily = lexendFontFamily()
    val colorScheme = when (theme) {
        AppTheme.LATTE -> LightColorScheme
        AppTheme.MOCHA -> DarkColorScheme
        AppTheme.BLACK -> AmoledColorScheme
        else -> LightColorScheme
    }
    val typography = provideTypography(fontFamily)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = Shapes,
        content = content
    )
}

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF40A02B),
    onPrimary = Color(0xFFEFF1F5),
    primaryContainer = Color(0xFFA6D189),
    onPrimaryContainer = Color(0xFF1E1E2E),
    secondary = Color(0xFF179299),
    onSecondary = Color(0xFFEFF1F5),
    secondaryContainer = Color(0xFF99D1DB),
    onSecondaryContainer = Color(0xFF4C4F69),
    surface = Color(0xFFEFF1F5),
    onSurface = Color(0xFF4C4F69),
    surfaceContainerHighest = Color(0xFFBCC0CC),
    outline = Color(0xFF8C8FA1),
    error = Color(0xFFD20F39),
    onError = Color(0xFFEFF1F5),
    errorContainer = Color(0xFFEA76CB),
    onErrorContainer = Color(0xFFEFF1F5)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA6E3A1),
    onPrimary = Color(0xFF1E1E2E),
    primaryContainer = Color(0xFF40A02B),
    onPrimaryContainer = Color(0xFFA6E3A1),
    secondary = Color(0xFF94E2D5),
    onSecondary = Color(0xFF1E1E2E),
    secondaryContainer = Color(0xFF89DCEB),
    onSecondaryContainer = Color(0xFF1E1E2E),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFCDD6F4),
    surfaceContainerHighest = Color(0xFF45475A),
    outline = Color(0xFF7F849C),
    error = Color(0xFFF38BA8),
    onError = Color(0xFF1E1E2E),
    errorContainer = Color(0xFFD20F39),
    onErrorContainer = Color(0xFFF38BA8)
)

val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFFA6E3A1),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF40A02B),
    onPrimaryContainer = Color(0xFFA6E3A1),
    secondary = Color(0xFF94E2D5),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF89DCEB),
    onSecondaryContainer = Color(0xFF000000),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFCDD6F4),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF1E1E2E),
    surfaceContainerHighest = Color(0xFF313244),
    outline = Color(0xFF7F849C),
    error = Color(0xFFF38BA8),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFD20F39),
    onErrorContainer = Color(0xFFF38BA8)
)

@Composable
fun provideTypography(fontFamily: FontFamily) = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

val Shapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun ChatAvatar(
    name: String,
    isGroup: Boolean,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            androidx.compose.material3.Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}