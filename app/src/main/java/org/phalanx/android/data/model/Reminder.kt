package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A message the user asked to be reminded of at [remindAtMillis]. [preview] is what the
 * notification shows of it, kept on this phone only.
 */
@Immutable
@Serializable
data class Reminder(
    val id: String,
    val roomId: String,
    val eventId: String,
    val roomName: String,
    val senderName: String,
    val preview: String,
    val remindAtMillis: Long
)
