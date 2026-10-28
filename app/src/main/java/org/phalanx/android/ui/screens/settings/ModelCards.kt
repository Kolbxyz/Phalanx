package org.phalanx.android.ui.screens.settings

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/**
 * The models one feature accepts, a card each: the installed one is filled, the others download in
 * one tap. Below, the transfer under way and, for a phone offline, importing a downloaded file.
 */
@Composable
internal fun ModelCards(
    models: List<KnownModel>,
    installed: String?,
    transfer: ModelTransfer,
    icon: ImageVector,
    @StringRes rejected: Int,
    actions: ModelActions
) {
    // The files have no registered type, so the picker cannot filter on it.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onImport(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
            ModelCard(
                title = stringResource(model.role),
                detail = model.name,
                bytes = model.maxBytes,
                icon = icon,
                installed = installed == model.name,
                busy = transfer is ModelTransfer.Copying,
                onDownload = { actions.onDownload(model) },
                onRemove = actions.onRemove
            )
        }
        TransferStatus(transfer, rejected, actions.onCancel, onImport = { picker.launch(arrayOf("*/*")) })
    }
}

/** What the model cards do, from their view model. */
internal class ModelActions(
    val onDownload: (KnownModel) -> Unit,
    val onImport: (Uri) -> Unit,
    val onCancel: () -> Unit,
    val onRemove: () -> Unit
)

/**
 * One downloadable model, or language pair: what it is for first, the file below, filled and
 * checked once installed, with its size on the download button.
 */
@Composable
internal fun ModelCard(
    title: String,
    detail: String,
    bytes: Long,
    icon: ImageVector,
    installed: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, bytes)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (installed) colors.primaryContainer else colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (installed) Symbols.Check else icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (installed) {
                        Text(
                            stringResource(R.string.settings_ai_model_installed, size),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary
                        )
                    }
                }
            }
            if (installed) {
                OutlinedButton(onClick = onRemove, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                    Icon(Symbols.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.settings_ai_model_remove), Modifier.padding(start = 8.dp))
                }
            } else {
                Button(onClick = onDownload, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                    Icon(Symbols.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.settings_ai_model_download, size), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** The transfer under way with its cancel button, how the last one failed, and importing a file instead. */
@Composable
internal fun TransferStatus(
    transfer: ModelTransfer,
    @StringRes rejected: Int,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    when (transfer) {
        is ModelTransfer.Copying -> Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_ai_model_checking), style = MaterialTheme.typography.titleSmall)
                LinearWavyProgressIndicator(progress = { transfer.progress }, modifier = Modifier.fillMaxWidth())
                TextButton(
                    onClick = onCancel,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.settings_ai_model_cancel))
                }
            }
        }
        ModelTransfer.Rejected, ModelTransfer.Failed -> SegmentedRow(
            headline = stringResource(
                if (transfer == ModelTransfer.Rejected) rejected else R.string.settings_ai_model_failed
            ),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
        ModelTransfer.Idle -> Unit
    }
    if (transfer !is ModelTransfer.Copying) {
        TextButton(onClick = onImport, shapes = ButtonDefaults.shapes()) {
            Icon(Symbols.InsertDriveFile, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.settings_ai_model_from_file), Modifier.padding(start = 8.dp))
        }
    }
}
