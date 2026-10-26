package org.phalanx.android.ui.screens.catchup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.screens.room.ScheduleSheet
import org.phalanx.android.ui.theme.Symbols

/** Every room with something unread, caught up on in turn; a card opens its room. */
@Composable
fun CatchUpScreen(
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    viewModel: CatchUpViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    var reminding by remember { mutableStateOf<CatchUpCard?>(null) }
    // By room: what came of the reminder set on its promise.
    var reminded by remember { mutableStateOf(mapOf<String, Int>()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catch_up_title)) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.catch_up_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards, key = { it.room.id }) { card ->
                CatchUpCardView(
                    card = card,
                    reminded = reminded[card.room.id],
                    onRemind = { reminding = card },
                    onClick = { onOpenRoom(card.room.id) }
                )
            }
            if (cards.any { it.state == CardState.Waiting }) {
                item {
                    FilledTonalButton(onClick = viewModel::more, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.catch_up_more))
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.summary_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    reminding?.let { card ->
        ScheduleSheet(
            exact = viewModel.canScheduleExactly(),
            onDismiss = { reminding = null },
            onPick = { at ->
                reminding = null
                viewModel.remindPromise(card, at) { saved ->
                    reminded = reminded + (card.room.id to if (saved) R.string.remind_done else R.string.remind_failed)
                }
            },
            title = R.string.remind_title
        )
    }
}

@Composable
private fun CatchUpCardView(card: CatchUpCard, @StringRes reminded: Int?, onRemind: () -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.room.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                val unread = card.room.unreadCount.toInt()
                Text(
                    pluralStringResource(R.plurals.catch_up_unread, unread, unread),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            when (val state = card.state) {
                CardState.Waiting -> Muted(stringResource(R.string.catch_up_waiting))
                CardState.Reading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(Modifier.size(24.dp))
                    Muted(stringResource(R.string.catch_up_reading))
                }
                is CardState.Failed -> Muted(stringResource(state.message))
                is CardState.Done -> {
                    state.catchUp.points.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    state.catchUp.waiting?.let { Line(stringResource(R.string.catch_up_waiting_on_you), it) }
                    state.catchUp.promised?.let {
                        Line(stringResource(R.string.catch_up_you_promised), it)
                        if (reminded != null) {
                            Muted(stringResource(reminded))
                        } else {
                            TextButton(onClick = onRemind) {
                                Icon(Symbols.AlarmOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.remind_title), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, text: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
