package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.OpenFileEffect
import org.phalanx.android.ui.components.TimelinePlaceholder
import org.phalanx.android.ui.components.TopNoticeHost
import org.phalanx.android.ui.screens.imageedit.ImageEditor

@Composable
fun RoomScreen(
    onBack: () -> Unit,
    onOpenDetails: (roomId: String) -> Unit,
    onOpenMedia: (roomId: String, entryId: String) -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    viewModel: RoomViewModel = hiltViewModel(),
    summary: SummaryViewModel = hiltViewModel(),
    unread: UnreadViewModel = hiltViewModel(),
    reactorsModel: ReactorsViewModel = hiltViewModel()
) {
    val kitchen: KitchenViewModel = hiltViewModel()
    ProtectedRoomScreen()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composerState by viewModel.composer.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var profileOf by remember { mutableStateOf<String?>(null) }
    var attaching by remember { mutableStateOf(false) }
    var stickering by remember { mutableStateOf(false) }
    var editingImage by remember { mutableStateOf<String?>(null) }
    var scheduling by remember { mutableStateOf(false) }
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val launchers = rememberAttachLaunchers(viewModel)

    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose { viewModel.onHidden() }
    }

    OpenFileEffect(openRequest, viewModel::onOpened) { viewModel.notify(R.string.timeline_open_failed) }

    val actions = remember(viewModel) {
        MessageActions(
            onOpenMedia = { onOpenMedia(viewModel.roomId, it.id) },
            onOpenFile = viewModel::openFile,
            resolveFile = viewModel::fileUri,
            onToggleReaction = viewModel::toggleReaction,
            onShowReactors = reactorsModel::show,
            onReply = viewModel.composer::reply,
            onLongPress = { selected = it },
            onOpenProfile = { profileOf = it },
            onJumpTo = viewModel::jumpTo
        )
    }
    val composerActions = remember(viewModel) {
        val composer = viewModel.composer
        ComposerActions(
            onDraftChange = composer::onDraftChange,
            onPickSuggestion = composer::pickSuggestion,
            onSend = composer::send,
            onSendMix = { mix ->
                composer.clearDraft()
                kitchen.send(mix) { viewModel.notify(R.string.composer_send_failed) }
            },
            onCancelMode = composer::cancelMode,
            onAttach = { attaching = true },
            onRemoveAttachment = composer::removeAttachment,
            onEditAttachment = { editingImage = it },
            onRecord = launchers.record,
            onCancelRecording = composer::cancelRecording,
            onSendRecording = composer::sendRecording,
            onSchedule = { scheduling = true },
            onSendScheduledNow = viewModel::sendScheduledNow,
            onCancelScheduled = viewModel::cancelScheduled
        )
    }

    val ready = state as? RoomUiState.Ready
    val prefs = settings?.timelinePrefs() ?: TimelinePrefs()
    Scaffold(
        topBar = {
            RoomTopBar(
                header, ready?.title, onBack,
                onOpenDetails = { onOpenDetails(viewModel.roomId) },
                onSummarize = if (settings?.aiSummaries == true) summary::start else null
            )
        },
        bottomBar = {
            if (ready != null) {
                Composer(composerState, composerActions, enterToSend = settings?.enterToSend == true, scheduled)
            }
        }
    ) { padding ->
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }
        ) { current ->
            when (current) {
                RoomUiState.Loading -> TimelinePlaceholder(padding)
                RoomUiState.Unavailable -> Centered(padding) {
                    Text(
                        text = stringResource(R.string.room_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is RoomUiState.Ready -> CompositionLocalProvider(LocalTimelinePrefs provides prefs) {
                    Box(Modifier.fillMaxSize()) {
                        val checkpoints by summary.marks.collectAsStateWithLifecycle()
                        val marks = remember(checkpoints) { SummaryMarks(checkpoints, summary::show) }
                        TimelineList(
                            current, padding, actions, marks, viewModel::loadOlder, focus, viewModel::onFocused
                        )
                        val offer by unread.offer.collectAsStateWithLifecycle()
                        UnreadJump(
                            offer,
                            onJump = {
                                unread.jump(viewModel::jumpTo) { viewModel.notify(R.string.timeline_focus_not_found) }
                            },
                            onDismiss = unread::dismiss,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .padding(top = padding.calculateTopPadding() + 8.dp)
                        )
                    }
                }
            }
        }
    }
    TopNoticeHost(notice, onDismiss = viewModel::dismissNotice)

    MessageMenu(selected, onDismiss = { selected = null }, viewModel)
    ProfileMenu(profileOf, onDismiss = { profileOf = null }, onOpenRoom, viewModel)
    val reactors by reactorsModel.reactors.collectAsStateWithLifecycle()
    reactors?.let { ReactorsSheet(it, onDismiss = reactorsModel::dismiss) }
    editingImage?.let { id ->
        val image = composerState.attachments.firstOrNull { it.id == id }?.media as? OutgoingMedia.Image
        if (image == null) {
            editingImage = null
        } else {
            ImageEditor(
                file = image.file,
                onDone = {
                    viewModel.composer.editAttachment(id, it)
                    editingImage = null
                },
                onFailed = {
                    editingImage = null
                    viewModel.notify(R.string.editor_failed)
                },
                onDismiss = { editingImage = null }
            )
        }
    }
    if (scheduling) {
        ScheduleSheet(
            exact = viewModel.canScheduleExactly(),
            onDismiss = { scheduling = false },
            onPick = { at ->
                scheduling = false
                viewModel.scheduleDraft(at)
            }
        )
    }
    if (attaching) {
        // Each choice leaves the app for a picker, so the sheet closes first.
        fun pick(launch: () -> Unit): () -> Unit = {
            attaching = false
            launch()
        }
        AttachSheet(
            onDismiss = { attaching = false },
            onPhoto = pick(launchers.photo),
            onVideo = pick(launchers.video),
            onGallery = pick(launchers.gallery),
            onFile = pick(launchers.file),
            // Emoji Kitchen is the only sticker source, so the choice shows only once it is on.
            onSticker = if (settings?.emojiKitchen == true) {
                {
                    attaching = false
                    stickering = true
                }
            } else {
                null
            }
        )
    }
    SummaryHost(summary, viewModel)
    if (stickering) {
        KitchenSheet(
            onDismiss = { stickering = false },
            onPick = { mix ->
                stickering = false
                kitchen.send(mix) { viewModel.notify(R.string.composer_send_failed) }
            },
            onStarFailed = { viewModel.notify(R.string.kitchen_star_failed) },
            kitchen = kitchen
        )
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { content() }
}
