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
import androidx.annotation.StringRes
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

/**
 * "Marie is typing" and "Marie read your message", for the rooms the user asked to watch. Shown on
 * a silent channel of their own, one alert per room and kind, replaced as it changes. Never for the
 * room on screen, where the dots and receipts are already in sight.
 */
@Singleton
internal class ActivityAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: Notifier,
    private val settings: SettingsStore
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel_activity),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    /** [names] started typing in [roomName]; the alert goes away by itself once it is stale. */
    suspend fun typing(roomId: String, roomName: String, names: List<String>) {
        if (names.isEmpty()) return
        val text = textOf(R.string.activity_typing, R.string.activity_typing_hidden, names)
        show(roomId, TYPING_ID, roomName, text, TYPING_TIMEOUT_MILLIS)
    }

    fun stoppedTyping(roomId: String) = manager.cancel(roomId, TYPING_ID)

    /** [names] read this account's latest message in [roomName]. */
    suspend fun read(roomId: String, roomName: String, names: List<String>) {
        if (names.isEmpty()) return
        val text = textOf(R.string.activity_read, R.string.activity_read_hidden, names)
        show(roomId, READ_ID, roomName, text, timeoutMillis = null)
    }

    // Notifications that hide who wrote hide who typed or read too, and in which room.
    private suspend fun hidden() = settings.current().notificationContent == NotificationContent.Hidden

    private suspend fun textOf(@StringRes named: Int, @StringRes anonymous: Int, names: List<String>) =
        if (hidden()) context.getString(anonymous) else context.getString(named, names.joinToString(", "))

    @SuppressLint("MissingPermission") // Checked just before.
    private suspend fun show(roomId: String, id: Int, roomName: String, text: String, timeoutMillis: Long?) {
        if (notifier.isViewing(roomId) || !allowed()) return
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.phalanx_notification_accent))
            .setContentTitle(if (hidden()) context.getString(R.string.app_name) else roomName)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openRoom(roomId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .apply { if (timeoutMillis != null) setTimeoutAfter(timeoutMillis) }
            .build()
        manager.notify(roomId, id, notification)
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // The URI keeps each room's PendingIntent apart, as the message notifications do.
    private fun openRoom(roomId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setData("phalanx-room:$roomId".toUri())
            .putExtra(Notifier.EXTRA_ROOM_ID, roomId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val CHANNEL = "activity"

        // Apart from the message notification's ID 1, the summary's 3 and WorkManager's 2.
        const val TYPING_ID = 10
        const val READ_ID = 11

        // A typing notice lasts about 30 s on the server unless renewed.
        const val TYPING_TIMEOUT_MILLIS = 30_000L
    }
}
