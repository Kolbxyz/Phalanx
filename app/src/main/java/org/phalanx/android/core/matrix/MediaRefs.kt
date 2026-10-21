package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MediaRef

/** Serializes this source, encryption keys included, so the UI can hold it without SDK types. */
internal fun MediaSource.toRef(): MediaRef = MediaRef(toJson())

/** For plain `mxc://` URLs such as avatars. Null when the server sent something that is not one. */
internal fun mediaRefOf(url: String?): MediaRef? {
    if (url == null) return null
    return try {
        MediaSource.fromUrl(url).use { it.toRef() }
    } catch (_: ClientException) {
        null
    }
}

/** The caller owns the returned source and must close it. */
internal fun MediaRef.toSource(): MediaSource = MediaSource.fromJson(serialized)
