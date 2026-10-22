package org.phalanx.android.ui.components

import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A [ModalBottomSheet] that lets go of the screen as soon as it starts to hide. The sheet is its
 * own window, which otherwise keeps every touch until its exit animation ends, so a quick tap after
 * back or a scrim tap would land on the closing sheet instead of what is under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState) {
        ReleaseTouchesWhileHiding(sheetState)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseTouchesWhileHiding(state: SheetState) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    // Hidden is also where the sheet starts, before its entry animation: only after it showed.
    var shown by remember { mutableStateOf(false) }
    if (state.currentValue != SheetValue.Hidden) shown = true
    val hiding = shown && state.targetValue == SheetValue.Hidden
    LaunchedEffect(window, hiding) {
        if (hiding) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }
}
