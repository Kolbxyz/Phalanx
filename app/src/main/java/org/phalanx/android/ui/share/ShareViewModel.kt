package org.phalanx.android.ui.share

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.RoomListRepository

/** The conversations a share from another app can go to. */
@HiltViewModel
class ShareViewModel @Inject constructor(
    roomList: RoomListRepository,
    private val inbox: ShareInbox
) : ViewModel() {
    val rooms: StateFlow<List<RoomSummary>?> = roomList.rooms

    val pending: StateFlow<SharedContent?> = inbox.pending

    fun sendTo(roomId: String) = inbox.sendTo(roomId)

    fun dismiss() = inbox.dismiss()
}
