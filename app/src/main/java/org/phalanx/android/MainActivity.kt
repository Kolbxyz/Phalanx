package org.phalanx.android

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.phalanx.android.core.matrix.OidcCallbacks
import org.phalanx.android.core.matrix.SyncController
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.core.push.Notifier
import org.phalanx.android.core.security.AppLock
import org.phalanx.android.data.model.ThemeMode
import org.phalanx.android.ui.PhalanxApp
import org.phalanx.android.ui.components.IncognitoInput
import org.phalanx.android.ui.components.LocalIncognitoKeyboard
import org.phalanx.android.ui.components.LocalSecureScreens
import org.phalanx.android.ui.components.SecureScreens
import org.phalanx.android.ui.screens.lock.LockScreen
import org.phalanx.android.ui.share.ShareInbox
import org.phalanx.android.ui.share.sharedContent
import org.phalanx.android.ui.theme.PhalanxTheme

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

    @Inject
    lateinit var appLock: AppLock

    private val secureScreens = SecureScreens()

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
        lifecycleScope.launch { protectWindow() }
        setContent {
            // Nothing is drawn until the settings are read (a few ms), so the theme never flashes
            // from the default to the user's choice; the window background covers the gap.
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            // Null until the lock was decided at start, so the app never shows for a moment first.
            val lockState by appLock.locked.collectAsStateWithLifecycle()
            val locked = lockState ?: return@setContent
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
                CompositionLocalProvider(
                    LocalSecureScreens provides secureScreens,
                    LocalIncognitoKeyboard provides current.incognitoKeyboard
                ) {
                    Box {
                        // Kept composed under the lock, so unlocking returns to the same screen;
                        // hidden from accessibility services meanwhile.
                        Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) {
                            IncognitoInput {
                                PhalanxApp(openRoom = room, onRoomOpened = { pendingRoom.value = null })
                            }
                        }
                        if (locked) LockScreen(appLock::onAuthenticating, appLock::onAuthenticated)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { appLock.onShown() }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLock.onHidden()
    }

    /**
     * Keeps the window from being captured while the settings or a protected room ask for it. With
     * app lock on, Recents shows no picture of the app either; before Android 13 only a secure
     * window does that, so it is secure too.
     */
    private suspend fun protectWindow() {
        combine(settingsStore.settings, secureScreens.active) { settings, room ->
            val hideRecents = settings.appLock
            val secure = settings.blockScreenshots || room ||
                (hideRecents && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            secure to hideRecents
        }.collect { (secure, hideRecents) ->
            if (secure) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!hideRecents)
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
