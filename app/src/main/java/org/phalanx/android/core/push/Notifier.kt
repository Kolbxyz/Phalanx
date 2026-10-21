package org.phalanx.android.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phalanx.android.MainActivity
import org.phalanx.android.R
import org.phalanx.android.data.model.IncomingMessage
import org.phalanx.android.ui.screens.room.summaryOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One notification per room, tagged with its ID, that grows into a conversation as messages
 * arrive. The lock screen shows only "New message" unless the user allows more in system settings.
 */
@Singleton
internal class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** MessagingStyle needs a "me"; this account's own messages are never notified, so it rarely shows. */
    private val self = Person.Builder().setName(context.getString(R.string.notification_self)).build()

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    context.getString(R.string.notification_channel_messages),
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANNEL_QUIET,
                    context.getString(R.string.notification_channel_quiet),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    CHANNEL_CHECKING,
                    context.getString(R.string.notification_channel_checking),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        )
    }

    @SuppressLint("MissingPermission") // Checked by allowed().
    fun show(message: IncomingMessage) {
        if (!allowed()) return
        val text = message.body?.let { summaryOf(it, context::getString) }
            ?: context.getString(R.string.notification_invite, message.senderName)
        val style: NotificationCompat.MessagingStyle =
            existingStyle(message.roomId) ?: NotificationCompat.MessagingStyle(self)
        style.setConversationTitle(if (message.isDirect) null else message.roomName)
        style.setGroupConversation(!message.isDirect)
        style.addMessage(text, message.timestampMillis, Person.Builder().setName(message.senderName).build())
        val notification = NotificationCompat.Builder(context, if (message.isNoisy) CHANNEL_MESSAGES else CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(message.timestampMillis)
            .setContentIntent(openRoom(message.roomId))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            .build()
        manager.notify(message.roomId, MESSAGE_ID, notification)
    }

    fun dismiss(roomId: String) = manager.cancel(roomId, MESSAGE_ID)

    fun dismissAll() = manager.cancelAll()

    /** What WorkManager shows while a push is fetched on Android 11 and older, which lack expedited jobs. */
    fun checking(): Notification = NotificationCompat.Builder(context, CHANNEL_CHECKING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.notification_checking))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .build()

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun existingStyle(roomId: String): NotificationCompat.MessagingStyle? =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.tag == roomId && it.id == MESSAGE_ID }
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification) }

    private fun publicVersion(): Notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.notification_new_message))
        .build()

    // The URI keeps each room's PendingIntent apart, so one room's tap never opens another.
    private fun openRoom(roomId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setData("phalanx-room:$roomId".toUri())
            .putExtra(EXTRA_ROOM_ID, roomId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val EXTRA_ROOM_ID = "org.phalanx.android.ROOM_ID"
        const val CHECKING_ID = 2
        private const val MESSAGE_ID = 1
        private const val CHANNEL_MESSAGES = "messages"
        private const val CHANNEL_QUIET = "messages_quiet"
        private const val CHANNEL_CHECKING = "checking"
    }
}
