package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.LinkPreview
import org.phalanx.android.data.model.MessageBody

private val CardShape = RoundedCornerShape(18.dp)
private val ImageHeight = 140.dp
private const val CARD_ALPHA = 0.12f
private const val MUTED_ALPHA = 0.8f

/**
 * Under a message with a web link, once the user turned previews on: the page's picture, site,
 * title and description, opening the page on a tap. Nothing at all while it loads or if the page
 * has nothing to show, so the bubble never jumps for a link that ends up without a card.
 */
@Composable
internal fun LinkPreviewCard(body: MessageBody.Text, modifier: Modifier = Modifier) {
    val previews = LocalLinkPreviews.current ?: return
    val enabled by previews.enabled.collectAsStateWithLifecycle()
    val url = linksOf(body).firstOrNull(::isPreviewable)?.takeIf { enabled } ?: return
    val withImage = LocalTimelinePrefs.current.autoLoadMedia
    LaunchedEffect(url, withImage) { previews.load(url, withImage) }
    val states by previews.states.collectAsStateWithLifecycle()
    (states[url] as? LinkPreviewState.Ready)?.let { Card(it.preview, modifier) }
}

@Composable
private fun Card(preview: LinkPreview, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    val content = LocalContentColor.current
    Column(
        modifier
            .width(MediaWidth)
            .clip(CardShape)
            .background(content.copy(alpha = CARD_ALPHA))
            .clickable { uriHandler.openUri(preview.url) }
    ) {
        preview.image?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(ImageHeight)
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            preview.siteName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = MUTED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            preview.title?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            preview.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = MUTED_ALPHA),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Matrix permalinks open in the app and say nothing a card could add; plain HTTP never loads.
private fun isPreviewable(url: String) = url.startsWith("https://", ignoreCase = true) &&
    !url.startsWith("https://matrix.to/", ignoreCase = true)
