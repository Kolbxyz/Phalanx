package org.phalanx.android.core.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phalanx.android.di.ApplicationScope

/** Fires at a reminder's time and shows it; nothing needs the network. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var reminders: Reminders

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                reminders.fire(id)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val EXTRA_ID = "id"
    }
}
