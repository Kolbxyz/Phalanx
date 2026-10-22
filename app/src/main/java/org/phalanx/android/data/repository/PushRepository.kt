package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.PushDistributor
import org.phalanx.android.data.model.PushStatus

interface PushRepository {
    val status: StateFlow<PushStatus>

    /** The gateway the user typed, or null to use the distributor's own or the public one. */
    val customGateway: Flow<String?>

    /** The distributor apps installed right now. */
    fun distributors(): List<PushDistributor>

    /** Registers with [packageName] instead of the current distributor. */
    suspend fun useDistributor(packageName: String)

    /** [url] must be HTTPS; null goes back to finding one automatically. False if [url] is not valid. */
    suspend fun setCustomGateway(url: String?): Boolean

    /** [roomId]'s conversation is on screen: removes its notifications and shows none until [stopViewing]. */
    fun startViewing(roomId: String)

    fun stopViewing(roomId: String)
}
