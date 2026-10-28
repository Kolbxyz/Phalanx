package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A message the user saved for later. Only [roomId] and [eventId] travel with the account; the
 * names and [preview] quote the message, so they stay on this phone and are blank for a bookmark
 * made on another device.
 */
@Immutable
@Serializable
data class Bookmark(
    val roomId: String,
    val eventId: String,
    val roomName: String,
    val senderName: String,
    val preview: String,
    val savedAtMillis: Long
)
