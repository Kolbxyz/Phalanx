package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** What kind of message a kept copy was; only its text, caption or file name is kept, never the media. */
enum class ArchivedKind { Text, Image, Video, Audio, File, Location, Sticker, Other }

/** A message deleted after this phone had received it, read back from the local copy. */
@Immutable
data class DeletedMessage(
    val eventId: String,
    val roomName: String,
    val senderName: String,
    val kind: ArchivedKind,
    /** The last text seen before the deletion; empty for media sent without a caption. */
    val text: String,
    val wasEdited: Boolean,
    val sentAtMillis: Long,
    val deletedAtMillis: Long
)
