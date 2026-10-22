package org.phalanx.android.data.model

/** `<annotation>` keys the sanitized HTML of a [MessageBody.Text] marks spans with, for the timeline to style. */
object TextMarks {
    /** A mention pill; the value is the mentioned user's ID. */
    const val MENTION = "mention"

    /** Inline code or a code block. */
    const val CODE = "code"

    /** Hidden until tapped. */
    const val SPOILER = "spoiler"
}
