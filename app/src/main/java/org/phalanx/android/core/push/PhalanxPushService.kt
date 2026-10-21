package org.phalanx.android.core.push

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import javax.inject.Inject

/**
 * Where the distributor delivers. Nothing slow happens here: a push becomes an expedited job,
 * which may run past the few seconds the distributor keeps this service alive.
 */
@AndroidEntryPoint
class PhalanxPushService : PushService() {
    @Inject
    internal lateinit var registrar: PushRegistrar

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) = registrar.onNewEndpoint(endpoint.url)

    override fun onMessage(message: PushMessage, instance: String) {
        val event = parsePush(message.content) ?: return
        val request = OneTimeWorkRequestBuilder<PushWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(PushWorker.KEY_ROOM_ID to event.roomId, PushWorker.KEY_EVENT_ID to event.eventId))
            .build()
        // Keyed by event, so a push the gateway delivers twice is fetched once.
        WorkManager.getInstance(this).enqueueUniqueWork("push-${event.eventId}", ExistingWorkPolicy.KEEP, request)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) = registrar.onRegistrationFailed(reason)

    override fun onUnregistered(instance: String) = registrar.onUnregistered()
}
