package org.phalanx.android.core.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phalanx.android.core.push.ReminderNotifier
import org.phalanx.android.data.model.Reminder
import org.phalanx.android.data.repository.ReminderRepository

/**
 * Two triggers per reminder, as for scheduled messages: an alarm at the chosen time, which wakes the
 * phone even in Doze, and a WorkManager job just after it, which survives reboots. Whichever runs
 * second finds the reminder gone and does nothing. Nothing runs in between.
 */
@Singleton
internal class Reminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ReminderStore,
    private val notifier: ReminderNotifier
) : ReminderRepository {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val work = WorkManager.getInstance(context)
    private val firing = Mutex()

    override val reminders: Flow<List<Reminder>> = store.reminders.onStart { store.load() }

    override suspend fun remind(reminder: Reminder) {
        store.update { it + reminder }
        val intent = alarmIntent(reminder.id)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.remindAtMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.remindAtMillis, intent)
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(
                (reminder.remindAtMillis - System.currentTimeMillis() + FALLBACK_GRACE_MILLIS).coerceAtLeast(0),
                TimeUnit.MILLISECONDS
            )
            .setInputData(workDataOf(ReminderWorker.KEY_ID to reminder.id))
            .build()
        work.enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    override suspend fun cancel(id: String) {
        store.update { all -> all.filterNot { it.id == id } }
        alarms.cancel(alarmIntent(id))
        work.cancelUniqueWork(workName(id))
    }

    /** Shows [id]'s notification if it is still waiting, and forgets it. One at a time, so it shows once. */
    suspend fun fire(id: String) = firing.withLock {
        val reminder = store.load().firstOrNull { it.id == id } ?: return@withLock
        notifier.show(reminder)
        cancel(id)
    }

    // One alarm per reminder: the URI tells the PendingIntents apart, so cancelling one leaves the rest.
    private fun alarmIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java)
            .setData("phalanx-reminder:$id".toUri())
            .putExtra(ReminderAlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun workName(id: String) = "reminder-$id"

    private companion object {
        // The job waits a little past the alarm, so on a normal day the alarm shows it and the job finds nothing.
        const val FALLBACK_GRACE_MILLIS = 60_000L
    }
}
