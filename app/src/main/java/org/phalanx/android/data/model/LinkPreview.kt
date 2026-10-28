package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** The card under a message for the page one of its links leads to. */
@Immutable
class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    /** The page's picture, already fetched; null without one, or when media wait for a tap. */
    val image: ByteArray?
)
