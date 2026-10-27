package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/**
 * A custom emoji usable in a room: [shortcode] without its colons, [url] its `mxc://` image, which
 * is also the key of a reaction with it. Bridges turn both back into the network's own emoji.
 */
@Immutable
data class CustomEmote(val shortcode: String, val url: String, val image: MediaRef)
