package org.phalanx.android.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object RoomListRoute

/** [focusEventId]: a message to scroll to and highlight once the conversation opens. */
@Serializable
data class RoomRoute(val roomId: String, val focusEventId: String? = null)

/**
 * A photo or video of [roomId], named by its timeline item, never by its media. [fromGallery]: the
 * item comes from the room's details screen rather than its conversation.
 */
@Serializable
data class MediaViewerRoute(val roomId: String, val entryId: String, val fromGallery: Boolean = false)

@Serializable
data class RoomDetailsRoute(val roomId: String)

/** All of [roomId]'s photos and videos, or with [files] all its files and audio. */
@Serializable
data class RoomGalleryRoute(val roomId: String, val files: Boolean)

@Serializable
data object AccountRoute

@Serializable
data object SettingsRoute

@Serializable
data object DevicesRoute

@Serializable
data object DeletedMessagesRoute

/** One page of settings; [category] is a [org.phalanx.android.ui.screens.settings.SettingsCategory] name. */
@Serializable
data class SettingsCategoryRoute(val category: String)
