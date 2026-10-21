package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.Device

data class AccountProfile(val userId: String, val displayName: String?, val avatarUrl: String?) {
    val hasDisplayName: Boolean get() = !displayName.isNullOrBlank()

    /** What to call the account on screen: its display name, else its Matrix ID. */
    val label: String get() = displayName?.takeIf { hasDisplayName } ?: userId
}

data class AccountDetails(
    val deviceId: String,
    val homeserver: String,
    /** Signed in through the server's OAuth provider, which then owns the account and device pages. */
    val managedByProvider: Boolean,
    /** The server lets this app deactivate the account itself (with the password). */
    val canDeactivate: Boolean
)

/** Pages of the OAuth provider's account management site. */
enum class AccountPage { Profile, Devices, Deactivate }

/**
 * The signed-in account. Every call fails or returns null when no one is signed in. Failures the
 * UI can explain arrive as [org.phalanx.android.data.model.AccountException].
 */
interface AccountRepository {
    /** The own profile, updated live as it changes from this or another device. Null while signed out. */
    val profile: StateFlow<AccountProfile?>

    /** The own avatar as image bytes about [sizePx] square, or null when none is set or it can't load. */
    fun avatar(sizePx: Int): Flow<ByteArray?>

    suspend fun details(): AccountDetails?

    suspend fun setDisplayName(name: String): Result<Unit>

    /** Uploads [jpeg] and makes it the avatar. */
    suspend fun setAvatar(jpeg: ByteArray): Result<Unit>

    suspend fun removeAvatar(): Result<Unit>

    /** The provider's page for [page], or null when the account is not provider-managed. */
    suspend fun manageUrl(page: AccountPage): String?

    /** Password accounts only; provider-managed accounts list devices on the provider's site. */
    suspend fun devices(): Result<List<Device>>

    suspend fun deleteDevice(deviceId: String, password: String): Result<Unit>

    /**
     * Permanently deactivates the account, then forgets it on this device. [erase] asks the server
     * to also forget the account's messages where it can.
     */
    suspend fun deactivate(password: String, erase: Boolean): Result<Unit>

    /** Logs this device out on the server (best effort) and deletes everything it stored locally. */
    suspend fun signOut()
}
