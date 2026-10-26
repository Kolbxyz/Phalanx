package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.ReplyPreview
import org.phalanx.android.data.model.ScheduledMessage
import org.phalanx.android.ui.theme.Symbols

/** Sits in the Scaffold's bottom bar, so it owns the keyboard and navigation-bar insets itself. */
@Composable
internal fun Composer(
    state: ComposerState,
    actions: ComposerActions,
    enterToSend: Boolean,
    scheduled: List<ScheduledMessage>,
    modifier: Modifier = Modifier
) {
    val motion = MaterialTheme.motionScheme
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        Column(Modifier.windowInsetsPadding(insets)) {
            AnimatedVisibility(
                visible = scheduled.isNotEmpty(),
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
            ) {
                ScheduledStrip(scheduled, actions.onSendScheduledNow, actions.onCancelScheduled)
            }
            ModeBanner(state.mode, actions.onCancelMode)
            AnimatedVisibility(
                visible = state.suggestions.isNotEmpty(),
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
            ) {
                MentionSuggestions(state.suggestions, actions.onPickSuggestion)
            }
            AnimatedVisibility(
                visible = state.attachments.isNotEmpty(),
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
            ) {
                AttachmentStrip(state.attachments, actions.onRemoveAttachment, actions.onEditAttachment)
            }
            if (state.attachments.isEmpty() && state.mode !is ComposerMode.Edit) {
                KitchenMerge(state.draft, actions.onSendMix)
            }
            val fade = motion.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = state.recording != null,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }
            ) { recording ->
                if (recording) {
                    RecordingRow(state.recording.orEmpty(), actions.onCancelRecording, actions.onSendRecording)
                } else {
                    InputRow(state, actions, enterToSend)
                }
            }
        }
    }
}

/** Kept on screen through the collapse animation with the last target, so it doesn't blank out. */
@Composable
private fun ModeBanner(mode: ComposerMode, onCancel: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(mode) }
    if (mode != ComposerMode.New) shown = mode
    AnimatedVisibility(
        visible = mode != ComposerMode.New,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
    ) {
        val (preview, cancel) = when (val target = shown) {
            ComposerMode.New -> return@AnimatedVisibility
            is ComposerMode.Reply -> ReplyPreview(
                stringResource(R.string.composer_replying_to, target.message.senderName),
                target.message.body
            ) to R.string.composer_reply_cancel
            is ComposerMode.Edit -> ReplyPreview(
                stringResource(R.string.composer_editing),
                target.message.body
            ) to R.string.composer_edit_cancel
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReplyQuote(preview, Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Symbols.Close, contentDescription = stringResource(cancel))
            }
        }
    }
}
