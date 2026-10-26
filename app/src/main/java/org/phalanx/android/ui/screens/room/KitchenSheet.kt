package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.phalanx.android.R
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.ui.components.ModalSheet

/**
 * The Emoji Kitchen picker, offered only once the user turned it on: [onPick] sends the mix from
 * the composer, or reacts with it from a message's actions.
 */
@Composable
internal fun KitchenSheet(
    onDismiss: () -> Unit,
    onPick: (KitchenMix) -> Unit,
    onStarFailed: () -> Unit,
    kitchen: KitchenViewModel = hiltViewModel()
) {
    ModalSheet(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.kitchen_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            KitchenTab(kitchen, onPick, onStarFailed)
        }
    }
}
