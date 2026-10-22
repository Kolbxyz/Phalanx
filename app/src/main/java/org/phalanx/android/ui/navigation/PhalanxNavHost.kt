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
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.phalanx.android.ui.screens.account.AccountScreen
import org.phalanx.android.ui.screens.deleted.DeletedMessagesScreen
import org.phalanx.android.ui.screens.details.RoomDetailsScreen
import org.phalanx.android.ui.screens.devices.DevicesScreen
import org.phalanx.android.ui.screens.media.MediaViewerScreen
import org.phalanx.android.ui.screens.room.RoomScreen
import org.phalanx.android.ui.screens.roomlist.RoomListScreen
import org.phalanx.android.ui.screens.settings.SettingsCategory
import org.phalanx.android.ui.screens.settings.SettingsCategoryScreen
import org.phalanx.android.ui.screens.settings.SettingsScreen

/**
 * Shared-axis transitions on the theme's motion scheme, so reduced motion shortens them too.
 * [openRoom] is a room a tapped notification asked for; it opens over the room list.
 */
@Composable
fun PhalanxNavHost(openRoom: String?, onRoomOpened: () -> Unit) {
    val navController = rememberNavController()
    LaunchedEffect(openRoom) {
        if (openRoom == null) return@LaunchedEffect
        navController.navigate(RoomRoute(openRoom)) {
            popUpTo(RoomListRoute)
            launchSingleTop = true
        }
        onRoomOpened()
    }
    val spatial: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = RoomListRoute,
                enterTransition = { slideInHorizontally(spatial) { it / SHIFT_DIVISOR } + fadeIn(effects) },
                exitTransition = { slideOutHorizontally(spatial) { -it / SHIFT_DIVISOR } + fadeOut(effects) },
                popEnterTransition = { slideInHorizontally(spatial) { -it / SHIFT_DIVISOR } + fadeIn(effects) },
                popExitTransition = { slideOutHorizontally(spatial) { it / SHIFT_DIVISOR } + fadeOut(effects) }
            ) {
                composable<RoomListRoute> { entry ->
                    RoomListScreen(
                        onOpenRoom = { navController.from(entry) { navigate(RoomRoute(it)) } },
                        onOpenMessage = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } },
                        onOpenDetails = { navController.from(entry) { navigate(RoomDetailsRoute(it)) } },
                        onOpenAccount = { navController.from(entry) { navigate(SettingsRoute) } }
                    )
                }
                composable<RoomRoute>(
                    exitTransition = { if (toMedia()) ExitTransition.KeepUntilTransitionsFinished else null },
                    popEnterTransition = { if (fromMedia()) EnterTransition.None else null }
                ) { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        RoomScreen(
                            onBack = { navController.from(entry) { popBackStack() } },
                            onOpenDetails = { navController.from(entry) { navigate(RoomDetailsRoute(it)) } },
                            onOpenMedia = { roomId, entryId ->
                                navController.from(entry) { navigate(MediaViewerRoute(roomId, entryId)) }
                            }
                        )
                    }
                }
                // Fades over the room, which holds still, so the image growing out of its bubble is the
                // only motion that reads.
                composable<MediaViewerRoute>(
                    enterTransition = { fadeIn(effects) },
                    exitTransition = { fadeOut(effects) },
                    popEnterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) }
                ) { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MediaViewerScreen(
                            onClose = { navController.from(entry) { popBackStack() } },
                            onJumpTo = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } }
                        )
                    }
                }
                composable<RoomDetailsRoute>(
                    exitTransition = { if (toMedia()) ExitTransition.KeepUntilTransitionsFinished else null },
                    popEnterTransition = { if (fromMedia()) EnterTransition.None else null }
                ) { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        RoomDetailsScreen(
                            onBack = { navController.from(entry) { popBackStack() } },
                            onLeft = { navController.from(entry) { popBackStack(RoomListRoute, inclusive = false) } },
                            onJumpTo = { roomId, eventId -> navController.from(entry) { jumpTo(roomId, eventId) } },
                            onOpenMedia = { roomId, entryId ->
                                navController.from(entry) {
                                    navigate(MediaViewerRoute(roomId, entryId, fromGallery = true))
                                }
                            }
                        )
                    }
                }
                composable<AccountRoute> { entry ->
                    AccountScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenDevices = { navController.from(entry) { navigate(DevicesRoute) } }
                    )
                }
                composable<DevicesRoute> { entry ->
                    DevicesScreen(onBack = { navController.from(entry) { popBackStack() } })
                }
                composable<SettingsRoute> { entry ->
                    SettingsScreen(
                        onBack = { navController.from(entry) { popBackStack() } },
                        onOpenAccount = { navController.from(entry) { navigate(AccountRoute) } },
                        onOpenCategory = { navController.from(entry) { navigate(SettingsCategoryRoute(it.name)) } }
                    )
                }
                composable<SettingsCategoryRoute> { entry ->
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
                composable<DeletedMessagesRoute> { entry ->
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
