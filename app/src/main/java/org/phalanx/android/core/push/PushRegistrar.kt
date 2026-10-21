package org.phalanx.android.core.push

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.core.matrix.MatrixPushers
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.PushDistributor
import org.phalanx.android.data.model.PushFailure
import org.phalanx.android.data.model.PushStatus
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.data.repository.PushRepository
import org.phalanx.android.di.ApplicationScope
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one UnifiedPush registration for the signed-in account. It registers again every time the
 * session opens, as UnifiedPush asks, and the distributor answers with the endpoint
 * ([onNewEndpoint]), which becomes the homeserver's pusher. A sign-out needs no pusher deletion:
 * the homeserver drops a device's pushers with its access token.
 */
@Singleton
internal class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val active: ActiveClient,
    private val pushers: MatrixPushers,
    private val store: PushStore,
    private val settings: SettingsStore,
    private val notifier: Notifier
) : PushRepository {
    private val lock = Mutex()
    private val mutableStatus = MutableStateFlow<PushStatus>(PushStatus.Off)
    override val status: StateFlow<PushStatus> = mutableStatus

    override val customGateway: Flow<String?> = settings.settings.map { it.pushGateway }.distinctUntilChanged()

    /** Called once from the application; follows the session for the life of the process. */
    fun start() {
        scope.launch {
            active.state
                .filterNot { it is SessionState.Restoring }
                .map { it is SessionState.LoggedIn }
                .distinctUntilChanged()
                .collect { signedIn -> if (signedIn) register() else forget() }
        }
    }

    override fun distributors(): List<PushDistributor> = UnifiedPush.getDistributors(context).map(::describe)

    override suspend fun useDistributor(packageName: String) {
        // Saving a new distributor unregisters from the old one.
        UnifiedPush.saveDistributor(context, packageName)
        register()
    }

    override suspend fun setCustomGateway(url: String?): Boolean {
        if (url != null && !isHttpsUrl(url)) return false
        settings.setPushGateway(url)
        // The distributor answers a new registration with the same endpoint, which re-sets the pusher.
        if (active.state.value is SessionState.LoggedIn) register()
        return true
    }

    override fun dismiss(roomId: String) = notifier.dismiss(roomId)

    fun onNewEndpoint(endpoint: String) {
        scope.launch {
            // The distributor may change the endpoint while the app is not running.
            active.restore()
            lock.withLock {
                if (active.state.value !is SessionState.LoggedIn) return@withLock
                val gateway = settings.current().pushGateway
                    ?: GatewayDiscovery.resolve(endpoint).getOrElse {
                        mutableStatus.value = PushStatus.Failed(PushFailure.Network)
                        return@withLock
                    }
                if (pushers.set(endpoint, gateway).isFailure) {
                    mutableStatus.value = PushStatus.Failed(PushFailure.Homeserver)
                    return@withLock
                }
                val previous = store.load()
                // Best effort: a stale pusher left behind only points at an endpoint nothing listens on.
                if (previous != null && previous.pushKey != endpoint) pushers.delete(previous.pushKey)
                store.save(PushRegistration(endpoint, gateway))
                val distributor = UnifiedPush.getAckDistributor(context)
                mutableStatus.value = if (distributor == null) {
                    PushStatus.ChooseDistributor
                } else {
                    PushStatus.Active(describe(distributor), gateway)
                }
            }
        }
    }

    fun onRegistrationFailed(reason: FailedReason) {
        mutableStatus.value = PushStatus.Failed(
            when (reason) {
                FailedReason.NETWORK -> PushFailure.Network
                FailedReason.ACTION_REQUIRED -> PushFailure.DistributorActionRequired
                FailedReason.INTERNAL_ERROR, FailedReason.VAPID_REQUIRED -> PushFailure.Distributor
            }
        )
    }

    /** The distributor dropped this app (the user removed it there, say); the old pusher is useless now. */
    fun onUnregistered() {
        scope.launch {
            lock.withLock {
                // Also the distributor's answer to the sign-out's own unregister.
                if (active.state.value !is SessionState.LoggedIn) return@withLock
                store.load()?.let { pushers.delete(it.pushKey) }
                store.clear()
                mutableStatus.value = PushStatus.ChooseDistributor
            }
        }
    }

    private suspend fun register() = lock.withLock {
        val distributor = UnifiedPush.getAckDistributor(context) ?: pickDefault()
        if (distributor == null) return@withLock
        if (mutableStatus.value !is PushStatus.Active) mutableStatus.value = PushStatus.Registering
        UnifiedPush.register(context)
    }

    /** Saves the phone's default distributor, or the only one installed; null when the user must choose. */
    private fun pickDefault(): String? {
        val picked = when (val resolved = UnifiedPush.resolveDefaultDistributor(context)) {
            is ResolvedDistributor.Found -> resolved.packageName
            ResolvedDistributor.ToSelect -> UnifiedPush.getDistributors(context).singleOrNull()
            ResolvedDistributor.NoneAvailable -> null
        }
        if (picked == null) {
            mutableStatus.value = if (UnifiedPush.getDistributors(context).isEmpty()) {
                PushStatus.NoDistributor
            } else {
                PushStatus.ChooseDistributor
            }
        } else {
            UnifiedPush.saveDistributor(context, picked)
        }
        return picked
    }

    private suspend fun forget() = lock.withLock {
        UnifiedPush.unregister(context)
        store.clear()
        notifier.dismissAll()
        mutableStatus.value = PushStatus.Off
    }

    private fun describe(packageName: String): PushDistributor {
        val label = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        return PushDistributor(packageName, label)
    }

    private fun isHttpsUrl(url: String): Boolean = try {
        URI(url).let { it.scheme == "https" && !it.host.isNullOrEmpty() }
    } catch (_: URISyntaxException) {
        false
    }
}
