package org.phalanx.android.core.push

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.concurrent.thread
import org.phalanx.android.BuildConfig
import org.phalanx.android.data.model.NotificationPicture

/**
 * Hands the system the photos shown in message notifications. They stay in this process's memory
 * under a random name, so no decrypted copy reaches disk and no media key leaves the app; one the
 * process lost (it was killed) simply isn't drawn. Not exported: the system reads it through the
 * grant a notification carries.
 */
internal class NotificationPictureProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String? = pictures.get(uri.lastPathSegment.orEmpty())?.mimeType

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val picture = pictures.get(uri.lastPathSegment.orEmpty()) ?: throw FileNotFoundException(uri.toString())
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "notification-picture") {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(picture.bytes) }
        }
        return read
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.notificationpictures"

        // About a dozen notification photos; older ones fall out first.
        private const val CACHE_BYTES = 8 * 1024 * 1024

        // Android 8.x doesn't grant a notification's URIs to the system by itself; later versions do.
        private const val SYSTEM_UI = "com.android.systemui"

        private val pictures = object : LruCache<String, NotificationPicture>(CACHE_BYTES) {
            override fun sizeOf(key: String, value: NotificationPicture) = value.bytes.size
        }

        /** Keeps [picture] for the system to read and returns where it can. */
        fun publish(context: Context, picture: NotificationPicture): Uri {
            val name = UUID.randomUUID().toString()
            pictures.put(name, picture)
            val uri = Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(name).build()
            context.grantUriPermission(SYSTEM_UI, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return uri
        }
    }
}
