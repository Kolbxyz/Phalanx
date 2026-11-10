package org.phalanx.android.ui.screens.room

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.format.FoundEntity
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.LiftOverlay
import org.phalanx.android.ui.components.NoticeKind

/** The long-press menu for [message]: lifted over the timeline, with the sheets its choices open. */
@Composable
internal fun MessageMenu(
    message: TimelineEntry.Message?,
    onDismiss: () -> Unit,
    viewModel: RoomViewModel,
    kitchen: KitchenViewModel = hiltViewModel(),
    assist: AssistViewModel = hiltViewModel(),
    reminder: ReminderViewModel = hiltViewModel(),
    bookmarks: BookmarkViewModel = hiltViewModel(),
    transcripts: TranscriptViewModel = hiltViewModel(),
    entities: EntitiesViewModel = hiltViewModel()
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var forwarding by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var versionsOf by remember { mutableStateOf<String?>(null) }
    var mixingFor by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var remindingOf by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var pickingFor by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    val kitchenOn by kitchen.enabled.collectAsStateWithLifecycle()
    val bookmarked by bookmarks.saved.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val text = (message?.body as? MessageBody.Text)?.text
    val transcriptStates by transcripts.states.collectAsStateWithLifecycle()
    // What the AI reads: the text, or a voice message's transcript once it is written.
    val readable = text ?: message?.takeIf { it.body is MessageBody.Audio }?.let { audio ->
        val state = transcriptStates[audio.eventId ?: audio.id] as? TranscriptState.Done
        state?.transcript?.text?.takeIf { it.isNotBlank() }
    }
    val aiOn = settings?.aiSummaries == true
    val offlinePairs by assist.pairs.collectAsStateWithLifecycle()
    val emotes by produceState(emptyList<CustomEmote>(), message != null) {
        if (message != null) value = viewModel.emotes()
    }
    val found by produceState(emptyList<FoundEntity>(), readable) {
        value = readable?.let { entities.find(it) }.orEmpty()
    }

    fun copy(value: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, value))) }
    }

    // Every choice closes the sheet once it has acted.
    fun then(action: () -> Unit): () -> Unit = {
        action()
        onDismiss()
    }
    val pinnedIds = header?.pinnedEventIds.orEmpty()
    LiftOverlay(
        item = message,
        onDismiss = onDismiss,
        closeLabel = stringResource(R.string.menu_close),
        stage = { lifted ->
            MessageLiftStage(
                message = lifted,
                pinned = lifted.eventId in pinnedIds,
                bookmarked = lifted.eventId in bookmarked,
                found = found,
                // The reaction lands on the message before the menu goes, so it is seen to.
                onReact = { key ->
                    viewModel.toggleReaction(lifted, key)
                    scope.launch {
                        delay(REACTION_SEEN_MILLIS)
                        onDismiss()
                    }
                },
                onMoreReactions = then { pickingFor = lifted },
                onReactWithMix = if (kitchenOn) then { mixingFor = lifted } else null,
                onEntityAction = { action ->
                    then { if (!action.launch(context)) viewModel.notify(R.string.entity_action_failed) }()
                }
            )
        },
        sheet = { lifted ->
            val liftedText = (lifted.body as? MessageBody.Text)?.text
            MessageActionsContent(
                message = lifted,
                onReact = { key -> then { viewModel.toggleReaction(lifted, key) }() },
                emotes = emotes,
                onReply = then { viewModel.composer.reply(lifted) },
                onEdit = then { liftedText?.let { viewModel.composer.edit(lifted, it) } },
                onShowVersions = versionsTarget(lifted, settings)?.let { eventId -> then { versionsOf = eventId } },
                onCopy = then {
                    liftedText?.let(::copy)
                    viewModel.notify(R.string.text_copied, NoticeKind.Done)
                },
                onTranslate = readable?.takeIf { aiOn || offlinePairs.isNotEmpty() }
                    ?.let { then { assist.translate(it) } },
                onSummarize = readable?.takeIf { aiOn && it.length >= AssistViewModel.LONG_MESSAGE_CHARS }
                    ?.let { then { assist.summarize(it) } },
                onCopyLink = { link -> then { copy(link) }() },
                onShareLink = { link -> then { shareText(context, link) }() },
                onForward = then { forwarding = lifted },
                onRemind = lifted.eventId?.let { then { remindingOf = lifted } },
                pinned = lifted.eventId in pinnedIds,
                onTogglePin = then { viewModel.setPinned(lifted, lifted.eventId !in pinnedIds) },
                bookmarked = lifted.eventId in bookmarked,
                onToggleBookmark = then { bookmarks.toggle(lifted) { viewModel.notify(R.string.bookmark_failed) } },
                onDelete = then { viewModel.redact(lifted) },
                onBlock = then { viewModel.block(lifted.senderId) }
            )
        }
    )
    forwarding?.let { target ->
        val rooms by viewModel.rooms.collectAsStateWithLifecycle()
        ForwardSheet(
            rooms = rooms?.filter { it.id != viewModel.roomId },
            onPick = { roomId ->
                viewModel.forward(target, roomId)
                forwarding = null
            },
            onDismiss = { forwarding = null }
        )
    }
    versionsOf?.let { eventId -> EditHistorySheet(eventId, onDismiss = { versionsOf = null }) }
    pickingFor?.let { target ->
        ReactionPickerDialog(
            onPick = { key ->
                viewModel.toggleReaction(target, key)
                pickingFor = null
            },
            onDismiss = { pickingFor = null }
        )
    }
    remindingOf?.let { target ->
        ScheduleSheet(
            exact = viewModel.canScheduleExactly(),
            onDismiss = { remindingOf = null },
            onPick = { at ->
                remindingOf = null
                reminder.remind(target, at) { saved ->
                    if (saved) {
                        viewModel.notify(R.string.remind_done, NoticeKind.Scheduled)
                    } else {
                        viewModel.notify(R.string.remind_failed)
                    }
                }
            },
            title = R.string.remind_title
        )
    }
    val assistState by assist.state.collectAsStateWithLifecycle()
    AssistSheet(assistState, onDismiss = assist::dismiss)
    mixingFor?.let { target ->
        KitchenSheet(
            onDismiss = { mixingFor = null },
            onPick = { mix ->
                mixingFor = null
                kitchen.react(target, mix) { viewModel.notify(R.string.timeline_action_failed) }
            },
            onStarFailed = { viewModel.notify(R.string.kitchen_star_failed) },
            kitchen = kitchen
        )
    }
}

/** The system share sheet for [text]; a chooser always resolves, so there is no failure to report. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

// Long enough to see the reaction land on the message, short enough not to wait for the menu.
private const val REACTION_SEEN_MILLIS = 320L

/** An edited message's versions are offered once it is sent, and only with the setting on. */
private fun versionsTarget(message: TimelineEntry.Message, settings: AppSettings?): String? =
    message.eventId?.takeIf { message.isEdited && settings?.showEditHistory == true }
