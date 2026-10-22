package org.phalanx.android.ui.screens.room

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.phalanx.android.data.model.TextMarks

/**
 * Styles the [TextMarks] spans of a message: mentions as pills that call [onMention] with the user
 * tapped, code on a faint ground, spoilers covered in [contentColor] until [revealed]; tapping one
 * calls [onReveal].
 */
internal fun AnnotatedString.withMarks(
    contentColor: Color,
    revealed: Boolean,
    onMention: (userId: String) -> Unit,
    onReveal: () -> Unit
): AnnotatedString {
    val source = this
    return buildAnnotatedString {
        append(source)
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

private const val MARK_ALPHA = 0.12f
