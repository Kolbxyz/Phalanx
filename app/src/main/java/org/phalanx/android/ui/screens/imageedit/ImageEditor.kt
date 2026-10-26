package org.phalanx.android.ui.screens.imageedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phalanx.android.R
import org.phalanx.android.core.media.ImageEdits
import org.phalanx.android.core.media.decodeImageFile
import org.phalanx.android.core.media.withEdits
import org.phalanx.android.ui.theme.Symbols

// Editing a scaled copy keeps every stroke instant; the full picture is edited once, on Done.
private const val PREVIEW_SIDE = 1280

/** Full-screen editor for a picture waiting to be sent. [onDone] gets what to apply to the real file. */
@Composable
internal fun ImageEditor(file: File, onDone: (ImageEdits) -> Unit, onFailed: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val preview by produceState<android.graphics.Bitmap?>(null, file) {
            value = withContext(Dispatchers.IO) {
                try {
                    decodeImageFile(file, PREVIEW_SIDE)
                } catch (_: IOException) {
                    null
                }
            }
            if (value == null) onFailed()
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val source = preview
            if (source == null) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            } else {
                EditorContent(source, onDone, onDismiss)
            }
        }
    }
}

@Composable
private fun EditorContent(source: android.graphics.Bitmap, onDone: (ImageEdits) -> Unit, onDismiss: () -> Unit) {
    var edits by remember { mutableStateOf(ImageEdits()) }
    var tool by remember { mutableStateOf(EditorTool.Draw) }
    var color by remember { mutableStateOf(InkColors.first()) }
    // The last picture stays up while the next is drawn, so a stroke never flashes.
    val shown by produceState<ImageBitmap?>(null, source, edits.marks, edits.turns, edits.mirrored) {
        value = withContext(Dispatchers.Default) { source.withEdits(edits).asImageBitmap() }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Symbols.Close, contentDescription = stringResource(R.string.editor_cancel))
            }
            Row {
                IconButton(
                    onClick = { edits = edits.copy(marks = edits.marks.dropLast(1)) },
                    enabled = edits.marks.isNotEmpty()
                ) {
                    Icon(Symbols.Undo, contentDescription = stringResource(R.string.editor_undo))
                }
                IconButton(onClick = { onDone(edits) }) {
                    Icon(Symbols.Check, contentDescription = stringResource(R.string.editor_done))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            shown?.let { image ->
                EditorCanvas(
                    image = image,
                    edits = edits,
                    tool = tool,
                    inkColor = color,
                    onMark = { edits = edits.copy(marks = edits.marks + it) },
                    onCrop = { edits = edits.copy(crop = it) }
                )
            }
        }
        EditorToolbar(
            tool = tool,
            onTool = { tool = it },
            color = color,
            onColor = { color = it },
            onRotate = { edits = edits.rotated() },
            onFlip = { edits = edits.mirroredAcross() }
        )
    }
}
