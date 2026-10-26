package org.phalanx.android.ui.screens.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.LinkReason
import org.phalanx.android.data.model.LinkSuggestion
import org.phalanx.android.data.model.Person
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.screens.roomlist.RoomSource
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

/**
 * People whose chats on several networks the user linked, each chat opening its room, and the
 * links suggested for the user to confirm. Nothing is linked without that.
 */
@Composable
fun PeopleScreen(onBack: () -> Unit, onOpenRoom: (String) -> Unit, viewModel: PeopleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failure = stringResource(R.string.people_failed)
    LaunchedEffect(failed) {
        if (failed) {
            snackbar.showSnackbar(failure)
            viewModel.dismissFailure()
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.people_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentGap)
        ) {
            val current = state ?: return@LazyColumn
            if (current.suggestions.isEmpty() && current.people.isEmpty()) {
                item(key = "empty") { Muted(stringResource(R.string.people_empty)) }
            }
            suggestions(current.suggestions, viewModel::link, viewModel::dismiss)
            current.people.forEach { personSection(it, onOpenRoom, viewModel::unlink) }
        }
    }
}

private fun LazyListScope.suggestions(
    suggestions: List<LinkSuggestion>,
    onLink: (LinkSuggestion) -> Unit,
    onDismiss: (LinkSuggestion) -> Unit
) {
    if (suggestions.isEmpty()) return
    item(key = "suggested") { Heading(stringResource(R.string.people_suggested)) }
    itemsIndexed(suggestions, key = { _, pair -> "${pair.first.id} ${pair.second.id}" }) { index, suggestion ->
        SegmentedRow(
            headline = "${chatLabel(suggestion.first)} · ${chatLabel(suggestion.second)}",
            supporting = stringResource(
                if (suggestion.reason == LinkReason.SamePhone) R.string.people_same_phone else R.string.people_same_name
            ),
            shape = segmentedShape(index, suggestions.size),
            leading = { Icon(Symbols.Link, contentDescription = null) },
            trailing = {
                Row {
                    TextButton(onClick = { onDismiss(suggestion) }) { Text(stringResource(R.string.people_not_same)) }
                    TextButton(onClick = { onLink(suggestion) }) { Text(stringResource(R.string.people_link)) }
                }
            }
        )
    }
}

private fun LazyListScope.personSection(person: Person, onOpenRoom: (String) -> Unit, onUnlink: (Person) -> Unit) {
    item(key = "person ${person.chats.first().id}") {
        Row {
            Heading(person.name, Modifier.weight(1f))
            IconButton(onClick = { onUnlink(person) }) {
                Icon(Symbols.Close, contentDescription = stringResource(R.string.people_unlink))
            }
        }
    }
    itemsIndexed(person.chats, key = { _, chat -> chat.id }) { chatIndex, chat ->
        SegmentedRow(
            headline = stringResource(sourceOf(chat).label),
            supporting = chat.name,
            shape = segmentedShape(chatIndex, person.chats.size),
            leading = { SourceIcon(sourceOf(chat)) },
            interaction = Modifier.clickable(role = Role.Button) { onOpenRoom(chat.id) }
        )
    }
}

private fun sourceOf(chat: RoomSummary): RoomSource = chat.bridge?.let(RoomSource::Network) ?: RoomSource.Matrix

@Composable
private fun chatLabel(chat: RoomSummary): String = "${chat.name} (${stringResource(sourceOf(chat).label)})"

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() }
    )
}

@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
