package org.phalanx.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The app's snackbars: the inverse surface, a little squarer than a card, Undo in inverse primary. */
@Composable
internal fun PhalanxSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(data, shape = SnackbarShape)
    }
}

/**
 * Says what just happened and offers to take it back; [onUndo] runs only if Undo was tapped before
 * the snackbar timed out. A newer message replaces this one, which then counts as not undone.
 */
internal suspend fun SnackbarHostState.showUndo(message: String, undo: String, onUndo: () -> Unit) {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

/** A plain report, with nothing to take back. */
internal suspend fun SnackbarHostState.showReport(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message, duration = SnackbarDuration.Short)
}

private val SnackbarShape = RoundedCornerShape(14.dp)
