package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.RoomException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.BridgeNetwork
import org.phalanx.android.data.model.BridgeStatus
import org.phalanx.android.data.repository.BridgeRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.TimelineUnavailableException
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the homeserver's bridge bots by asking for their profiles, which only exist where the
 * bridge runs. Signing in itself happens in the chat with the bot (a QR code, a code or cookies),
 * so credentials go to the user's own bridge and never through this app's code.
 */
@Singleton
internal class RustBridgeRepository @Inject constructor(
    private val active: ActiveClient,
    private val api: ClientServerApi,
    private val actions: RoomActionsRepository
) : BridgeRepository {
    override suspend fun statuses(): List<BridgeStatus> = coroutineScope {
        val server = api.session().userId.substringAfter(':')
        BridgeNetwork.entries.map { network -> async { statusOf(network, server) } }.awaitAll()
    }

    private suspend fun statusOf(network: BridgeNetwork, server: String): BridgeStatus {
        val botId = network.botLocalparts.map { "@$it:$server" }.firstOrNull { exists(it) }
            ?: return BridgeStatus.Unavailable(network)
        val roomId = withContext(Dispatchers.IO) {
            try {
                active.client()?.getDmRoom(botId)?.use { it.id() }
            } catch (_: ClientException) {
                null
            }
        }
        return BridgeStatus.Available(network, botId, roomId)
    }

    private suspend fun exists(userId: String): Boolean = try {
        api.call("GET", "profile/${URLEncoder.encode(userId, "UTF-8")}").isSuccess
    } catch (_: IOException) {
        false
    }

    override suspend fun connect(network: BridgeNetwork, botId: String): Result<String> {
        val roomId = actions.directRoomWith(botId).getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
            try {
                room.use {
                    val content = buildJsonObject {
                        put("msgtype", "m.text")
                        put("body", network.loginCommand)
                    }
                    it.sendRaw("m.room.message", content.toString())
                }
                Result.success(roomId)
            } catch (e: RoomException) {
                Result.failure(e)
            } catch (e: ClientException) {
                Result.failure(e)
            }
        }
    }
}
