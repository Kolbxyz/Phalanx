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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.OpenFileEffect
import org.phalanx.android.ui.components.TimelinePlaceholder
import org.phalanx.android.ui.components.TopNoticeHost

@Composable
fun RoomScreen(
    onBack: () -> Unit,
    onOpenDetails: (roomId: String) -> Unit,
    onOpenMedia: (roomId: String, entryId: String) -> Unit,
    viewModel: RoomViewModel = hiltViewModel()
) {
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
    var scheduling by remember { mutableStateOf(false) }
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val launchers = rememberAttachLaunchers(viewModel)

    OpenFileEffect(openRequest, viewModel::onOpened) { viewModel.notify(R.string.timeline_open_failed) }

    val actions = remember(viewModel) {
        MessageActions(
            onOpenMedia = { onOpenMedia(viewModel.roomId, it.id) },
            onOpenFile = viewModel::openFile,
            resolveAudio = viewModel::audioUri,
            onToggleReaction = viewModel::toggleReaction,
            onShowReactors = viewModel::showReactors,
            onReply = viewModel.composer::reply,
            onLongPress = { selected = it },
            onOpenProfile = { profileOf = it }
        )
    }
    val composerActions = remember(viewModel) {
        val composer = viewModel.composer
        ComposerActions(
            onDraftChange = composer::onDraftChange,
            onPickSuggestion = composer::pickSuggestion,
            onSend = composer::send,
            onCancelMode = composer::cancelMode,
            onAttach = { attaching = true },
            onRemoveAttachment = composer::removeAttachment,
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
        topBar = { RoomTopBar(header, ready?.title, onBack, onOpenDetails = { onOpenDetails(viewModel.roomId) }) },
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
                    TimelineList(current, padding, actions, viewModel::loadOlder, focus, viewModel::onFocused)
                }
            }
        }
    }
    TopNoticeHost(notice, onDismiss = viewModel::dismissNotice)

    MessageMenu(selected, onDismiss = { selected = null }, viewModel)
    ProfileMenu(profileOf, onDismiss = { profileOf = null }, viewModel)
    val reactors by viewModel.reactors.collectAsStateWithLifecycle()
    reactors?.let { ReactorsSheet(it, onDismiss = viewModel::dismissReactors) }
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
            onFile = pick(launchers.file)
        )
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { content() }
}
