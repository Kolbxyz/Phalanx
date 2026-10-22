package org.phalanx.android.ui.screens.room

import android.net.Uri
import androidx.compose.runtime.Stable
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.TimelineEntry

/** Everything a message row can ask of its screen, passed down as one object. */
@Stable
internal class MessageActions(
    val onOpenMedia: (TimelineEntry.Message) -> Unit,
    val onOpenFile: (media: MediaRef, filename: String, mimeType: String?) -> Unit,
    /** A local URI the player can read, or null when the download failed. */
    val resolveAudio: suspend (media: MediaRef, filename: String, mimeType: String?) -> Uri?,
    val onToggleReaction: (TimelineEntry.Message, String) -> Unit,
    val onShowReactors: (TimelineEntry.Message, String) -> Unit,
    val onReply: (TimelineEntry.Message) -> Unit,
    val onLongPress: (TimelineEntry.Message) -> Unit,
    /** A mention, an avatar or a sender's name was tapped. */
    val onOpenProfile: (userId: String) -> Unit
)
