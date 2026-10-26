package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.phalanx.android.data.model.SessionState

@Singleton
class SyncController @Inject internal constructor(private val active: ActiveClient) {
    /**
     * Keeps the SDK syncing for exactly as long as the caller stays active, and only while an
     * account is signed in. Meant to run under `repeatOnLifecycle(STARTED)`: syncing in the
     * background is push's job, never a polling loop's.
     */
    suspend fun syncWhileActive() {
        active.state
            .map { it is SessionState.LoggedIn }
            .distinctUntilChanged()
            .collectLatest { signedIn ->
                if (signedIn) {
                    active.startSync()
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) { active.stopSync() }
                    }
                }
            }
    }
}
