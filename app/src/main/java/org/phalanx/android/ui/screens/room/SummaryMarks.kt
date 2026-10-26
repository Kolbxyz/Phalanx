package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
import org.phalanx.android.core.ai.SummaryCheckpoint

/** A room's summary checkpoints, found by the event ID of the last message each one covers. */
@Immutable
internal class SummaryMarks(checkpoints: List<SummaryCheckpoint>, val onOpen: (SummaryCheckpoint) -> Unit) {
    private val byEvent = checkpoints.associateBy { it.eventId }

    fun at(eventId: String?): SummaryCheckpoint? = eventId?.let(byEvent::get)
}
