package org.phalanx.android.core.matrix

import android.os.Build
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.phalanx.android.BuildConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The homeserver's record of where to send this device's pushes. */
@Singleton
internal class MatrixPushers @Inject constructor(private val active: ActiveClient) {
    /**
     * Asks the homeserver to push to [pushKey] (the distributor's endpoint) through [gatewayUrl].
     * Event ID only: the gateway and the distributor never see a sender, a room name or content.
     */
    suspend fun set(pushKey: String, gatewayUrl: String): Result<Unit> = call {
        it.setPusher(
            identifiers = PusherIdentifiers(pushkey = pushKey, appId = BuildConfig.APPLICATION_ID),
            kind = PusherKind.Http(
                HttpPusherData(url = gatewayUrl, format = PushFormat.EVENT_ID_ONLY, defaultPayload = null)
            ),
            appDisplayName = APP_NAME,
            deviceDisplayName = Build.MODEL,
            profileTag = null,
            lang = Locale.getDefault().toLanguageTag(),
            // False: the same endpoint registered for another account on this phone is replaced.
            append = false
        )
    }

    suspend fun delete(pushKey: String): Result<Unit> = call {
        it.deletePusher(PusherIdentifiers(pushkey = pushKey, appId = BuildConfig.APPLICATION_ID))
    }

    private suspend fun call(block: suspend (Client) -> Unit): Result<Unit> {
        val client = active.client() ?: return Result.failure(IllegalStateException("Signed out"))
        return try {
            block(client)
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    private companion object {
        const val APP_NAME = "Phalanx"
    }
}
