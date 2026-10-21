package org.phalanx.android.data.model

/** One of the account's signed-in sessions, as the server lists it. */
data class Device(
    val id: String,
    val name: String?,
    val lastSeenIp: String?,
    val lastSeenMillis: Long?,
    val isCurrent: Boolean
)

/** Why an account action failed, when the UI can say something more useful than "try again". */
enum class AccountFailure { WrongPassword, Unknown }

class AccountException(val failure: AccountFailure, cause: Throwable? = null) : Exception(failure.name, cause)
