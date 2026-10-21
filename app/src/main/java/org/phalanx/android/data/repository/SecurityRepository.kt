package org.phalanx.android.data.repository

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/** Whether this session can read encrypted history, and the recovery key that lets it. */
interface SecurityRepository {
    /** Null until the SDK has reported, and while signed out. */
    val status: StateFlow<SecurityStatus?>

    /** The emoji comparison with another of this account's devices, step by step. */
    val verification: StateFlow<VerificationStep>

    /** Creates the account's recovery key and backs up message keys; returns the key to show once. */
    suspend fun setUpRecovery(): Result<String>

    /** Replaces the recovery key; the old one stops working. Returns the new key to show once. */
    suspend fun changeRecoveryKey(): Result<String>

    /** Verifies this session with the key and restores the message keys it unlocks. */
    suspend fun enterRecoveryKey(key: String): Result<Unit>

    suspend fun startVerification()

    /** The emoji matched on both screens. */
    suspend fun confirmVerification()

    /** The emoji differed, or the user gave up. */
    suspend fun cancelVerification()

    fun resetVerification()
}

@Immutable
data class SecurityStatus(val verified: Boolean, val recovery: RecoveryStatus)

enum class RecoveryStatus {
    /** No recovery key yet: losing every device loses encrypted history. */
    Off,

    /** Set up, and this session has it. */
    On,

    /** Set up on another device; this one hasn't entered the key yet. */
    Incomplete,
    Unknown
}

@Immutable
sealed interface VerificationStep {
    data object Idle : VerificationStep

    /** Waiting for the other device to accept. */
    data object Requested : VerificationStep

    /** Accepted; the emoji are being agreed. */
    data object Starting : VerificationStep

    data class CompareEmoji(val emoji: List<Pair<String, String>>) : VerificationStep

    data object Done : VerificationStep

    data object Failed : VerificationStep
}
