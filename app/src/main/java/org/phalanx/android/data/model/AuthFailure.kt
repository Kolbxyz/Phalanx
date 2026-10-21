package org.phalanx.android.data.model

/** Why signing in failed, in terms the UI can turn into a localized message. */
enum class AuthFailure {
    InvalidServer,
    ServerUnreachable,

    /** The homeserver offers neither password nor OAuth login (SSO-only servers, for example). */
    NoSupportedLogin,
    InvalidCredentials,
    AccountDisabled,
    RateLimited,

    /** No app on the device can open the server's sign-in page. */
    BrowserUnavailable,

    /** The user backed out on the server's sign-in page. Not an error worth a message. */
    Cancelled,
    Unknown
}

class AuthException(val failure: AuthFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** How a homeserver lets accounts sign in; at least one is true. */
data class LoginMethods(val password: Boolean, val oidc: Boolean)
