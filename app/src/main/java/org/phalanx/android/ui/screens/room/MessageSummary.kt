package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.UnsupportedKind

/** One line standing in for a message: its text, its caption, or what kind of message it is. */
@Composable
internal fun summaryOf(body: MessageBody): String = summaryOf(body, LocalResources.current::getString)

/** [summaryOf] outside composition, such as in a notification; [label] resolves a string resource. */
internal fun summaryOf(body: MessageBody, label: (Int) -> String): String = when (body) {
    is MessageBody.Text -> body.text
    is MessageBody.Image -> body.caption?.text ?: label(kindLabel(body))
    is MessageBody.Video -> body.caption?.text ?: label(kindLabel(body))
    is MessageBody.File -> body.caption?.text ?: body.filename
    is MessageBody.Location -> body.description ?: label(kindLabel(body))
    else -> label(kindLabel(body))
}

@StringRes
internal fun kindLabel(body: MessageBody): Int = when (body) {
    is MessageBody.Text -> R.string.timeline_kind_other
    is MessageBody.Image -> if (body.isSticker) R.string.timeline_kind_sticker else R.string.timeline_kind_image
    is MessageBody.Video -> R.string.timeline_kind_video
    is MessageBody.Audio -> if (body.isVoice) R.string.timeline_kind_voice else R.string.timeline_kind_audio
    is MessageBody.File -> R.string.timeline_kind_file
    is MessageBody.Location -> R.string.timeline_kind_location
    MessageBody.Redacted -> R.string.timeline_redacted
    MessageBody.Undecryptable -> R.string.timeline_undecryptable
    is MessageBody.Unsupported -> when (body.kind) {
        UnsupportedKind.Poll -> R.string.timeline_kind_poll
        UnsupportedKind.Gallery -> R.string.timeline_kind_gallery
        UnsupportedKind.LiveLocation -> R.string.timeline_kind_live_location
        UnsupportedKind.Other -> R.string.timeline_kind_other
    }
}
