package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.OAuthConfiguration
import org.phalanx.android.BuildConfig

/**
 * How Phalanx introduces itself to OAuth servers (MAS) when it registers dynamically. MAS only
 * accepts a private-use redirect scheme that starts with the reversed host of [CLIENT_URI], so the
 * two change together; the scheme itself lives in app/build.gradle.kts, shared with the manifest.
 */
internal object OAuthClient {
    private const val CLIENT_NAME = "Phalanx"
    private const val CLIENT_URI = "https://phalanx.kolbxyz.xyz"

    val redirectUri: String = "${BuildConfig.OAUTH_REDIRECT_SCHEME}:/"

    fun configuration() = OAuthConfiguration(
        clientName = CLIENT_NAME,
        redirectUri = redirectUri,
        clientUri = CLIENT_URI,
        logoUri = null,
        tosUri = null,
        policyUri = null,
        staticRegistrations = emptyMap()
    )
}
