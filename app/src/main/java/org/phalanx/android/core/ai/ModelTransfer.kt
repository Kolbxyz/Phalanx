package org.phalanx.android.core.ai

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a model's download or import stands. */
sealed interface ModelTransfer {
    data object Idle : ModelTransfer

    /** [progress] goes from 0 to 1. */
    data class Copying(val progress: Float) : ModelTransfer

    /** The file is not one this build accepts; it was deleted. */
    data object Rejected : ModelTransfer

    /** The file could not be read or downloaded. */
    data object Failed : ModelTransfer
}

/**
 * Runs one download or import at a time for a model store, in the app's [scope]: a model is
 * hundreds of megabytes, which must not stop when the user leaves the settings screen.
 */
internal class TransferRunner(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<ModelTransfer>(ModelTransfer.Idle)
    val state: StateFlow<ModelTransfer> = mutableState.asStateFlow()
    private var job: Job? = null

    /** Starts [block], which reports its progress and throws on failure, unless one is running. */
    fun start(block: suspend (onProgress: (Float) -> Unit) -> Unit) {
        if (mutableState.value is ModelTransfer.Copying) return
        mutableState.value = ModelTransfer.Copying(0f)
        job = scope.launch {
            mutableState.value = try {
                block { mutableState.value = ModelTransfer.Copying(it) }
                ModelTransfer.Idle
            } catch (_: ModelRejectedException) {
                ModelTransfer.Rejected
            } catch (_: IOException) {
                ModelTransfer.Failed
            }
        }
    }

    /** Stops the running transfer; its partial file is deleted. */
    fun cancel() {
        job?.cancel()
        mutableState.value = ModelTransfer.Idle
    }
}
