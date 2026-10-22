package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MemberRole
import org.phalanx.android.data.model.Participant
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape

/** How many members show before "Show all". */
private const val MEMBERS_CAP = 5

/** [members] null while they load; the first [MEMBERS_CAP] until expanded, admins and moderators first. */
internal fun LazyListScope.membersSection(members: List<Participant>?, showAll: Boolean, onShowAll: () -> Unit) {
    item { DetailsSectionTitle(R.string.details_members_title) }
    if (members.isNullOrEmpty()) {
        item { EmptyNote(stringResource(if (members == null) R.string.loading else R.string.details_members_none)) }
        return
    }
    val shown = if (showAll) members else members.take(MEMBERS_CAP)
    itemsIndexed(shown, key = { _, it -> "member-${it.member.userId}" }) { index, participant ->
        val member = participant.member
        SegmentedRow(
            headline = member.name,
            supporting = member.userId.takeIf { it != member.name },
            shape = segmentedShape(index, shown.size),
            leading = { MatrixAvatar(member.name, member.avatar, 40.dp) },
            trailing = roleLabel(participant)?.let { label ->
                {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        Spacer(Modifier.height(2.dp))
    }
    if (!showAll && members.size > MEMBERS_CAP) {
        item(key = "members-all") {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) {
                FilledTonalButton(onClick = onShowAll) { Text(stringResource(R.string.details_show_all)) }
            }
        }
    }
}

private fun roleLabel(participant: Participant): Int? = when {
    participant.invited -> R.string.details_member_invited
    participant.role == MemberRole.Admin -> R.string.details_member_admin
    participant.role == MemberRole.Moderator -> R.string.details_member_moderator
    else -> null
}
