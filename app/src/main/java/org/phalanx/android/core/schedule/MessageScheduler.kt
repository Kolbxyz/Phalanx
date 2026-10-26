package org.phalanx.android.core.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phalanx.android.core.matrix.ScheduledSender
import org.phalanx.android.data.model.ScheduledMessage
import org.phalanx.android.data.repository.ScheduleRepository

/**
 * Two triggers per message, so one lost trigger never loses the message:
 * - an alarm at the chosen time, which wakes the phone even in Doze and tries to send right away;
 * - a WorkManager job just after it, which survives reboots (alarms don't), waits for a network,
 *   and retries with backoff. Whichever runs second finds the message gone and does nothing.
 * Nothing runs between now and then: no polling, no wake-ups but these.
 */
@Singleton
internal class MessageScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ScheduledStore,
    private val sender: ScheduledSender
) : ScheduleRepository {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val work = WorkManager.getInstance(context)
    private val sending = Mutex()

    override fun scheduled(roomId: String): Flow<List<ScheduledMessage>> =
        store.messages.onStart { store.load() }.map { all -> all.filter { it.roomId == roomId } }

    override suspend fun schedule(roomId: String, text: String, replyToEventId: String?, sendAtMillis: Long) {
        val message = ScheduledMessage(UUID.randomUUID().toString(), roomId, text, sendAtMillis, replyToEventId)
        store.update { it + message }
        setAlarm(message)
        enqueue(message.id, delayMillis = sendAtMillis - System.currentTimeMillis() + FALLBACK_GRACE_MILLIS)
    }

    override suspend fun cancel(id: String) {
        store.update { all -> all.filterNot { it.id == id } }
        alarms.cancel(alarmIntent(id))
        work.cancelUniqueWork(workName(id))
    }

    override suspend fun sendNow(id: String) {
        store.update { all -> all.map { if (it.id == id) it.copy(failed = false) else it } }
        if (!deliver(id)) enqueue(id, delayMillis = 0)
    }

    override fun canScheduleExactly(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * Sends [id] if it is still waiting. True when there is nothing left to do (sent, or cancelled
     * meanwhile); false to try again later. One send at a time, so the two triggers never both send.
     */
    suspend fun deliver(id: String): Boolean = sending.withLock {
        val message = store.load().firstOrNull { it.id == id } ?: return@withLock true
        sender.send(message).fold(
            onSuccess = {
                store.update { all -> all.filterNot { it.id == id } }
                alarms.cancel(alarmIntent(id))
                work.cancelUniqueWork(workName(id))
                true
            },
            onFailure = { false }
        )
    }

    /** Hands [id] to WorkManager to send as soon as there is a network. */
    fun retrySoon(id: String) = enqueue(id, delayMillis = 0)

    suspend fun markFailed(id: String) = store.update { all ->
        all.map { if (it.id == id) it.copy(failed = true) else it }
    }

    private fun setAlarm(message: ScheduledMessage) {
        val intent = alarmIntent(message.id)
        if (canScheduleExactly()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.sendAtMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.sendAtMillis, intent)
        }
    }

    private fun enqueue(id: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<SendScheduledWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(SendScheduledWorker.KEY_ID to id))
            .build()
        work.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    // One alarm per message: the URI tells the PendingIntents apart, so cancelling one leaves the rest.
    private fun alarmIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleAlarmReceiver::class.java)
            .setData("phalanx-scheduled:$id".toUri())
            .putExtra(ScheduleAlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun workName(id: String) = "scheduled-$id"

    private companion object {
        // The job waits a little past the alarm, so on a normal day the alarm sends and the job finds nothing.
        const val FALLBACK_GRACE_MILLIS = 60_000L
        const val BACKOFF_SECONDS = 30L
    }
}
