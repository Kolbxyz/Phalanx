package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phalanx.android.data.model.SessionState

@Singleton
class SyncController @Inject internal constructor(
    private val active: ActiveClient,
    private val roomWatch: RoomWatch
) {
    /**
     * Keeps the SDK syncing for exactly as long as the caller stays active, and only while an
     * account is signed in. Meant to run under `repeatOnLifecycle(STARTED)`: syncing in the
     * background is push's job, never a polling loop's. The typing and read alerts the user asked
     * for are watched for meanwhile, since only a running sync receives them.
     */
    suspend fun syncWhileActive() {
        active.state
            .map { it is SessionState.LoggedIn }
            .distinctUntilChanged()
            .collectLatest { signedIn ->
                if (signedIn) {
                    active.startSync()
                    try {
                        coroutineScope {
                            launch { roomWatch.run() }
                            awaitCancellation()
                        }
                    } finally {
                        withContext(NonCancellable) { active.stopSync() }
                    }
                }
            }
    }
}
