package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.UnsupportedKind

/** One line standing in for a message: its text, its caption, or what kind of message it is. */
@Composable
internal fun summaryOf(body: MessageBody): String = when (body) {
    is MessageBody.Text -> body.text
    is MessageBody.Image -> body.caption ?: stringResource(kindLabel(body))
    is MessageBody.Video -> body.caption ?: stringResource(kindLabel(body))
    is MessageBody.File -> body.caption ?: body.filename
    is MessageBody.Location -> body.description ?: stringResource(kindLabel(body))
    else -> stringResource(kindLabel(body))
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
