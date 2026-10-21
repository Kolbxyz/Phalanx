package org.phalanx.android.data.repository

import android.net.Uri
import org.phalanx.android.data.model.MediaRef

interface MediaRepository {
    /**
     * Downloads (and decrypts) [media] to a private cache file and returns a `content://` URI that
     * another app can be granted to read. The files are deleted on the next launch.
     */
    suspend fun openable(media: MediaRef, filename: String, mimeType: String?): Result<Uri>
}
