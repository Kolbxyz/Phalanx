package org.phalanx.android.core.push

import android.content.Context
import org.phalanx.android.R
import org.phalanx.android.data.model.IncomingMessage
import org.phalanx.android.data.model.NotificationContent
import org.phalanx.android.ui.screens.room.summaryOf

/** The line a notification shows for [message], no more than its [IncomingMessage.shown] allows. */
internal fun Context.notificationText(message: IncomingMessage): String {
    val summary = message.body?.let { summaryOf(it, this::getString) }
    return when {
        message.shown == NotificationContent.Hidden -> getString(R.string.notification_new_message)
        summary == null -> getString(R.string.notification_invite, message.senderName)
        message.shown == NotificationContent.NameOnly && message.reaction != null ->
            getString(R.string.notification_reacted)
        message.shown == NotificationContent.NameOnly -> getString(R.string.notification_new_message)
        message.reaction == "" -> getString(R.string.notification_reaction_unknown, summary)
        message.reaction != null -> getString(R.string.notification_reaction, message.reaction, summary)
        else -> summary
    }
}

/** Who the notification says wrote; the app's name when notifications hide even that. */
internal fun Context.notificationSender(message: IncomingMessage): String =
    if (message.shown == NotificationContent.Hidden) getString(R.string.app_name) else message.senderName

/** The room's name, unless notifications hide it. */
internal fun notificationRoomName(message: IncomingMessage): String? =
    message.roomName.takeUnless { message.isDirect || message.shown == NotificationContent.Hidden }
