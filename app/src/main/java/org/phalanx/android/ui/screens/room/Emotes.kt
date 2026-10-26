package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.ReactionSummary

/** The annotation key [org.phalanx.android.core.matrix.HtmlSanitizer] marks custom emotes with. */
private const val EMOTE_TAG = "emote"
private const val EMOTE_ID = "emote:"

// Emotes are drawn a little larger than the text, as Discord and Element do, and fetched at a
// size that stays sharp at the largest text setting.
private const val EMOTE_EM = 1.4f
private const val EMOTE_PX = 96

/**
 * This text with every marked custom emote swapped for an inline image slot. The alt text
 * (":shortcode:") is kept as the slot's alternate text, so copying and screen readers still get it.
 */
internal fun AnnotatedString.withInlineEmotes(emotes: List<MediaRef?>): AnnotatedString {
    val source = this
    val marks = source.getStringAnnotations(EMOTE_TAG, 0, source.length)
    if (marks.isEmpty()) return source
    return buildAnnotatedString {
        var at = 0
        marks.forEach { mark ->
            val index = mark.item.toIntOrNull() ?: return@forEach
            if (emotes.getOrNull(index) == null || mark.start < at) return@forEach
            append(source.subSequence(at, mark.start))
            appendInlineContent(EMOTE_ID + index, source.text.substring(mark.start, mark.end))
            at = mark.end
        }
        append(source.subSequence(at, source.length))
    }
}

/** The images for [withInlineEmotes]' slots. */
internal fun emoteContent(emotes: List<MediaRef?>): Map<String, InlineTextContent> =
    emotes.withIndex().mapNotNull { (index, ref) ->
        ref ?: return@mapNotNull null
        val slot = Placeholder(EMOTE_EM.em, EMOTE_EM.em, PlaceholderVerticalAlign.TextCenter)
        EMOTE_ID + index to InlineTextContent(slot) { alt ->
            AsyncImage(model = MediaRequest(ref, EMOTE_PX), contentDescription = alt, modifier = Modifier.fillMaxSize())
        }
    }.toMap()

/** What a screen reader calls a reaction: its emoji, or "custom emoji" for an image. */
@Composable
internal fun reactionName(reaction: ReactionSummary): String =
    if (reaction.image != null) stringResource(R.string.timeline_reaction_custom) else reaction.key

/** A reaction's emoji, or its image when it is a custom emote, followed by [suffix] if any. */
@Composable
internal fun ReactionKey(reaction: ReactionSummary, imageSize: Dp, style: TextStyle, suffix: String?) {
    val image = reaction.image
    if (image == null) {
        Text(if (suffix != null) "${reaction.key} $suffix" else reaction.key, style = style)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Decorative: the chip's or tab's description names the reaction.
        AsyncImage(
            model = MediaRequest(image, EMOTE_PX),
            contentDescription = null,
            modifier = Modifier.size(imageSize)
        )
        if (suffix != null) Text(" $suffix", style = style)
    }
}
