package org.phalanx.android.ui.screens.people

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.components.BackButton

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val current = state ?: return@LazyColumn
            if (current.suggestions.isEmpty() && current.people.isEmpty()) {
                item(key = "empty") { Muted(stringResource(R.string.people_empty)) }
            }
            suggestions(current.suggestions, viewModel::link, viewModel::dismiss)
            people(current.people, onOpenRoom, viewModel::unlink)
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
    items(suggestions, key = { pair -> "${pair.first.id} ${pair.second.id}" }) { suggestion ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OverlappingAvatars(suggestion.first, suggestion.second)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${chatLabel(suggestion.first)} · ${chatLabel(suggestion.second)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(reasonText(suggestion.reason)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { onDismiss(suggestion) }) { Text(stringResource(R.string.people_not_same)) }
                    Button(onClick = { onLink(suggestion) }) { Text(stringResource(R.string.people_link)) }
                }
            }
        }
    }
}

private fun LazyListScope.people(people: List<Person>, onOpenRoom: (String) -> Unit, onUnlink: (Person) -> Unit) {
    if (people.isEmpty()) return
    items(people, key = { person -> "person ${person.chats.first().id}" }) { person ->
        PersonCard(person, onOpenRoom, onUnlink)
    }
}

@StringRes
private fun reasonText(reason: LinkReason) =
    if (reason == LinkReason.SamePhone) R.string.people_same_phone else R.string.people_same_name

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
