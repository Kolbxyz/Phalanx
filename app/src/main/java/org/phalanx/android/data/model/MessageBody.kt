package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface MessageBody {
    /**
     * [html] is already sanitized and free of reply fallbacks; [text] is the plain fallback.
     * [emotes] are the custom emoji [html] marks with `<annotation emote="N">`, by N.
     */
    data class Text(
        val text: String,
        val html: String?,
        val style: TextStyle,
        val emotes: List<MediaRef?> = emptyList()
    ) : MessageBody

    data class Image(
        val media: MediaRef,
        val thumbnail: MediaRef?,
        val width: Int?,
        val height: Int?,
        val caption: Text?,
        val isSticker: Boolean,
        val filename: String,
        val mimeType: String?,
        val sizeBytes: Long? = null
    ) : MessageBody

    data class Video(
        val media: MediaRef,
        val thumbnail: MediaRef?,
        val width: Int?,
        val height: Int?,
        val durationMillis: Long?,
        val filename: String,
        val mimeType: String?,
        val caption: Text?,
        val sizeBytes: Long? = null,
        /** A bridged GIF sent as video: played inline, silent and looping, without controls. */
        val isGif: Boolean = false
    ) : MessageBody

    data class Audio(
        val media: MediaRef,
        val filename: String,
        val mimeType: String?,
        val durationMillis: Long?,
        /** Bar heights from 0 to 1, present for voice messages. */
        val waveform: List<Float>?,
        val isVoice: Boolean
    ) : MessageBody

    data class File(
        val media: MediaRef,
        val filename: String,
        val mimeType: String?,
        val sizeBytes: Long?,
        val caption: Text?
    ) : MessageBody

    data class Location(val geoUri: String, val description: String?) : MessageBody

    /**
     * A poll, as Matrix clients and bridges (WhatsApp, Signal, Telegram) send it. [myAnswers] are
     * the IDs this account voted for; with [disclosed] false, counts stay hidden until it [ended].
     */
    data class Poll(
        val question: String,
        val answers: List<PollAnswer>,
        val maxSelections: Int,
        val disclosed: Boolean,
        val ended: Boolean,
        val myAnswers: Set<String>
    ) : MessageBody

    /** A message of a kind this build cannot show yet. */
    data class Unsupported(val kind: UnsupportedKind) : MessageBody

    data object Redacted : MessageBody

    /** Encrypted for keys this device does not have (yet). */
    data object Undecryptable : MessageBody

    enum class TextStyle { Normal, Notice, Emote }
}

/** One choice of a [MessageBody.Poll], with how many voted for it. */
data class PollAnswer(val id: String, val text: String, val votes: Int)

enum class UnsupportedKind { Gallery, LiveLocation, Other }
