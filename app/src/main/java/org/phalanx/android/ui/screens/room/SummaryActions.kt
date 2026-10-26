package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** What the summary sheet can lead to. */
internal class SummaryActions(
    val onDismiss: () -> Unit,
    /** Closes the sheet and shows the message a point cites. */
    val onJump: (eventId: String) -> Unit,
    val onSuggestReplies: () -> Unit,
    /** Closes the sheet with the reply in the composer, for the user to change or send. */
    val onUseReply: (String) -> Unit
)

/** The summary sheet, wired to the room: a point jumps to its message, a reply goes to the composer. */
@Composable
internal fun SummaryHost(summary: SummaryViewModel, room: RoomViewModel) {
    val state by summary.state.collectAsStateWithLifecycle()
    val actions = remember(summary, room) {
        SummaryActions(
            onDismiss = summary::dismiss,
            onJump = { eventId ->
                summary.dismiss()
                room.jumpTo(eventId)
            },
            onSuggestReplies = summary::suggestReplies,
            onUseReply = { reply ->
                summary.dismiss()
                room.composer.receive(reply, emptyList())
            }
        )
    }
    SummarySheet(state, actions)
}
