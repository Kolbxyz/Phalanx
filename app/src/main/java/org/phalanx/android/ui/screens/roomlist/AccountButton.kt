package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.InitialAvatar

@Composable
internal fun AccountButton(label: String, avatar: ImageBitmap?, onClick: () -> Unit) {
    val description = stringResource(R.string.roomlist_account)
    IconButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp).size(48.dp)) {
        InitialAvatar(
            name = label,
            shape = CircleShape,
            size = 40.dp,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            image = avatar,
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}
