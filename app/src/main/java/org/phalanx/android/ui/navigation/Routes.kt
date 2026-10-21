package org.phalanx.android.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object RoomListRoute

@Serializable
data class RoomRoute(val roomId: String)

/**
 * A photo or video of [roomId], named by its timeline item, never by its media. [fromGallery]: the
 * item comes from the room's details screen rather than its conversation.
 */
@Serializable
data class MediaViewerRoute(val roomId: String, val entryId: String, val fromGallery: Boolean = false)

@Serializable
data class RoomDetailsRoute(val roomId: String)

@Serializable
data object AccountRoute

@Serializable
data object SettingsRoute

@Serializable
data object DevicesRoute

/** One page of settings; [category] is a [org.phalanx.android.ui.screens.settings.SettingsCategory] name. */
@Serializable
data class SettingsCategoryRoute(val category: String)
