package org.phalanx.android.core.matrix

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.Encryption
import org.matrix.rustcomponents.sdk.RecoveryException
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.UserIdentity
import org.matrix.rustcomponents.sdk.VerificationState
import org.matrix.rustcomponents.sdk.VerificationStateListener
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.data.repository.RecoveryStatus
import org.phalanx.android.data.repository.SecurityRepository
import org.phalanx.android.data.repository.SecurityStatus
import org.phalanx.android.data.repository.VerificationStep
import org.phalanx.android.di.ApplicationScope

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RustSecurityRepository @Inject constructor(
    private val active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) : SecurityRepository {
    private val flow = VerificationFlow(scope)

    override val status: StateFlow<SecurityStatus?> = active.syncService
        .flatMapLatest { active.client()?.encryption()?.statusUpdates() ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val verification: StateFlow<VerificationStep> = flow.step

    override suspend fun setUpRecovery(): Result<String> = withEncryption { encryption ->
        // Returns once the key exists; message keys keep uploading in the background.
        encryption.enableRecovery(waitForBackupsToUpload = false, passphrase = null, progressListener = NoProgress)
    }

    override suspend fun changeRecoveryKey(): Result<String> = withEncryption { it.resetRecoveryKey() }

    override suspend fun enterRecoveryKey(key: String): Result<Unit> = withEncryption { it.recover(key.trim()) }

    override suspend fun startVerification() {
        val client = active.client() ?: return
        withContext(Dispatchers.IO) {
            try {
                flow.start(client.getSessionVerificationController())
            } catch (_: ClientException) {
                flow.reset()
            }
        }
    }

    override suspend fun confirmVerification() = withContext(Dispatchers.IO) { flow.confirm() }

    override suspend fun cancelVerification() = withContext(Dispatchers.IO) { flow.cancel() }

    override fun resetVerification() = flow.reset()

    override suspend fun trustOf(userId: String): UserTrust? = withEncryption { encryption ->
        // Asks the server when the identity isn't cached yet, as for someone never met before.
        val identity: UserIdentity? = encryption.userIdentity(userId, true)
        identity?.use {
            when {
                it.hasVerificationViolation() -> UserTrust.Changed
                it.isVerified() -> UserTrust.Verified
                else -> UserTrust.Unverified
            }
        }
    }.getOrNull()

    private suspend fun <T> withEncryption(block: suspend (Encryption) -> T): Result<T> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(IOException("Signed out"))
        try {
            Result.success(block(client.encryption()))
        } catch (e: RecoveryException) {
            Result.failure(e)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    private object NoProgress : EnableRecoveryProgressListener {
        override fun onUpdate(status: EnableRecoveryProgress) = Unit
    }
}

/** Both states, current first, then on every change. */
private fun Encryption.statusUpdates(): Flow<SecurityStatus> = combine(
    callbackFlow {
        trySend(verificationState())
        val handle = verificationStateListener(
            object : VerificationStateListener {
                override fun onUpdate(status: VerificationState) {
                    trySend(status)
                }
            }
        )
        awaitClose { handle.cancel(); handle.destroy() }
    },
    callbackFlow {
        trySend(recoveryState())
        val handle = recoveryStateListener(
            object : RecoveryStateListener {
                override fun onUpdate(status: RecoveryState) {
                    trySend(status)
                }
            }
        )
        awaitClose { handle.cancel(); handle.destroy() }
    }
) { verification, recovery ->
    SecurityStatus(
        verified = verification == VerificationState.VERIFIED,
        recovery = when (recovery) {
            RecoveryState.ENABLED -> RecoveryStatus.On
            RecoveryState.DISABLED -> RecoveryStatus.Off
            RecoveryState.INCOMPLETE -> RecoveryStatus.Incomplete
            RecoveryState.UNKNOWN -> RecoveryStatus.Unknown
        }
    )
}
