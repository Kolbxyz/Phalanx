package org.phalanx.android.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object RoomListRoute

@Serializable
data class RoomRoute(val roomId: String)

@Serializable
data object AccountRoute

@Serializable
data object SettingsRoute

@Serializable
data object DevicesRoute
