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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import org.phalanx.android.MainActivity
import org.phalanx.android.R
import org.phalanx.android.data.model.IncomingMessage
import org.phalanx.android.data.model.NotificationContent

/**
 * One notification per room, tagged with its ID, that grows into a conversation as messages
 * arrive. The lock screen shows only "New message" unless the user allows more in system settings.
 */
@Singleton
internal class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** MessagingStyle needs a "me"; this account's own messages are never notified, so it rarely shows. */
    private val self = Person.Builder().setName(context.getString(R.string.notification_self)).build()

    /** The room whose conversation is on screen; its messages are already in front of the user. */
    private val viewing = AtomicReference<String?>(null)

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
        if (!allowed() || message.roomId == viewing.get()) return
        val text = context.notificationText(message)
        val hidden = message.shown == NotificationContent.Hidden
        val style: NotificationCompat.MessagingStyle =
            existingStyle(message.roomId) ?: NotificationCompat.MessagingStyle(self)
        style.setConversationTitle(notificationRoomName(message))
        style.setGroupConversation(!message.isDirect && !hidden)
        val sender = Person.Builder()
            .setName(context.notificationSender(message))
            .setIcon(message.senderAvatar?.let(IconCompat::createWithBitmap))
            .build()
        val line = NotificationCompat.MessagingStyle.Message(text, message.timestampMillis, sender)
        message.picture?.let { line.setData(it.mimeType, NotificationPictureProvider.publish(context, it)) }
        style.addMessage(line)
        // A shortcut is named after the person or room, which hidden notifications keep to themselves.
        if (!hidden) publishShortcut(message, sender)
        val notification = NotificationCompat.Builder(context, if (message.isNoisy) CHANNEL_MESSAGES else CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setColor(context.getColor(R.color.phalanx_notification_accent))
            .apply {
                if (!hidden) setShortcutId(message.roomId).setLocusId(LocusIdCompat(message.roomId))
            }
            .setLargeIcon(message.senderAvatar.takeIf { message.isDirect })
            .setGroup(GROUP_MESSAGES)
            .setOnlyAlertOnce(true)
            .apply { if (message.body != null) addActions(message.roomId) }
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(message.timestampMillis)
            .setContentIntent(openRoom(message.roomId))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            .build()
        manager.notify(message.roomId, MESSAGE_ID, notification)
        manager.notify(SUMMARY_ID, summary())
    }

    // Without a long-lived shortcut, Android 11+ files the notification outside the Conversations section.
    private fun publishShortcut(message: IncomingMessage, sender: Person) {
        val shortcut = ShortcutInfoCompat.Builder(context, message.roomId)
            .setShortLabel(if (message.isDirect) message.senderName else message.roomName)
            .setLongLived(true)
            .setIsConversation()
            .setLocusId(LocusIdCompat(message.roomId))
            .setPerson(sender)
            .setIcon(sender.icon)
            .setIntent(roomIntent(message.roomId).setAction(Intent.ACTION_VIEW))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /** Bundles the per-room notifications; the children alert, so the summary stays silent. */
    private fun summary(): Notification = NotificationCompat.Builder(context, CHANNEL_QUIET)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(context.getColor(R.color.phalanx_notification_accent))
        .setGroup(GROUP_MESSAGES)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setAutoCancel(true)
        .build()

    /** Redraws a room's notification, with [repliedWith] added as this account's message. */
    @SuppressLint("MissingPermission") // Checked by allowed().
    fun refresh(roomId: String, repliedWith: String? = null) {
        if (!allowed()) return
        val shown = shownFor(roomId) ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown.notification)
            ?: return
        repliedWith?.let { style.addMessage(it, System.currentTimeMillis(), self) }
        manager.notify(
            roomId,
            MESSAGE_ID,
            NotificationCompat.Builder(context, shown.notification)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    fun dismiss(roomId: String) = manager.cancel(roomId, MESSAGE_ID)

    /** [roomId]'s conversation came on screen: its notification is stale and new ones are held back. */
    fun startViewing(roomId: String) {
        viewing.set(roomId)
        dismiss(roomId)
    }

    // Only if no other room came on screen in between, as when one room opens another.
    fun stopViewing(roomId: String) {
        viewing.compareAndSet(roomId, null)
    }

    fun isViewing(roomId: String): Boolean = viewing.get() == roomId

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

    private fun shownFor(roomId: String) =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.tag == roomId && it.id == MESSAGE_ID }

    private fun existingStyle(roomId: String): NotificationCompat.MessagingStyle? = shownFor(roomId)
        ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification) }

    private fun NotificationCompat.Builder.addActions(roomId: String) {
        val reply = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notification_action_reply),
            actionIntent(ACTION_REPLY, roomId, mutable = true) // The system fills in the typed text.
        )
            .addRemoteInput(
                RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.notification_reply_hint)).build()
            )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        val markRead = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notification_action_mark_read),
            actionIntent(ACTION_MARK_READ, roomId, mutable = false)
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        addAction(markRead)
        addAction(reply)
    }

    // The URI keeps each room's and action's PendingIntent apart.
    private fun actionIntent(action: String, roomId: String, mutable: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .setData("phalanx-$action:$roomId".toUri())
                .putExtra(EXTRA_ROOM_ID, roomId),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        )

    private fun publicVersion(): Notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.notification_new_message))
        .build()

    // The URI keeps each room's PendingIntent apart, so one room's tap never opens another.
    private fun openRoom(roomId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        roomIntent(roomId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun roomIntent(roomId: String): Intent = Intent(context, MainActivity::class.java)
        .setData("phalanx-room:$roomId".toUri())
        .putExtra(EXTRA_ROOM_ID, roomId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val EXTRA_ROOM_ID = "org.phalanx.android.ROOM_ID"

        /** With [EXTRA_ROOM_ID]: the message to open the conversation on. */
        const val EXTRA_EVENT_ID = "org.phalanx.android.EVENT_ID"
        const val ACTION_MARK_READ = "org.phalanx.android.MARK_READ"
        const val ACTION_REPLY = "org.phalanx.android.REPLY"
        const val KEY_REPLY = "reply"
        const val CHECKING_ID = 2
        private const val MESSAGE_ID = 1
        private const val SUMMARY_ID = 3
        private const val GROUP_MESSAGES = "org.phalanx.android.MESSAGES"
        private const val CHANNEL_MESSAGES = "messages"
        private const val CHANNEL_QUIET = "messages_quiet"
        private const val CHANNEL_CHECKING = "checking"
    }
}
