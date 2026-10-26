package org.phalanx.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * How many screens on show ask that nothing of them be captured. The activity keeps its window
 * secure while any does: no screenshot, no screen recording and a blank card in Recents.
 */
class SecureScreens {
    private val count = MutableStateFlow(0)

    val active: Flow<Boolean> = count.map { it > 0 }.distinctUntilChanged()

    internal fun acquire() = count.update { it + 1 }

    internal fun release() = count.update { it - 1 }
}

val LocalSecureScreens = staticCompositionLocalOf<SecureScreens> { error("No SecureScreens provided") }

/** While this is composed and [enabled], the window can't be captured. */
@Composable
fun SecureScreen(enabled: Boolean) {
    val screens = LocalSecureScreens.current
    DisposableEffect(screens, enabled) {
        if (enabled) screens.acquire()
        onDispose { if (enabled) screens.release() }
    }
}
