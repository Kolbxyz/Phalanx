package org.phalanx.android.ui.screens.room

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.InlineConfirm
import org.phalanx.android.ui.components.MenuAction
import org.phalanx.android.ui.components.MenuActions
import org.phalanx.android.ui.theme.Symbols

/**
 * What a long press on a message offers, under the lifted message: the most used first as big
 * buttons, the rest as a list, and blocking or deleting apart at the foot, asking in place.
 */
@Composable
internal fun MessageActionsContent(
    message: TimelineEntry.Message,
    onReact: (String) -> Unit,
    /** The room's custom emoji, offered as reactions; the quick ones are over the message. */
    emotes: List<CustomEmote>,
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
    val live = message.body != MessageBody.Redacted
    val sent = live && message.eventId != null
    if (sent && emotes.isNotEmpty()) EmoteReactions(message, emotes, onReact)
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
    MenuActions(
        actions,
        extra = { LinkActions(remember(message.body) { linksOf(message.body) }, onCopyLink, onShareLink) },
        footer = {
            when {
                !message.isOwn -> InlineConfirm(
                    icon = Symbols.Block,
                    label = stringResource(R.string.action_block),
                    question = stringResource(R.string.block_title, message.senderName),
                    body = stringResource(R.string.block_body),
                    confirmLabel = stringResource(R.string.action_block),
                    onConfirm = onBlock
                )
                live -> InlineConfirm(
                    icon = Symbols.Delete,
                    label = stringResource(R.string.action_delete),
                    question = stringResource(R.string.delete_title),
                    body = stringResource(R.string.delete_body),
                    confirmLabel = stringResource(R.string.action_delete),
                    onConfirm = onDelete
                )
            }
        }
    )
}

/** Messages proper; a poll, a gallery or a message that can't be read has nothing to send on. */
private fun MessageBody.isForwardable(): Boolean = when (this) {
    is MessageBody.Text, is MessageBody.Image, is MessageBody.Video, is MessageBody.Audio,
    is MessageBody.File, is MessageBody.Location -> true
    is MessageBody.Poll, is MessageBody.Unsupported, MessageBody.Redacted, MessageBody.Undecryptable -> false
}
