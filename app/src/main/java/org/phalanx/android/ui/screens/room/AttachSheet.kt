package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

/** The composer's "+": each choice closes the sheet and hands over to the system picker or camera. */
@Composable
internal fun AttachSheet(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    /** Null hides the choice: Emoji Kitchen is off. */
    onSticker: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    ModalSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Choice(Symbols.PhotoLibrary, R.string.composer_attach_gallery, MaterialShapes.Cookie9Sided.toShape(),
                colors.primaryContainer, colors.onPrimaryContainer, onGallery)
            Choice(Symbols.PhotoCamera, R.string.composer_attach_camera, MaterialShapes.Sunny.toShape(),
                colors.secondaryContainer, colors.onSecondaryContainer, onPhoto)
            Choice(Symbols.Videocam, R.string.composer_attach_video, MaterialShapes.Pill.toShape(),
                colors.tertiaryContainer, colors.onTertiaryContainer, onVideo)
            Choice(Symbols.InsertDriveFile, R.string.composer_attach_file,
                MaterialShapes.Clover4Leaf.toShape(), colors.surfaceContainerHighest, colors.onSurface, onFile)
            if (onSticker != null) {
                Choice(Symbols.Sticker, R.string.composer_attach_sticker, MaterialShapes.Puffy.toShape(),
                    colors.errorContainer, colors.onErrorContainer, onSticker)
            }
        }
    }
}

@Composable
private fun Choice(
    icon: ImageVector,
    @StringRes label: Int,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}
