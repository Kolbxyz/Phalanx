package org.phalanx.android.core.matrix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the OAuth redirect from the activity that receives it to the login screen. A state flow
 * rather than an event, so a redirect that lands while the screen is not collecting is not lost.
 */
@Singleton
class OidcCallbacks @Inject constructor() {
    private val pending = MutableStateFlow<String?>(null)
    val callback: StateFlow<String?> = pending

    fun deliver(url: String) {
        pending.value = url
    }

    /** Marks [url] handled, unless a newer redirect has replaced it. */
    fun consume(url: String) {
        pending.compareAndSet(url, null)
    }
}
