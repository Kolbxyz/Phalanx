package org.phalanx.android.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object RoomListRoute

@Serializable
data class RoomRoute(val roomId: String)

/** An image of [roomId]'s timeline, named by its timeline item, not by its media. */
@Serializable
data class MediaViewerRoute(val roomId: String, val entryId: String)

@Serializable
data object AccountRoute

@Serializable
data object SettingsRoute

@Serializable
data object DevicesRoute
