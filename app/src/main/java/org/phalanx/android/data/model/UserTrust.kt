package org.phalanx.android.data.model

/** How far someone's cross-signing identity is trusted from this account. */
enum class UserTrust {
    Verified,
    Unverified,

    /** Verified once, and their identity has changed since: someone may be impersonating them. */
    Changed
}
