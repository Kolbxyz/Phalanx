package org.phalanx.android.core.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.phalanx.android.R
import org.phalanx.android.core.ai.speech.Transcriber
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.IncomingMessage
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.NotificationContent
import org.phalanx.android.data.repository.MediaRepository

/**
 * Writes a notified voice message out as text, on the phone, in place of "Voice message", when the
 * user has voice messages written out on sight. Kept under the event, as the conversation keys it,
 * so opening the room shows the same transcript without running the model again.
 */
@Singleton
internal class VoiceNotification @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val transcriber: Transcriber,
    private val media: MediaRepository,
    private val notifier: Notifier
) {
    // Best effort, as the sender's photo is: on any failure the notification keeps "Voice message",
    // which is still true, and the conversation offers the transcript again.
    suspend fun writeOut(message: IncomingMessage) {
        val body = (message.body as? MessageBody.Audio)?.takeIf { isWanted(message, it) } ?: return
        media.openable(body.media, body.filename, body.mimeType).onSuccess { audio ->
            transcriber.transcribe(message.eventId, audio) {}.onSuccess { transcript ->
                if (transcript.text.isNotBlank()) {
                    notifier.rewrite(message, context.getString(R.string.notification_voice, transcript.text))
                }
            }
        }
    }

    // Full content only: names alone or "New message" must not turn into what was said.
    private suspend fun isWanted(message: IncomingMessage, body: MessageBody.Audio): Boolean =
        body.isVoice &&
            message.reaction == null &&
            message.shown == NotificationContent.Full &&
            !notifier.isViewing(message.roomId) &&
            settings.current().autoTranscribe
}
