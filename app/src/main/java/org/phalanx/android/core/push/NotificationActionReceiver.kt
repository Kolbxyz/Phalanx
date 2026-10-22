package org.phalanx.android.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.phalanx.android.core.matrix.NotificationActions
import org.phalanx.android.di.ApplicationScope
import javax.inject.Inject

/** The "Mark as read" and "Reply" buttons of a message notification. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var actions: NotificationActions

    @Inject
    internal lateinit var notifier: Notifier

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(Notifier.EXTRA_ROOM_ID) ?: return
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    Notifier.ACTION_MARK_READ -> {
                        val done = withTimeoutOrNull(BUDGET_MILLIS) { actions.markRead(roomId) }
                        if (done?.isSuccess == true) notifier.dismiss(roomId) else notifier.refresh(roomId)
                    }
                    Notifier.ACTION_REPLY -> {
                        val text = reply?.trim().orEmpty()
                        val sent = text.isNotEmpty() &&
                            withTimeoutOrNull(BUDGET_MILLIS) { actions.reply(roomId, text) }?.isSuccess == true
                        // Either way the notification is redrawn, which ends the reply's spinner.
                        notifier.refresh(roomId, repliedWith = text.takeIf { sent })
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // A receiver gets about ten seconds.
        const val BUDGET_MILLIS = 8_000L
    }
}
