package org.phalanx.android.core.ai

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [block], a native call no suspension point can interrupt, so that cancelling the coroutine
 * calls [cancel] and stops it. [cancel] is never called once [block] has returned, so a handle
 * freed after this returns is never touched.
 */
internal suspend fun <T> cancellableNative(cancel: () -> Unit, block: CoroutineScope.() -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val watcher = launch {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) cancel()
        }
    }
    try {
        block()
    } finally {
        finished.set(true)
        withContext(NonCancellable) { watcher.cancelAndJoin() }
    }
}
