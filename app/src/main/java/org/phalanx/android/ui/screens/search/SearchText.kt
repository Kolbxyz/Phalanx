package org.phalanx.android.ui.screens.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** [text] with every occurrence of [query] in bold, ignoring case. */
@Composable
internal fun highlight(text: String, query: String) = buildAnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val accent = MaterialTheme.colorScheme.primary
    var start = 0
    while (true) {
        val at = text.indexOf(needle, start, ignoreCase = true)
        if (at < 0) break
        append(text.substring(start, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
            append(text.substring(at, at + needle.length))
        }
        start = at + needle.length
    }
    append(text.substring(start))
}

/** "Sender · Conversation", or just the sender when the conversation is unknown. */
internal fun origin(sender: String, roomName: String?): String = listOfNotNull(sender, roomName).joinToString(" · ")
