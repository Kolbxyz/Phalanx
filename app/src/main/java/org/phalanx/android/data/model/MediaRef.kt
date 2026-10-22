package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/**
 * Points at a piece of media on the server, including the keys for encrypted media. Opaque outside
 * `core/matrix`, which created it and is the only place that can read it.
 */
@Immutable
data class MediaRef(internal val serialized: String)

/**
 * What the image loader is asked for: [ref] scaled down by the server to about [thumbnailPx]
 * square, or the original when [thumbnailPx] is null. With [videoFrame], [ref] is a video and the
 * picture is its first frame, taken on the device at most [thumbnailPx] wide.
 */
@Immutable
data class MediaRequest(val ref: MediaRef, val thumbnailPx: Int? = null, val videoFrame: Boolean = false)
