package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private val UrlPattern = Regex("""https?://\S+""")

/**
 * Pasting a link over selected text turns the selection into a markdown link, as in Element X.
 * Returns the field as it should become, or null when this edit is not such a paste.
 */
internal fun TextFieldValue.asLinkPasteOver(previous: TextFieldValue): TextFieldValue? {
    val selection = previous.selection
    if (selection.collapsed) return null
    val before = previous.text.substring(0, selection.min)
    val after = previous.text.substring(selection.max)
    if (text.length < before.length + after.length || !text.startsWith(before) || !text.endsWith(after)) return null
    val pasted = text.substring(before.length, text.length - after.length)
    if (!UrlPattern.matches(pasted)) return null
    val link = "[${previous.text.substring(selection.min, selection.max)}]($pasted)"
    return TextFieldValue(before + link + after, TextRange(before.length + link.length))
}
