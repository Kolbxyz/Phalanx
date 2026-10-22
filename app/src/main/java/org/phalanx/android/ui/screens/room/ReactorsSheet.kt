package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.ReactionSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.ModalSheet

/** Every reaction to a message as a tab; each tab lists who reacted with it, in the order they did. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReactorsSheet(reactors: Reactors, onDismiss: () -> Unit) {
    ModalSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.timeline_reactors_heading),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        val reactions = reactors.reactions
        val pager = rememberPagerState(initialPage = reactions.indexOfFirst { it.key == reactors.initialKey }.coerceAtLeast(0)) {
            reactions.size
        }
        val scope = rememberCoroutineScope()
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 16.dp, containerColor = Color.Transparent) {
            reactions.forEachIndexed { index, reaction ->
                val name = reactionName(reaction)
                val description = pluralStringResource(R.plurals.timeline_reactors_title, reaction.count, name, reaction.count)
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index, animationSpec = spatial) } },
                    text = {
                        ReactionKey(reaction, 20.dp, LocalTextStyle.current, suffix = reaction.count.toString())
                    },
                    modifier = Modifier.semantics { contentDescription = description }
                )
            }
        }
        HorizontalPager(pager, Modifier.navigationBarsPadding(), verticalAlignment = Alignment.Top) { page ->
            val members = reactors.members
            if (members == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { LoadingIndicator() }
            } else {
                // Someone the room no longer knows still shows, as their Matrix ID.
                ReactorList(reactions[page].senderIds.map { members[it] ?: Member(it, it, null) })
            }
        }
    }
}

@Composable
private fun ReactorList(members: List<Member>) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(members, key = { it.userId }) { member ->
            ListItem(
                headlineContent = { Text(member.name, maxLines = 1) },
                supportingContent = { Text(member.userId, maxLines = 1) },
                leadingContent = { MatrixAvatar(member.name, member.avatar, 40.dp) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }
    }
}

/** A message's reactions, [initialKey] first shown; [members] by user ID, null while they load. */
@Immutable
data class Reactors(val reactions: List<ReactionSummary>, val initialKey: String, val members: Map<String, Member>?)
