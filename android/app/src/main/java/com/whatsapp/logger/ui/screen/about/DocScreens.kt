package com.whatsapp.logger.ui.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.whatsapp.logger.ui.theme.MessagesPage
import com.whatsapp.logger.ui.theme.SubPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val REPO_URL = "https://github.com/notamitgamer/whatsapp-logger-self-hosted-"
private const val RAW = "https://raw.githubusercontent.com/notamitgamer/whatsapp-logger-self-hosted-/main/"

/** Pages fetched live from the GitHub repo so they never go stale. */
enum class LiveDoc(val route: String, val title: String, val file: String) {
    Changelog("changelog", "Changelog", "CHANGELOG.md"),
    Security("security", "Security", "SECURITY.md"),
    Contributing("contributing", "Contributing", "CONTRIBUTING.md");

    companion object {
        fun fromRoute(route: String?) = entries.firstOrNull { it.route == route } ?: Changelog
    }
}

@Composable
fun LiveDocScreen(doc: LiveDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(doc, reload) {
        failed = false
        val cache = File(context.cacheDir, "doc_${doc.file}")
        val fetched = withContext(Dispatchers.IO) {
            try {
                val conn = URL(RAW + doc.file).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                cache.writeText(body)
                body
            } catch (e: Exception) {
                null
            }
        }
        if (fetched != null) {
            text = fetched; offline = false
        } else {
            val cached = withContext(Dispatchers.IO) { if (cache.exists()) cache.readText() else null }
            if (cached != null) { text = cached; offline = true } else failed = true
        }
    }

    MessagesPage(
        header = {
            SubPageHeader(title = doc.title, onBack = onBack, actions = {
                IconButton(onClick = { text = null; reload++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            })
        }
    ) {
        when {
            failed -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Couldn't load this page", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            text == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                if (offline) {
                    Text(
                        "Offline. Showing the last copy.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                MarkdownText(text.orEmpty())
            }
        }
    }
}

@Composable
fun LicenseScreen(onBack: () -> Unit) {
    MessagesPage(header = { SubPageHeader(title = "License", onBack = onBack) }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Text("Open source under the MIT License", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                MIT_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val MIT_TEXT = """MIT License

Copyright (c) 2026 AMIT DUTTA

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""

/** Small markdown renderer: headings, bullets, numbered lists, bold, code, links (shown as text). */
@Composable
private fun MarkdownText(source: String) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val codeBg = cs.surfaceContainerHighest
    fun inline(s: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("**", i) && s.indexOf("**", i + 2) > 0 -> {
                    val end = s.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                    i = end + 2
                }
                s[i] == '`' && s.indexOf('`', i + 1) > 0 -> {
                    val end = s.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                }
                s[i] == '[' && s.indexOf("](", i) > 0 && s.indexOf(')', s.indexOf("](", i)) > 0 -> {
                    val mid = s.indexOf("](", i)
                    val end = s.indexOf(')', mid)
                    withStyle(SpanStyle(color = primary)) { append(s.substring(i + 1, mid)) }
                    i = end + 1
                }
                else -> { append(s[i]); i++ }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        source.lines().forEach { raw ->
            val line = raw.trimEnd()
            val t = line.trimStart()
            when {
                t.isBlank() -> Spacer(Modifier.height(4.dp))
                t.startsWith("### ") -> Text(inline(t.drop(4)), style = MaterialTheme.typography.titleSmall, color = primary, modifier = Modifier.padding(top = 8.dp))
                t.startsWith("## ") -> Text(inline(t.drop(3)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                t.startsWith("# ") -> Text(inline(t.drop(2)), style = MaterialTheme.typography.headlineSmall)
                t.startsWith("- ") || t.startsWith("* ") -> Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(inline(t.drop(2)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                t.firstOrNull()?.isDigit() == true && t.contains(". ") && t.indexOf(". ") <= 3 -> Row {
                    Text(t.substring(0, t.indexOf(". ") + 2), style = MaterialTheme.typography.bodyMedium)
                    Text(inline(t.substring(t.indexOf(". ") + 2)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                else -> Text(inline(t), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
