package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails
import org.phalanx.android.data.repository.VerificationStep

/**
 * Drives one emoji verification of this session against another of the account's devices:
 * request, wait for acceptance, start SAS, compare emoji, confirm. Only outgoing requests.
 */
internal class VerificationFlow(private val scope: CoroutineScope) {
    private val mutableStep = MutableStateFlow<VerificationStep>(VerificationStep.Idle)
    val step: StateFlow<VerificationStep> = mutableStep

    private var controller: SessionVerificationController? = null

    suspend fun start(controller: SessionVerificationController) {
        this.controller = controller
        controller.setDelegate(delegate)
        mutableStep.value = VerificationStep.Requested
        attempt { controller.requestDeviceVerification() }
    }

    suspend fun confirm() = attempt { controller?.approveVerification() }

    suspend fun cancel() {
        attempt { controller?.cancelVerification() }
        mutableStep.value = VerificationStep.Idle
    }

    fun reset() {
        mutableStep.value = VerificationStep.Idle
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: ClientException) {
            mutableStep.value = VerificationStep.Failed
        }
    }

    private val delegate = object : SessionVerificationControllerDelegate {
        override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) = Unit

        override fun didAcceptVerificationRequest() {
            mutableStep.value = VerificationStep.Starting
            scope.launch { attempt { controller?.startSasVerification() } }
        }

        override fun didStartSasVerification() {
            mutableStep.value = VerificationStep.Starting
        }

        override fun didReceiveVerificationData(data: SessionVerificationData) {
            val emojis = (data as? SessionVerificationData.Emojis)?.emojis ?: return
            val pairs = emojis.map { emoji -> emoji.use { it.symbol() to it.description() } }
            mutableStep.value = VerificationStep.CompareEmoji(pairs)
        }

        override fun didFail() {
            mutableStep.value = VerificationStep.Failed
        }

        override fun didCancel() {
            mutableStep.value = VerificationStep.Failed
        }

        override fun didFinish() {
            mutableStep.value = VerificationStep.Done
        }
    }
}
