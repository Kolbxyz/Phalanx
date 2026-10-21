package org.phalanx.android.ui.screens.room

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.TimelinePlaceholder

@Composable
fun RoomScreen(
    onBack: () -> Unit,
    onOpenMedia: (roomId: String, entryId: String) -> Unit,
    viewModel: RoomViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var deleting by remember { mutableStateOf<TimelineEntry.Message?>(null) }

    val noticeText = notice?.let { stringResource(it) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbar.showSnackbar(noticeText)
            viewModel.dismissNotice()
        }
    }
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(request.uri, request.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.notify(R.string.timeline_open_failed)
        }
        viewModel.onOpened()
    }

    val actions = remember(viewModel) {
        MessageActions(
            onOpenImage = { onOpenMedia(viewModel.roomId, it.id) },
            onOpenFile = viewModel::openFile,
            resolveAudio = viewModel::audioUri,
            onToggleReaction = viewModel::toggleReaction,
            onReply = viewModel::startReply,
            onLongPress = { selected = it }
        )
    }

    val ready = state as? RoomUiState.Ready
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = ready?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            if (ready != null) {
                Composer(draft, replyingTo, viewModel::onDraftChange, viewModel::send, viewModel::cancelReply)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
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
                is RoomUiState.Ready -> TimelineList(current, padding, actions, viewModel::loadOlder)
            }
        }
    }

    selected?.let { message ->
        MessageActionsSheet(
            message = message,
            onDismiss = { selected = null },
            onReact = { key ->
                viewModel.toggleReaction(message, key)
                selected = null
            },
            onReply = {
                viewModel.startReply(message)
                selected = null
            },
            onCopy = {
                (message.body as? MessageBody.Text)?.let { body ->
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, body.text))) }
                }
                selected = null
            },
            onDelete = {
                deleting = message
                selected = null
            }
        )
    }
    deleting?.let { message ->
        DeleteMessageDialog(
            onConfirm = {
                viewModel.redact(message)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { content() }
}
