package org.phalanx.android.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.phalanx.android.ui.screens.account.AccountScreen
import org.phalanx.android.ui.screens.bookmarks.BookmarksScreen
import org.phalanx.android.ui.screens.bridges.BridgesScreen
import org.phalanx.android.ui.screens.catchup.CatchUpScreen
import org.phalanx.android.ui.screens.deleted.DeletedMessagesScreen
import org.phalanx.android.ui.screens.details.GalleryScreen
import org.phalanx.android.ui.screens.details.RoomDetailsScreen
import org.phalanx.android.ui.screens.devices.DevicesScreen
import org.phalanx.android.ui.screens.media.MediaViewerScreen
import org.phalanx.android.ui.screens.people.PeopleScreen
import org.phalanx.android.ui.screens.reminders.RemindersScreen
import org.phalanx.android.ui.screens.room.RoomScreen
import org.phalanx.android.ui.screens.roomlist.RoomListScreen
import org.phalanx.android.ui.screens.settings.SettingsCategory
import org.phalanx.android.ui.screens.settings.SettingsCategoryScreen
import org.phalanx.android.ui.screens.settings.SettingsScreen
import org.phalanx.android.ui.share.SharePicker

/**
 * Shared-axis transitions on the theme's motion scheme, so reduced motion shortens them too.
 * [openRoom] is a room a tapped notification asked for, maybe on one message; it opens over the room list.
 */
@Composable
fun PhalanxNavHost(openRoom: RoomRoute?, onRoomOpened: () -> Unit) {
    val navController = rememberNavController()
    LaunchedEffect(openRoom) {
        if (openRoom == null) return@LaunchedEffect
        navController.navigate(openRoom) {
            popUpTo(RoomListRoute)
            launchSingleTop = true
        }
        onRoomOpened()
    }
    SharePicker(onPicked = { roomId ->
        navController.navigate(RoomRoute(roomId)) {
            popUpTo(RoomListRoute)
            launchSingleTop = true
        }
    })
    val spatial: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalTopEntry provides navController.currentBackStackEntryAsState().value
        ) {
            NavHost(
                navController = navController,
                startDestination = RoomListRoute,
                enterTransition = { slideInHorizontally(spatial) { it / SHIFT_DIVISOR } + fadeIn(effects) },
                exitTransition = { slideOutHorizontally(spatial) { -it / SHIFT_DIVISOR } + fadeOut(effects) },
                popEnterTransition = { slideInHorizontally(spatial) { -it / SHIFT_DIVISOR } + fadeIn(effects) },
                popExitTransition = { slideOutHorizontally(spatial) { it / SHIFT_DIVISOR } + fadeOut(effects) }
            ) {
                screen<RoomListRoute> { entry ->
                    RoomListScreen(
                        onOpenRoom = { navController.from(entry) { navigate(RoomRoute(it)) } },
                        onOpenMessage = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } },
                        onOpenDetails = { navController.from(entry) { navigate(RoomDetailsRoute(it)) } },
                        onOpenAccount = { navController.from(entry) { navigate(SettingsRoute) } },
                        onOpenCatchUp = { navController.from(entry) { navigate(CatchUpRoute) } },
                        onOpenReminders = { navController.from(entry) { navigate(RemindersRoute) } },
                        onOpenBookmarks = { navController.from(entry) { navigate(BookmarksRoute) } },
                        onOpenPeople = { navController.from(entry) { navigate(PeopleRoute) } }
                    )
                }
                screen<PeopleRoute> { entry ->
                    PeopleScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenRoom = { navController.from(entry) { navigate(RoomRoute(it)) } }
                    )
                }
                screen<RemindersRoute> { entry ->
                    RemindersScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenMessage = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } }
                    )
                }
                screen<BookmarksRoute> { entry ->
                    BookmarksScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenMessage = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } }
                    )
                }
                screen<CatchUpRoute> { entry ->
                    CatchUpScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenRoom = { navController.from(entry) { navigate(RoomRoute(it)) } }
                    )
                }
                screen<RoomRoute>(
                    exitTransition = { if (toMedia()) ExitTransition.KeepUntilTransitionsFinished else null },
                    popEnterTransition = { if (fromMedia()) EnterTransition.None else null }
                ) { entry ->
                    RoomScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenDetails = { navController.from(entry) { navigate(RoomDetailsRoute(it)) } },
                        onOpenMedia = { roomId, entryId ->
                            navController.from(entry) { navigate(MediaViewerRoute(roomId, entryId)) }
                        },
                        // A direct chat opened from a profile replaces this room, so back returns to the list.
                        onOpenRoom = { roomId ->
                            navController.from(entry) { navigate(RoomRoute(roomId)) { popUpTo(RoomListRoute) } }
                        }
                    )
                }
                // Fades over the room, which holds still, so the image growing out of its bubble is the
                // only motion that reads.
                screen<MediaViewerRoute>(
                    enterTransition = { fadeIn(effects) },
                    exitTransition = { fadeOut(effects) },
                    popEnterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) }
                ) { entry ->
                    MediaViewerScreen(
                        onClose = { navController.from(entry) { popBackStack() } },
                        onJumpTo = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } }
                    )
                }
                screen<RoomDetailsRoute>(
                    exitTransition = { if (toMedia()) ExitTransition.KeepUntilTransitionsFinished else null },
                    popEnterTransition = { if (fromMedia()) EnterTransition.None else null }
                ) { entry ->
                    RoomDetailsScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onLeft = { navController.from(entry) { popBackStack(RoomListRoute, inclusive = false) } },
                        onJumpTo = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } },
                        onOpenMedia = { roomId, entryId ->
                            navController.from(entry) {
                                navigate(MediaViewerRoute(roomId, entryId, fromGallery = true))
                            }
                        },
                        onShowAll = { roomId, files ->
                            navController.from(entry) { navigate(RoomGalleryRoute(roomId, files)) }
                        },
                        onOpenRoom = { roomId ->
                            navController.from(entry) { navigate(RoomRoute(roomId)) { popUpTo(RoomListRoute) } }
                        }
                    )
                }
                screen<RoomGalleryRoute>(
                    exitTransition = { if (toMedia()) ExitTransition.KeepUntilTransitionsFinished else null },
                    popEnterTransition = { if (fromMedia()) EnterTransition.None else null }
                ) { entry ->
                    GalleryScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenMedia = { roomId, entryId ->
                            navController.from(entry) {
                                navigate(MediaViewerRoute(roomId, entryId, fromGallery = true))
                            }
                        }
                    )
                }
                screen<AccountRoute> { entry ->
                    AccountScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenDevices = { navController.from(entry) { navigate(DevicesRoute) } }
                    )
                }
                screen<BridgesRoute> { entry ->
                    BridgesScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenRoom = { navController.from(entry) { navigate(RoomRoute(it)) } }
                    )
                }
                screen<DevicesRoute> { entry ->
                    DevicesScreen(onBack = { navController.from(entry) { popBackStack() } })
                }
                screen<SettingsRoute> { entry ->
                    SettingsScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenAccount = { navController.from(entry) { navigate(AccountRoute) } },
                        onOpenBridges = { navController.from(entry) { navigate(BridgesRoute) } },
                        onOpenCategory = { navController.from(entry) { navigate(SettingsCategoryRoute(it.name)) } }
                    )
                }
                screen<SettingsCategoryRoute> { entry ->
                    val name = entry.toRoute<SettingsCategoryRoute>().category
                    val category = SettingsCategory.entries.firstOrNull { it.name == name }
                        ?: SettingsCategory.Appearance
                    SettingsCategoryScreen(
                        category = category,
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenDevices = { navController.from(entry) { navigate(DevicesRoute) } },
                        onOpenDeletedMessages = { navController.from(entry) { navigate(DeletedMessagesRoute) } }
                    )
                }
                screen<DeletedMessagesRoute> { entry ->
                    DeletedMessagesScreen(onBack = { navController.from(entry) { popBackStack() } })
                }
            }
        }
    }
}

/**
 * Opens [roomId] on [eventId], straight over the room list: whatever led here (the room itself, its
 * details, the media viewer) is left behind, so back returns to the list as from any conversation.
 */
private fun NavController.jumpTo(roomId: String, eventId: String) {
    navigate(RoomRoute(roomId, eventId)) { popUpTo(RoomListRoute) }
}

/**
 * Runs [action] only while [entry] is the screen on top. A screen animating out is still drawn over
 * the one coming back, so a quick tap would otherwise land on it and navigate from a closed screen.
 */
private inline fun NavController.from(entry: NavBackStackEntry, action: NavController.() -> Unit) {
    if (currentBackStackEntry === entry) action()
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.toMedia() =
    targetState.destination.hasRoute<MediaViewerRoute>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fromMedia() =
    initialState.destination.hasRoute<MediaViewerRoute>()

private const val SHIFT_DIVISOR = 4
