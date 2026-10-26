package org.phalanx.android.core.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.di.ApplicationScope

/** Which messages a summary covers. */
enum class SummaryScope {
    /** The latest ones, with nothing unread. */
    Latest,

    /** Those unread when the room was opened, and the ones after. */
    Unread,

    /** Those after the previous summary's checkpoint. */
    SinceLast
}

/** A finished summary of a room, which covers the messages up to and including [eventId]. */
data class SummaryCheckpoint(
    val roomId: String,
    val eventId: String,
    val madeAtMillis: Long,
    val summary: Summary,
    val scope: SummaryScope
)

/**
 * The last few summaries of each room, so one can be shown again without running the model, and
 * the next one only reads what came after. Kept in memory only: a summary is room content and never
 * hits disk. Cleared on sign-in and sign-out, since they belong to the account.
 */
@Singleton
class SummaryCheckpoints @Inject internal constructor(
    active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) {
    private val all = MutableStateFlow<Map<String, List<SummaryCheckpoint>>>(emptyMap())

    init {
        scope.launch { active.syncService.collect { all.value = emptyMap() } }
    }

    /** The checkpoints of [roomId], oldest first. */
    fun of(roomId: String): Flow<List<SummaryCheckpoint>> = all.map { it[roomId].orEmpty() }.distinctUntilChanged()

    fun latest(roomId: String): SummaryCheckpoint? = all.value[roomId]?.lastOrNull()

    fun add(checkpoint: SummaryCheckpoint) = all.update { rooms ->
        val kept = rooms[checkpoint.roomId].orEmpty().filter { it.eventId != checkpoint.eventId }
        rooms + (checkpoint.roomId to (kept + checkpoint).takeLast(PER_ROOM))
    }

    private companion object {
        const val PER_ROOM = 5
    }
}
