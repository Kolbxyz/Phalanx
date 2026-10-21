package org.phalanx.android.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.components.OpenFileEffect
import org.phalanx.android.ui.components.TopNoticeHost
import org.phalanx.android.ui.theme.Symbols

/**
 * Searches chats by name and messages, photos, videos and files through the on-device index.
 * Shown over the chat list; back or the arrow closes it.
 */
@Composable
fun SearchOverlay(onClose: () -> Unit, onOpenRoom: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val close = {
        viewModel.reset()
        onClose()
    }
    BackHandler(onBack = close)
    OpenFileEffect(openRequest, viewModel::onOpened, viewModel::onNoApp)

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding()) {
            SearchField(query, viewModel::onQueryChange, onBack = close)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(SearchCategory.entries, key = { it.name }) { entry ->
                    FilterChip(
                        selected = entry == category,
                        onClick = { viewModel.onCategory(entry) },
                        label = { Text(stringResource(entry.label)) }
                    )
                }
            }
            SearchResultsList(
                query = query,
                category = category,
                results = results,
                onOpenRoom = { roomId ->
                    close()
                    onOpenRoom(roomId)
                },
                onOpenFile = viewModel::openFile,
                onShowCategory = viewModel::onCategory,
                onLoadMore = viewModel::loadMore
            )
        }
        TopNoticeHost(notice, onDismiss = viewModel::dismissNotice)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        TextField(
            value = query,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = CircleShape,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Symbols.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            } else {
                null
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus)
        )
    }
}
