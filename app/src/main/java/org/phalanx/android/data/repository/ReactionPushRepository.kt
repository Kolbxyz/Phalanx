package org.phalanx.android.data.repository

/**
 * The account's reaction push rule. It is account-wide: every client signed in to the account
 * follows it, not only this one.
 */
interface ReactionPushRepository {
    /** Whether a reaction should wake this account's pushers at all. */
    suspend fun setNotify(enabled: Boolean): Result<Unit>
}
