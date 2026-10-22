package org.phalanx.android.ui.screens.roomlist

import androidx.compose.runtime.Immutable
import org.phalanx.android.R
import org.phalanx.android.data.model.Bridge
import org.phalanx.android.data.model.RoomSummary

/** Where rooms come from: picked in the side panel, on top of the chip filters. */
@Immutable
sealed interface RoomSource {
    fun matches(room: RoomSummary): Boolean

    data object All : RoomSource {
        override fun matches(room: RoomSummary) = true
    }

    /** Rooms no bridge takes part in. */
    data object Matrix : RoomSource {
        override fun matches(room: RoomSummary) = room.bridge == null
    }

    data class Network(val bridge: Bridge) : RoomSource {
        override fun matches(room: RoomSummary) = room.bridge == bridge
    }
}

/** A source in the side panel, with how many rooms in view it holds. */
@Immutable
data class SourceCount(val source: RoomSource, val count: Int)

/** All, Matrix, then each network present, busiest first. */
internal fun sourcesOf(rooms: List<RoomSummary>): List<SourceCount> {
    val networks = rooms.mapNotNull { it.bridge }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { SourceCount(RoomSource.Network(it.key), it.value) }
    return listOf(
        SourceCount(RoomSource.All, rooms.size),
        SourceCount(RoomSource.Matrix, rooms.count { it.bridge == null })
    ) + networks
}

/** Brand names stay as they are in every language. */
internal val Bridge.label: Int
    get() = when (this) {
        Bridge.WhatsApp -> R.string.bridge_whatsapp
        Bridge.Signal -> R.string.bridge_signal
        Bridge.Telegram -> R.string.bridge_telegram
        Bridge.Discord -> R.string.bridge_discord
        Bridge.Instagram -> R.string.bridge_instagram
        Bridge.Messenger -> R.string.bridge_messenger
        Bridge.Meta -> R.string.bridge_meta
        Bridge.Slack -> R.string.bridge_slack
        Bridge.GoogleMessages -> R.string.bridge_gmessages
        Bridge.LinkedIn -> R.string.bridge_linkedin
        Bridge.Twitter -> R.string.bridge_twitter
        Bridge.Bluesky -> R.string.bridge_bluesky
        Bridge.IMessage -> R.string.bridge_imessage
    }

internal val RoomSource.label: Int
    get() = when (this) {
        RoomSource.All -> R.string.sources_all
        RoomSource.Matrix -> R.string.sources_matrix
        is RoomSource.Network -> bridge.label
    }
