package org.phalanx.android.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.phalanx.android.MainActivity
import org.phalanx.android.R
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.NotificationContent
import org.phalanx.android.data.model.Reminder

/**
 * A reminder the user set on a message, on a channel of its own: tapping it opens the conversation
 * on that message. It shows no more than the user lets notifications show.
 */
@Singleton
internal class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @SuppressLint("MissingPermission") // Checked just before.
    suspend fun show(reminder: Reminder) {
        if (!allowed()) return
        val shown = settings.current().notificationContent
        val title = if (shown == NotificationContent.Hidden) {
            context.getString(R.string.reminder_title_hidden)
        } else {
            context.getString(R.string.reminder_title, reminder.senderName, reminder.roomName)
        }
        val text = reminder.preview.takeIf { shown == NotificationContent.Full }
            ?: context.getString(R.string.reminder_text_hidden)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.phalanx_notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open(reminder))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(reminder.id, REMINDER_ID, notification)
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // The URI keeps each reminder's PendingIntent apart.
    private fun open(reminder: Reminder): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setData("phalanx-reminder:${reminder.id}".toUri())
            .putExtra(Notifier.EXTRA_ROOM_ID, reminder.roomId)
            .putExtra(Notifier.EXTRA_EVENT_ID, reminder.eventId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val CHANNEL = "reminders"
        const val REMINDER_ID = 20
    }
}
