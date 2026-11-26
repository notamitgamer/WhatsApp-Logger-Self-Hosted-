package com.whatsapp.logger.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.whatsapp.logger.data.repository.SyncState

enum class StatusKind { Live, Busy, Offline, Idle }

data class SyncStatusUi(val label: String, val detail: String, val kind: StatusKind)

fun SyncState.toUi(): SyncStatusUi = when (this) {
    is SyncState.Connecting -> SyncStatusUi("Connecting...", "Opening the live connection to your server", StatusKind.Busy)
    is SyncState.Syncing -> SyncStatusUi(
        if (messagesReceived > 0) "Live \u2022 %,d synced".format(messagesReceived) else "Live",
        if (messagesReceived > 0) "Connected. $messagesReceived messages received this session" else "Connected and waiting for new messages",
        StatusKind.Live
    )
    is SyncState.Connected -> SyncStatusUi("Live", "Connected and up to date", StatusKind.Live)
    is SyncState.Error -> SyncStatusUi(
        if (retryIn > 0) "Offline \u2022 retrying" else "Offline",
        if (retryIn > 0) "$message. Retrying in ${retryIn / 1000}s" else "$message. Not retrying, try Reconnect",
        StatusKind.Offline
    )
    is SyncState.Idle -> SyncStatusUi("Not syncing", "The live connection hasn't started yet", StatusKind.Idle)
    is SyncState.Stopped -> SyncStatusUi("Sync stopped", "The live connection was stopped", StatusKind.Idle)
}

@Composable
fun StatusKind.dotColor(): Color = when (this) {
    StatusKind.Live -> MaterialTheme.colorScheme.primary
    StatusKind.Busy -> MaterialTheme.colorScheme.tertiary
    StatusKind.Offline -> MaterialTheme.colorScheme.error
    StatusKind.Idle -> MaterialTheme.colorScheme.outline
}

/** Small "dot + label" connection indicator, tappable to open the logs. */
@Composable
fun SyncStatusPill(state: SyncState, onClick: () -> Unit, modifier: Modifier = Modifier, lastSyncedAt: Long = 0L) {
    val ui = state.toUi()
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() }
    }
    val label = if (ui.kind == StatusKind.Live && lastSyncedAt > 0) "Live \u2022 ${relativeAgo(now - lastSyncedAt)}" else ui.label
    val color by animateColorAsState(ui.kind.dotColor(), tween(300), label = "dot")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val dotAlpha = if (ui.kind == StatusKind.Busy) pulseAlpha else 1f

    Row(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun relativeAgo(diffMs: Long): String {
    val m = (diffMs / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "synced just now"
        m < 60 -> "synced ${m}m ago"
        m < 1440 -> "synced ${m / 60}h ago"
        else -> "synced ${m / 1440}d ago"
    }
}
