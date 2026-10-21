package org.phalanx.android.data.repository

import android.net.Uri
import org.phalanx.android.data.model.MediaRef

interface MediaRepository {
    /**
     * Downloads (and decrypts) [media] to a private cache file and returns a `content://` URI that
     * another app can be granted to read. The files are deleted on the next launch.
     */
    suspend fun openable(media: MediaRef, filename: String, mimeType: String?): Result<Uri>

    /**
     * Saves a decrypted copy into the shared Pictures or Movies collection, under "Phalanx".
     * Android 10 and later only; earlier versions save through [saveTo] and a document picker.
     */
    suspend fun saveToGallery(media: MediaRef, filename: String, mimeType: String?): Result<Unit>

    /** Writes a decrypted copy into [destination], a document the user picked. */
    suspend fun saveTo(media: MediaRef, destination: Uri): Result<Unit>
}
