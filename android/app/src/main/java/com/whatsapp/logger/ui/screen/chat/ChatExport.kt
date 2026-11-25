package com.whatsapp.logger.ui.screen.chat

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.whatsapp.logger.data.repository.MessageUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the chat to a .txt file in the cache and opens the Android share sheet. */
suspend fun exportChatAsText(context: Context, chatName: String, messages: List<MessageUiModel>) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = chatName.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifEmpty { "chat" }
        val out = File(dir, "$safe.txt")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        out.bufferedWriter().use { w ->
            w.write("Chat with $chatName\n\n")
            messages.forEach { m ->
                val who = if (m.fromMe) "You" else (m.senderName ?: chatName)
                w.write("[${fmt.format(Date(m.timestamp * 1000))}] $who: ${m.text ?: "[${m.type ?: "media"}]"}\n")
            }
        }
        out
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Chat with $chatName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareMessageText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share message").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
