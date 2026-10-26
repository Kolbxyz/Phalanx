package org.phalanx.android.core.security

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.phalanx.android.core.prefs.SettingsStore

/**
 * Whether the app waits for the user to unlock it. With app lock on, a fresh start is locked, and so
 * is coming back after the app spent its timeout out of sight. Only the UI is held back: sync and
 * notifications go on underneath.
 */
@Singleton
class AppLock @Inject internal constructor(private val settings: SettingsStore) {
    private val mutableLocked = MutableStateFlow<Boolean?>(null)

    /** Null until the settings were read at the first start, so nothing shows before the lock does. */
    val locked: StateFlow<Boolean?> = mutableLocked.asStateFlow()

    // Elapsed realtime when the app last left the screen; null while it is on screen.
    private var hiddenAt: Long? = null

    // The system's PIN screen covers the app while the user unlocks it: that is not leaving it.
    private var authenticating = false

    /** The app is on screen again, or for the first time. */
    suspend fun onShown() {
        val current = settings.current()
        val away = hiddenAt?.let { SystemClock.elapsedRealtime() - it }
        hiddenAt = null
        val coldStart = mutableLocked.value == null
        val timedOut = away != null && away >= current.lockTimeout.millis
        if (coldStart || (current.appLock && timedOut)) mutableLocked.value = current.appLock
    }

    /** The app left the screen, not just for a rotation. */
    fun onHidden() {
        if (!authenticating) hiddenAt = SystemClock.elapsedRealtime()
    }

    /** The system's unlock prompt is about to show. */
    fun onAuthenticating() {
        authenticating = true
    }

    /** The prompt closed; [success] when the user proved it is them. */
    fun onAuthenticated(success: Boolean) {
        authenticating = false
        if (success) mutableLocked.value = false
    }
}
