package org.phalanx.android.ui.screens.catchup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.PhalanxSnackbarHost
import org.phalanx.android.ui.components.ScrollHairline
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.components.scrolledPastTop
import org.phalanx.android.ui.components.showReport
import org.phalanx.android.ui.components.showUndo
import org.phalanx.android.ui.theme.Symbols

/**
 * Every room with something unread, caught up on in turn: first what is waiting on the user across
 * all of them, then what happened in each. Nothing is marked read unless asked.
 */
@Composable
fun CatchUpScreen(
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    viewModel: CatchUpViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    // By "room kind": where each need's reminder stands.
    var reminding by remember { mutableStateOf(mapOf<String, Reminding>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val doneLabel = stringResource(R.string.catch_up_marked_done)
    val undoLabel = stringResource(R.string.undo)
    val failedLabel = stringResource(R.string.remind_failed)
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.catch_up_title)) },
                    navigationIcon = { BackButton(onBack) },
                    actions = { OnThisPhone() }
                )
                ScrollHairline(list.scrolledPastTop())
            }
        },
        snackbarHost = { PhalanxSnackbarHost(snackbar) }
    ) { padding ->
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.catch_up_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        val needs = needsOf(cards)
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "progress") { CatchUpProgress(cards, Modifier.padding(bottom = 20.dp).riseIn(0L)) }
            item(key = "needs") {
                SectionTitle(stringResource(R.string.catch_up_needs, needs.size), needsColour = true)
            }
            if (needs.isEmpty()) {
                item(key = "nothing") { NothingWaiting() }
            }
            itemsIndexed(needs, key = { _, need -> needKey(need) }) { index, need ->
                val key = needKey(need)
                NeedCard(
                    need = need,
                    index = index,
                    count = needs.size,
                    reminding = reminding[key] ?: Reminding.Idle,
                    onReply = { onOpenRoom(need.card.room.id) },
                    onRemind = { reminding = reminding + (key to it) },
                    onPick = { at ->
                        reminding = reminding + (key to Reminding.Set(null))
                        viewModel.remind(need.card, need.kind, at) { id ->
                            reminding = reminding + (key to if (id != null) Reminding.Set(id) else Reminding.Idle)
                            if (id == null) scope.launch { snackbar.showReport(failedLabel) }
                        }
                    },
                    onUndoReminder = {
                        (reminding[key] as? Reminding.Set)?.reminderId?.let(viewModel::cancelReminder)
                        reminding = reminding + (key to Reminding.Idle)
                    },
                    onDone = {
                        viewModel.markDone(need.card.room.id, need.kind)
                        scope.launch {
                            snackbar.showUndo(doneLabel, undoLabel) {
                                viewModel.undoDone(need.card.room.id, need.kind, need.text)
                            }
                        }
                    },
                    modifier = Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                    )
                )
            }
            item(key = "happened") {
                SectionTitle(stringResource(R.string.catch_up_what_happened), needsColour = false)
            }
            itemsIndexed(cards, key = { _, card -> card.room.id }) { index, card ->
                HappenedCard(
                    card = card,
                    index = index,
                    count = cards.size,
                    onOpen = { onOpenRoom(card.room.id) },
                    onMarkRead = { viewModel.markRead(card.room.id, it) }
                )
            }
            item(key = "more") {
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visible = cards.any { it.state == CardState.Waiting },
                    exit = shrinkVertically(motion.defaultEffectsSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    FilledTonalButton(
                        onClick = viewModel::more,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp)
                    ) { Text(stringResource(R.string.catch_up_more)) }
                }
            }
            item(key = "disclaimer") {
                Text(
                    text = stringResource(R.string.summary_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

private fun needKey(need: Need) = "${need.card.room.id} ${need.kind}"

@Composable
private fun SectionTitle(text: String, needsColour: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (needsColour) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun NothingWaiting() {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            stringResource(R.string.catch_up_nothing_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

/** Says the model runs here, so nothing read was sent anywhere. */
@Composable
private fun OnThisPhone() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(Symbols.PhonelinkLock, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.catch_up_on_phone), style = MaterialTheme.typography.labelMedium)
        }
    }
}
