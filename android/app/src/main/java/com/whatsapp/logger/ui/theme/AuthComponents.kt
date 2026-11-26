package com.whatsapp.logger.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatsapp.logger.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberImeVisible(): Boolean = WindowInsets.isImeVisible

/** Contour rings in normalised units (1.0 = screen width). Built once, never per frame. */
private fun buildTopoRings(): List<Pair<Path, Float>> {
    val centers = listOf(
        Offset(0.22f, 0.30f),
        Offset(0.80f, 0.55f),
        Offset(0.45f, 0.95f),
        Offset(0.92f, 0.10f)
    )
    val rings = ArrayList<Pair<Path, Float>>()
    centers.forEachIndexed { ci, c ->
        for (ring in 1..8) {
            val base = ring * 0.065f
            val path = Path()
            val steps = 72
            for (i in 0..steps) {
                val t = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
                val wob = 1f +
                    0.18f * kotlin.math.sin(3f * t + ci + ring * 0.15f) +
                    0.10f * kotlin.math.sin(5f * t + ci * 2f) +
                    0.06f * kotlin.math.cos(7f * t - ring * 0.1f)
                val r = base * wob
                val x = c.x + r * kotlin.math.cos(t)
                val y = c.y + r * kotlin.math.sin(t) * 1.25f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            rings.add(path to (0.06f + 0.012f * ring))
        }
    }
    return rings
}

/**
 * Soft tonal topographic art in the system palette. It is static and rendered into an
 * offscreen layer, so it costs nothing while the keyboard or the sheet animates.
 */
@Composable
private fun TonalTopoArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val top = scheme.primaryContainer
    val bottom = scheme.tertiaryContainer
    val line = scheme.onPrimaryContainer
    val rings = remember { buildTopoRings() }

    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))
                val s = size.width
                val stroke = Stroke(width = 1.3.dp.toPx() / s)
                withTransform({ scale(s, s, pivot = Offset.Zero) }) {
                    rings.forEach { (path, alpha) ->
                        drawPath(path = path, color = line.copy(alpha = alpha), style = stroke)
                    }
                }
            }
    )
}

@Composable
private fun HeroBadge() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "App logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(62.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chat Log",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun HeroSlot(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120))
    ) {
        HeroBadge()
    }
}

/**
 * Sign-in / setup page. A tonal hero sits behind a rounded sheet. The sheet body scrolls and
 * the [actions] area is pinned to the bottom, so the primary button stays visible with the
 * keyboard open. When the keyboard opens the hero collapses and the sheet uses the space.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val imeVisible = rememberImeVisible()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        val screenHeight = maxHeight
        val sheetMax by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (imeVisible) screenHeight else screenHeight * 0.68f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            label = "sheetMax"
        )
        TonalTopoArt(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HeroSlot(visible = shown && !imeVisible)
            }

            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMax)
                        .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 8.dp)
                    ) {
                        Reveal(0) {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        content()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 20.dp)
                            .navigationBarsPadding()
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

/** Fades and lifts its content in after a staggered delay without changing layout size. */
@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(40L + index * 55L)
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 18.dp.toPx()
        }
    ) {
        content()
    }
}

/** Inline error banner that expands in and out. */
@Composable
fun ErrorBanner(message: String?) {
    val last = remember { mutableStateOf("") }
    if (message != null) last.value = message
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = last.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}
