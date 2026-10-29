package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.MenuAction
import org.phalanx.android.ui.components.MenuActions
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** What a long press on a message offers: a quick reaction, then the actions that apply to it. */
@Composable
internal fun MessageActionsSheet(
    message: TimelineEntry.Message,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    /** Opens the picker for any emoji beyond the quick ones. */
    onMoreReactions: () -> Unit,
    /** The room's custom emoji, offered as reactions after the quick ones. */
    emotes: List<CustomEmote>,
    /** Null hides the Emoji Kitchen reaction: Kitchen is off. */
    onReactWithMix: (() -> Unit)?,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    /** Null hides translating with the local model: AI is off, or the message is not text. */
    onTranslate: (() -> Unit)?,
    /** Null hides summing the message up: AI is off, or the message is short or not text. */
    onSummarize: (() -> Unit)?,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onEdit: () -> Unit,
    /** Null when the message's versions are not offered: not edited, or the setting is off. */
    onShowVersions: (() -> Unit)?,
    onForward: () -> Unit,
    /** Null when the message can't be reminded of yet: it is still being sent. */
    onRemind: (() -> Unit)?,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalSheet(onDismissRequest = onDismiss) {
        val live = message.body != MessageBody.Redacted
        if (live && message.eventId != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // The "+" and the mix button leave each a little less room on a narrow phone.
                val size = if (onReactWithMix != null) 40.dp else 44.dp
                QuickReactions.forEach { key ->
                    val mine = message.reactions.any { it.key == key && it.byMe }
                    Surface(
                        onClick = { onReact(key) },
                        shape = MaterialShapes.Cookie6Sided.toShape(),
                        color = if (mine) colors.secondaryContainer else colors.surfaceContainerHigh,
                        modifier = Modifier.size(size)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Surface(
                    onClick = onMoreReactions,
                    shape = MaterialShapes.Cookie6Sided.toShape(),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.size(size)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Symbols.Add, contentDescription = stringResource(R.string.reaction_more))
                    }
                }
                onReactWithMix?.let { onClick ->
                    Surface(
                        onClick = onClick,
                        shape = MaterialShapes.Puffy.toShape(),
                        color = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer,
                        modifier = Modifier.size(size)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Symbols.Sticker, contentDescription = stringResource(R.string.kitchen_react))
                        }
                    }
                }
            }
            if (emotes.isNotEmpty()) EmoteReactions(message, emotes, onReact)
        }
        val sent = live && message.eventId != null
        val actions = buildList {
            if (message.canReply) add(MenuAction(Symbols.Reply, R.string.action_reply, onReply))
            if (message.body is MessageBody.Text) add(MenuAction(Symbols.ContentCopy, R.string.action_copy, onCopy))
            if (message.canEdit && message.body is MessageBody.Text) {
                add(MenuAction(Symbols.Edit, R.string.action_edit, onEdit))
            }
            if (sent && message.body.isForwardable()) {
                add(MenuAction(Symbols.Forward, R.string.action_forward, onForward))
            }
            if (sent) {
                val pinLabel = if (pinned) R.string.action_unpin else R.string.action_pin
                add(MenuAction(Symbols.PushPin, pinLabel, onTogglePin, pinned))
                add(
                    MenuAction(
                        if (bookmarked) Symbols.BookmarkFilled else Symbols.Bookmark,
                        if (bookmarked) R.string.action_unbookmark else R.string.action_bookmark,
                        onToggleBookmark,
                        bookmarked
                    )
                )
            }
            onTranslate?.let { add(MenuAction(Symbols.Translate, R.string.action_translate, it)) }
            onSummarize?.let { add(MenuAction(Symbols.Summarize, R.string.action_summarize_message, it)) }
            if (live) onRemind?.let { add(MenuAction(Symbols.AlarmOn, R.string.action_remind, it)) }
            onShowVersions?.let { add(MenuAction(Symbols.History, R.string.action_edit_history, it)) }
        }
        val danger = buildList {
            if (!message.isOwn) add(MenuAction(Symbols.Block, R.string.action_block, onBlock))
            if (message.isOwn && live) add(MenuAction(Symbols.Delete, R.string.action_delete, onDelete))
        }
        MenuActions(actions, danger) {
            LinkActions(remember(message.body) { linksOf(message.body) }, onCopyLink, onShareLink)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun DeleteMessageDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun BlockUserDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Block, contentDescription = null) },
        title = { Text(stringResource(R.string.block_title, name)) },
        text = { Text(stringResource(R.string.block_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_block)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Messages proper; a poll, a gallery or a message that can't be read has nothing to send on. */
private fun MessageBody.isForwardable(): Boolean = when (this) {
    is MessageBody.Text, is MessageBody.Image, is MessageBody.Video, is MessageBody.Audio,
    is MessageBody.File, is MessageBody.Location -> true
    is MessageBody.Unsupported, MessageBody.Redacted, MessageBody.Undecryptable -> false
}
