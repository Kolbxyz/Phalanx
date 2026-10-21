package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.MatrixAvatar

/** The room's avatar and name; both come from the room list, so they show before the timeline. */
@Composable
internal fun RoomTopBar(header: RoomSummary?, fallbackTitle: String?, onBack: () -> Unit, onOpenDetails: () -> Unit) {
    val title = header?.name ?: fallbackTitle.orEmpty()
    TopAppBar(
        title = {
            // The whole title opens the conversation's details, as in other messengers.
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.room_action_details), onClick = onOpenDetails)
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title.isNotEmpty()) MatrixAvatar(title, header?.avatar, 40.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = { BackButton(onBack) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}
