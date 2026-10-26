package org.phalanx.android.core.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.phalanx.android.di.ApplicationScope

/**
 * Fires at a scheduled message's time. The system briefly lets the app use the network even in
 * Doze, so it tries to send straight away; if that doesn't finish in time, WorkManager takes over.
 */
@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var scheduler: MessageScheduler

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val done = withTimeoutOrNull(BUDGET_MILLIS) { scheduler.deliver(id) }
                if (done != true) scheduler.retrySoon(id)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val EXTRA_ID = "id"

        // A receiver gets about ten seconds; stop short of it so retrySoon still runs.
        const val BUDGET_MILLIS = 8_000L
    }
}
