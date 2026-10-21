package org.phalanx.android.core.matrix

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TypingNotificationsListener

/** Who else is typing in [room], and this account's own typing notices. Lives as long as its session. */
internal class RoomTyping(private val room: Room, private val ownUserId: String, private val scope: CoroutineScope) {
    private val mutableNames = MutableStateFlow<List<String>>(emptyList())

    /** Display names of the members typing right now, this account left out. */
    val names: StateFlow<List<String>> = mutableNames

    private val handle: TaskHandle = room.subscribeToTypingNotifications(
        object : TypingNotificationsListener {
            override fun call(typingUserIds: List<String>) {
                val others = typingUserIds.filter { it != ownUserId }
                scope.launch { mutableNames.value = others.map { nameOf(it) } }
            }
        }
    )

    private var lastSent = false
    private var lastSentAt = 0L

    /**
     * Tells the room whether this account is typing. Repeats of "typing" are spaced out, since the
     * server keeps the notice alive for a few seconds by itself; a change is sent at once.
     */
    fun notice(typing: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (typing == lastSent && (!typing || now - lastSentAt < RESEND_MILLIS)) return
        lastSent = typing
        lastSentAt = now
        scope.launch {
            try {
                room.typingNotice(typing)
            } catch (_: ClientException) {
                // A lost typing notice only means the others don't see the dots; nothing to recover.
            }
        }
    }

    fun close() {
        handle.cancel()
        handle.destroy()
    }

    private suspend fun nameOf(userId: String): String = try {
        room.memberDisplayName(userId)?.takeIf { it.isNotBlank() } ?: userId
    } catch (_: ClientException) {
        userId
    }

    private companion object {
        const val RESEND_MILLIS = 3_000L
    }
}
