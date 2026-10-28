package org.phalanx.android.core.format

import org.phalanx.android.data.model.Member

/**
 * What the composer's text is sent as. "/me waves" is an emote, as in every Matrix client.
 * "@silent ..." is Discord's message that notifies no one: sent as a notice, which the default push
 * rules never notify for, Matrix side as well as through a bridge.
 */
internal data class OutgoingMessage(
    val kind: Kind,
    val body: String,
    /** `org.matrix.custom.html`, or null when there is nothing to format. */
    val html: String?,
    /** For `m.mentions`, which notifies exactly these users and nobody whose name merely appears. */
    val userIds: List<String>,
    val mentionsRoom: Boolean
) {
    enum class Kind { Text, Emote, Notice }

    companion object {
        private const val EMOTE_PREFIX = "/me "
        private const val SILENT_PREFIX = "@silent "

        /**
         * [mentions] are members picked in the composer; [formatting] follows the markdown setting;
         * [emotes] are the room's custom emoji, shortcode to `mxc://` URL. With [stripTrackers], links
         * lose their [TrackingParameters] in the body and the HTML alike.
         */
        fun of(
            text: String,
            mentions: List<Member>,
            formatting: Boolean,
            emotes: Map<String, String> = emptyMap(),
            stripTrackers: Boolean = false
        ): OutgoingMessage {
            val text = if (stripTrackers) LinkTrackers.strip(text) else text
            val kind = when {
                text.startsWith(EMOTE_PREFIX) -> Kind.Emote
                text.startsWith(SILENT_PREFIX) -> Kind.Notice
                else -> Kind.Text
            }
            val body = when (kind) {
                Kind.Emote -> text.removePrefix(EMOTE_PREFIX)
                Kind.Notice -> text.removePrefix(SILENT_PREFIX)
                Kind.Text -> text
            }
            val rendered = Markdown.render(body, mentions, formatting, emotes)
            return OutgoingMessage(kind, body, rendered.html, rendered.userIds, Markdown.mentionsRoom(body))
        }
    }
}
