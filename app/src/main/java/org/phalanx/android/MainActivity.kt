package org.phalanx.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.matrix.OidcCallbacks
import org.phalanx.android.core.matrix.SyncController
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.core.push.Notifier
import org.phalanx.android.data.model.ThemeMode
import org.phalanx.android.ui.PhalanxApp
import org.phalanx.android.ui.share.ShareInbox
import org.phalanx.android.ui.share.sharedContent
import org.phalanx.android.ui.theme.PhalanxTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var syncController: SyncController

    @Inject
    lateinit var oidcCallbacks: OidcCallbacks

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var shareInbox: ShareInbox

    /** The room a tapped notification asked for, until the navigation opens it. */
    private val pendingRoom = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch: after a configuration change the intent is the one already handled.
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { syncController.syncWhileActive() }
        }
        setContent {
            // Nothing is drawn until the settings are read (a few ms), so the theme never flashes
            // from the default to the user's choice; the window background covers the gap.
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            val darkTheme = when (current.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // System bar icons follow the app's theme, not the phone's, when the two differ.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            PhalanxTheme(
                dynamicColor = current.dynamicColor,
                amoledBlack = current.amoledBlack,
                darkTheme = darkTheme,
                reduceMotion = current.reduceMotion
            ) {
                val room by pendingRoom.collectAsStateWithLifecycle()
                PhalanxApp(openRoom = room, onRoomOpened = { pendingRoom.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(Notifier.EXTRA_ROOM_ID)?.let { pendingRoom.value = it }
        sharedContent(intent)?.let { shareInbox.offer(it) }
        val uri = intent.data ?: return
        if (intent.action == Intent.ACTION_VIEW && uri.scheme == BuildConfig.OAUTH_REDIRECT_SCHEME) {
            oidcCallbacks.deliver(uri.toString())
        }
    }
}
