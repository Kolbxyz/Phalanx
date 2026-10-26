package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.phalanx.android.core.prefs.RoomPrefsStore
import org.phalanx.android.data.model.RoomPrefs
import org.phalanx.android.ui.components.SecureScreen

/**
 * Whether the room was set, on this phone, to block screenshots while it, its gallery or one of its
 * photos is open. Any screen whose route has a `roomId` can use it.
 */
@HiltViewModel
class RoomProtectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    roomPrefs: RoomPrefsStore
) : ViewModel() {
    private val roomId: String = checkNotNull(savedStateHandle[ROOM_ID]) { "The route has no room" }

    // Before the file is read the room counts as protected, so it is never capturable for a moment.
    val protectScreen: StateFlow<Boolean> = roomPrefs.prefs
        .map(::protects)
        .stateIn(viewModelScope, SharingStarted.Eagerly, protects(roomPrefs.prefs.value))

    private fun protects(all: Map<String, RoomPrefs>?): Boolean = all?.get(roomId)?.protectScreen ?: (all == null)
}

/** Blocks screenshots while composed, if the current route's room asks for it. */
@Composable
fun ProtectedRoomScreen(viewModel: RoomProtectionViewModel = hiltViewModel()) {
    val protect by viewModel.protectScreen.collectAsStateWithLifecycle()
    SecureScreen(protect)
}

// The property every room route names its room by; typed routes store each argument under its name.
private const val ROOM_ID = "roomId"
