package org.phalanx.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.ui.screens.login.LoginScreen
import org.phalanx.android.ui.navigation.PhalanxNavHost

@Composable
fun PhalanxApp(viewModel: AppViewModel = hiltViewModel()) {
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
                is SessionState.LoggedIn -> PhalanxNavHost()
            }
        }
    }
}
