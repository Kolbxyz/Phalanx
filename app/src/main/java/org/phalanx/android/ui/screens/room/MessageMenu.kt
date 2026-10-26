package org.phalanx.android.ui.screens.room

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.actions.mayHoldActions
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.NoticeKind

/** The long-press sheet for [message], and the delete confirmation it can lead to. */
@Composable
internal fun MessageMenu(
    message: TimelineEntry.Message?,
    onDismiss: () -> Unit,
    viewModel: RoomViewModel,
    kitchen: KitchenViewModel = hiltViewModel(),
    assist: AssistViewModel = hiltViewModel(),
    reminder: ReminderViewModel = hiltViewModel(),
    actions: ActionsViewModel = hiltViewModel()
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var blocking by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var forwarding by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var versionsOf by remember { mutableStateOf<String?>(null) }
    var mixingFor by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var remindingOf by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    val kitchenOn by kitchen.enabled.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val text = (message?.body as? MessageBody.Text)?.text
    val aiOn = settings?.aiSummaries == true

    fun copy(value: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, value))) }
    }

    // Every choice closes the sheet once it has acted.
    fun then(action: () -> Unit): () -> Unit = {
        action()
        onDismiss()
    }
    if (message != null) {
        MessageActionsSheet(
            message = message,
            onDismiss = onDismiss,
            onReact = { key -> then { viewModel.toggleReaction(message, key) }() },
            onReactWithMix = if (kitchenOn) then { mixingFor = message } else null,
            onReply = then { viewModel.composer.reply(message) },
            onEdit = then { text?.let { viewModel.composer.edit(message, it) } },
            onShowVersions = versionsTarget(message, settings)?.let { eventId -> then { versionsOf = eventId } },
            onCopy = then { text?.let(::copy) },
            onTranslate = text?.takeIf { aiOn }?.let { then { assist.translate(it) } },
            onSummarize = text?.takeIf { aiOn && it.length >= AssistViewModel.LONG_MESSAGE_CHARS }
                ?.let { then { assist.summarize(it) } },
            onFindActions = text?.takeIf { aiOn && mayHoldActions(it) }?.let { then { actions.find(message, it) } },
            onCopyLink = { link -> then { copy(link) }() },
            onShareLink = { link -> then { shareText(context, link) }() },
            onForward = then { forwarding = message },
            onRemind = message.eventId?.let { then { remindingOf = message } },
            pinned = message.eventId in header?.pinnedEventIds.orEmpty(),
            onTogglePin = then { viewModel.setPinned(message, message.eventId !in header?.pinnedEventIds.orEmpty()) },
            onDelete = then { deleting = message },
            senderMuted = message.senderId in muted,
            onToggleMute = then { viewModel.setMuted(message.senderId, message.senderId !in muted) },
            onBlock = then { blocking = message }
        )
    }
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
    val actionsState by actions.state.collectAsStateWithLifecycle()
    ActionsSheet(
        state = actionsState,
        onDismiss = actions::dismiss,
        onRemind = { found ->
            (actionsState as? ActionsState.Found)?.let { state ->
                reminder.remind(state.message, found.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
                    viewModel.notify(if (it) R.string.remind_done else R.string.remind_failed)
                }
            }
        },
        onCopy = ::copy,
        onNoApp = { viewModel.notify(R.string.actions_no_app) }
    )
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
    deleting?.let { target ->
        DeleteMessageDialog(
            onConfirm = {
                viewModel.redact(target)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
    blocking?.let { target ->
        BlockUserDialog(
            name = target.senderName,
            onConfirm = {
                viewModel.block(target.senderId)
                blocking = null
            },
            onDismiss = { blocking = null }
        )
    }
}

/** The system share sheet for [text]; a chooser always resolves, so there is no failure to report. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** An edited message's versions are offered once it is sent, and only with the setting on. */
private fun versionsTarget(message: TimelineEntry.Message, settings: AppSettings?): String? =
    message.eventId?.takeIf { message.isEdited && settings?.showEditHistory == true }
