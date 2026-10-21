package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.matrix.rustcomponents.sdk.AccountManagementAction
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ErrorKind
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.ProfileListener
import org.matrix.rustcomponents.sdk.UserProfile
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.AccountException
import org.phalanx.android.data.model.AccountFailure
import org.phalanx.android.data.model.Device
import org.phalanx.android.data.repository.AccountDetails
import org.phalanx.android.data.repository.AccountPage
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.data.repository.AccountRepository
import org.phalanx.android.di.ApplicationScope
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RustAccountRepository @Inject constructor(
    private val active: ActiveClient,
    private val devicesApi: DevicesApi,
    private val sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope
) : AccountRepository {
    // Keyed on the sync service, which exists exactly while an account is open. Held for the whole
    // session so no screen ever starts from an empty profile.
    override val profile: StateFlow<AccountProfile?> = active.syncService
        .flatMapLatest { service ->
            val client = active.client()
            if (service == null || client == null) flowOf(null) else client.ownProfile(cachedName())
        }
        .onEach { it?.let(::rememberName) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Only refetched when the URL changes, not on every display-name update.
    override fun avatar(sizePx: Int): Flow<ByteArray?> = profile
        .map { it?.avatarUrl }
        .distinctUntilChanged()
        .mapLatest { url -> url?.let { fetchThumbnail(it, sizePx) } }

    override suspend fun details(): AccountDetails? = io {
        val client = active.client() ?: return@io null
        try {
            AccountDetails(
                deviceId = client.deviceId(),
                homeserver = client.homeserver(),
                managedByProvider = client.session().oauthData != null,
                canDeactivate = client.canDeactivateAccount()
            )
        } catch (_: ClientException) {
            null
        }
    }

    override suspend fun setDisplayName(name: String): Result<Unit> = withClient { it.setDisplayName(name.trim()) }

    override suspend fun setAvatar(jpeg: ByteArray): Result<Unit> = withClient { it.uploadAvatar(JPEG_MIME, jpeg) }

    override suspend fun removeAvatar(): Result<Unit> = withClient { it.removeAvatar() }

    override suspend fun manageUrl(page: AccountPage): String? = io {
        val client = active.client() ?: return@io null
        val action = when (page) {
            AccountPage.Profile -> AccountManagementAction.Profile
            AccountPage.Devices -> AccountManagementAction.DevicesList
            AccountPage.Deactivate -> AccountManagementAction.AccountDeactivate
        }
        try {
            client.accountUrl(action)
        } catch (_: ClientException) {
            null
        }
    }

    override suspend fun devices(): Result<List<Device>> = try {
        Result.success(devicesApi.list())
    } catch (e: IOException) {
        Result.failure(AccountException(AccountFailure.Unknown, e))
    } catch (e: SerializationException) {
        Result.failure(AccountException(AccountFailure.Unknown, e))
    }

    override suspend fun deleteDevice(deviceId: String, password: String): Result<Unit> = try {
        when (devicesApi.delete(deviceId, password)) {
            DevicesApi.DeleteResult.Deleted -> Result.success(Unit)
            DevicesApi.DeleteResult.WrongPassword -> Result.failure(AccountException(AccountFailure.WrongPassword))
        }
    } catch (e: IOException) {
        Result.failure(AccountException(AccountFailure.Unknown, e))
    } catch (e: SerializationException) {
        Result.failure(AccountException(AccountFailure.Unknown, e))
    }

    override suspend fun deactivate(password: String, erase: Boolean): Result<Unit> {
        val result = withClient { it.deactivateAccount(password, erase) }
        // The account no longer exists on the server; logout will fail, which signOut tolerates,
        // and everything stored for it here is deleted.
        if (result.isSuccess) active.signOut()
        return result
    }

    override suspend fun signOut() = active.signOut()

    private fun cachedName(): String? = sessionStore.load()?.displayName

    private fun rememberName(profile: AccountProfile) {
        if (sessionStore.load()?.displayName == profile.displayName) return
        sessionStore.update { it.copy(displayName = profile.displayName) }
    }

    private suspend fun fetchThumbnail(url: String, sizePx: Int): ByteArray? = io {
        val client = active.client() ?: return@io null
        try {
            MediaSource.fromUrl(url).use { client.getMediaThumbnail(it, sizePx.toULong(), sizePx.toULong()) }
        } catch (_: ClientException) {
            null
        }
    }

    private suspend fun withClient(block: suspend (Client) -> Unit): Result<Unit> = io {
        val client = active.client() ?: return@io Result.failure(AccountException(AccountFailure.Unknown))
        try {
            block(client)
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(AccountException(e.toAccountFailure(), e))
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val JPEG_MIME = "image/jpeg"
    }
}

private fun ClientException.toAccountFailure(): AccountFailure =
    if (this is ClientException.MatrixApi && kind is ErrorKind.Forbidden) AccountFailure.WrongPassword else AccountFailure.Unknown

/**
 * The last known profile first, so the name shows without waiting for the network (and stays
 * shown offline), then what the server says, then live updates.
 */
private fun Client.ownProfile(cachedName: String?): Flow<AccountProfile?> = callbackFlow {
    val handle = try {
        val userId = userId()
        trySend(AccountProfile(userId, cachedName, avatarUrl = cachedAvatarUrl()))
        try {
            trySend(AccountProfile(userId, displayName(), cachedAvatarUrl()))
        } catch (_: ClientException) {
            // Offline: the live listener below fills the name in once the server answers.
        }
        subscribeToOwnProfile(
            object : ProfileListener {
                override fun onUpdate(profile: UserProfile) {
                    trySend(AccountProfile(profile.userId, profile.displayName, profile.avatarUrl))
                }
            }
        )
    } catch (_: ClientException) {
        // The client is closing under a sign-out; the session state change replaces this screen.
        null
    }
    awaitClose {
        handle?.cancel()
        handle?.destroy()
    }
}.flowOn(Dispatchers.IO)
