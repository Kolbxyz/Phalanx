package org.phalanx.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.ui.navigation.PhalanxNavHost
import org.phalanx.android.ui.screens.login.LoginScreen

/** [openRoom]: a room a tapped notification asked for; [onRoomOpened] once it is shown. */
@Composable
fun PhalanxApp(openRoom: String?, onRoomOpened: () -> Unit, viewModel: AppViewModel = hiltViewModel()) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Crossfade(
            targetState = sessionState,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "session"
        ) { state ->
            when (state) {
                SessionState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                SessionState.LoggedOut -> LoginScreen()
                is SessionState.LoggedIn -> {
                    AskForNotifications()
                    PhalanxNavHost(openRoom, onRoomOpened)
                }
            }
        }
    }
}

/**
 * Android 13+ blocks notifications until allowed. Asked once per launch while signed in; Android
 * itself stops showing the prompt after the user declines twice, and settings can still ask.
 */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!asked && !granted) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
