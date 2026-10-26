package org.phalanx.android.data.repository

import org.phalanx.android.data.model.BridgeNetwork
import org.phalanx.android.data.model.BridgeStatus

interface BridgeRepository {
    /** Every known network, probed on the homeserver: which bridges it runs, and the chats with them. */
    suspend fun statuses(): List<BridgeStatus>

    /** Opens the chat with [botId] and asks it to start signing in to [network]; the room's ID. */
    suspend fun connect(network: BridgeNetwork, botId: String): Result<String>
}
