package org.phalanx.android.data.repository

import org.phalanx.android.data.model.LinkPreview

interface LinkPreviewRepository {
    /**
     * The preview of [url], fetched by whoever the user's link preview setting names; null when the
     * setting is off or the page offers nothing to show. Without [withImage], no picture is fetched.
     */
    suspend fun preview(url: String, withImage: Boolean): Result<LinkPreview?>
}
