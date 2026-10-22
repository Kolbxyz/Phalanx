package org.phalanx.android.data.model

/** A photo for a message notification, already fetched and decrypted, held in memory only. */
class NotificationPicture(val bytes: ByteArray, val mimeType: String)
