package org.phalanx.android.ui.components

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copies to the clipboard and says so for a moment, in place of a toast that would cover the screen. */
@Stable
internal class CopyState(private val write: (label: String, value: String) -> Unit) {
    var copied by mutableStateOf(false)
        internal set

    fun copy(label: String, value: String) {
        write(label, value)
        copied = true
    }
}

@Composable
internal fun rememberCopyState(): CopyState {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val state = remember {
        CopyState { label, value ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) }
        }
    }
    LaunchedEffect(state.copied) {
        if (state.copied) {
            delay(COPIED_MILLIS)
            state.copied = false
        }
    }
    return state
}

private const val COPIED_MILLIS = 1_600L
