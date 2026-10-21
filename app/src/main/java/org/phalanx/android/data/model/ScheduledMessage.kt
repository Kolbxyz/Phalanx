package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A message waiting on this phone to be sent to [roomId] at [sendAtMillis]. */
@Immutable
@Serializable
data class ScheduledMessage(
    val id: String,
    val roomId: String,
    val text: String,
    val sendAtMillis: Long,
    val replyToEventId: String? = null,
    /** Every attempt failed; it stays listed until sent by hand or cancelled. */
    val failed: Boolean = false
)
