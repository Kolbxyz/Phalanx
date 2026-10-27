package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** What the summary sheet can lead to. */
internal class SummaryActions(
    val onDismiss: () -> Unit,
    /** Closes the sheet and shows the message a point cites. */
    val onJump: (eventId: String) -> Unit
)

/** The summary sheet, wired to the room: a point jumps to its message. */
@Composable
internal fun SummaryHost(summary: SummaryViewModel, room: RoomViewModel) {
    val state by summary.state.collectAsStateWithLifecycle()
    val actions = remember(summary, room) {
        SummaryActions(
            onDismiss = summary::dismiss,
            onJump = { eventId ->
                summary.dismiss()
                room.jumpTo(eventId)
            }
        )
    }
    SummarySheet(state, actions)
}
