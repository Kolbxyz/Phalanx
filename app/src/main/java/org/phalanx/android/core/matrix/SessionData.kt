package org.phalanx.android.core.matrix

import kotlinx.serialization.Serializable
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion

/** Everything needed to reopen a signed-in account. Only ever stored sealed by [org.phalanx.android.core.security.SecretBox]. */
@Serializable
internal data class SessionData(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val deviceId: String,
    val homeserverUrl: String,
    val oauthData: String?,
    /** Base64 of the raw key the SDK uses to encrypt its SQLite stores. */
    val storeKey: String,
    /** Names the per-account directories, see [SessionPaths]. */
    val directoryId: String,
    /** The last display name the server reported, shown before the network answers. */
    val displayName: String? = null
) {
    fun toSdkSession() = Session(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = userId,
        deviceId = deviceId,
        homeserverUrl = homeserverUrl,
        oauthData = oauthData,
        // Only native sliding sync exists in the SDK now, and sign-in discovers exactly that.
        slidingSyncVersion = SlidingSyncVersion.NATIVE
    )
}
