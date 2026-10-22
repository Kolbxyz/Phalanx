package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.phalanx.android.data.model.TextMarks

/** What a message's text is drawn on, so colours the sender picked can be checked against it. */
internal val LocalBubbleColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * Styles the [TextMarks] spans of a message: mentions as pills that call [onMention] with the user
 * tapped, code on a faint ground, spoilers covered in [contentColor] until [revealed]; tapping one
 * calls [onReveal]. Sender colours too faint on [background] (Discord's uncoloured roles are black)
 * fall back to [contentColor].
 */
internal fun AnnotatedString.withMarks(
    contentColor: Color,
    background: Color,
    revealed: Boolean,
    onMention: (userId: String) -> Unit,
    onReveal: () -> Unit
): AnnotatedString {
    val source = this
    return buildAnnotatedString {
        append(source)
        source.spanStyles.forEach {
            if (it.item.color.isSpecified && contrast(it.item.color, background) < MIN_CONTRAST) {
                addStyle(SpanStyle(color = contentColor), it.start, it.end)
            }
        }
        source.getStringAnnotations(TextMarks.MENTION, 0, source.length).forEach {
            addStyle(
                SpanStyle(fontWeight = FontWeight.SemiBold, background = contentColor.copy(alpha = MARK_ALPHA)),
                it.start,
                it.end
            )
            addLink(LinkAnnotation.Clickable(TextMarks.MENTION) { _ -> onMention(it.item) }, it.start, it.end)
        }
        source.getStringAnnotations(TextMarks.CODE, 0, source.length).forEach {
            addStyle(SpanStyle(background = contentColor.copy(alpha = MARK_ALPHA)), it.start, it.end)
        }
        source.getStringAnnotations(TextMarks.SPOILER, 0, source.length).forEach {
            if (revealed) {
                addStyle(SpanStyle(background = contentColor.copy(alpha = MARK_ALPHA)), it.start, it.end)
            } else {
                addStyle(SpanStyle(color = Color.Transparent, background = contentColor), it.start, it.end)
                addLink(LinkAnnotation.Clickable(TextMarks.SPOILER) { onReveal() }, it.start, it.end)
            }
        }
    }
}

/** WCAG contrast ratio of two opaque colours. */
private fun contrast(a: Color, b: Color): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + LUMINANCE_OFFSET) / (dark + LUMINANCE_OFFSET)
}

private const val MARK_ALPHA = 0.12f

// WCAG's floor for large text and UI parts; sender colours only tint words, so the body's 4.5 is too strict.
private const val MIN_CONTRAST = 3f
private const val LUMINANCE_OFFSET = 0.05f
