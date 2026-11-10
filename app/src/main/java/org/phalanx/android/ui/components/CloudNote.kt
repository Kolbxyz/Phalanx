package org.phalanx.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R

/** Shown wherever Claude writes in place of the model on this phone: the messages are sent out. */
@Composable
internal fun CloudNote() {
    Text(
        stringResource(R.string.cloud_note),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
