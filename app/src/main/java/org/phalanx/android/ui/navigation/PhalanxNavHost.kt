package org.phalanx.android.ui.navigation

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.phalanx.android.ui.screens.account.AccountScreen
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
                composable<RoomListRoute> {
                    RoomListScreen(
                        onOpenRoom = { navController.navigate(RoomRoute(it)) },
                        onOpenDetails = { navController.navigate(RoomDetailsRoute(it)) },
                        onOpenAccount = { navController.navigate(SettingsRoute) }
                    )
                }
                composable<RoomRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        RoomScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDetails = { navController.navigate(RoomDetailsRoute(it)) },
                            onOpenMedia = { roomId, entryId ->
                                navController.navigate(MediaViewerRoute(roomId, entryId))
                            }
                        )
                    }
                }
                // Fades over the room, so the image growing out of its bubble is the motion that reads.
                composable<MediaViewerRoute>(
                    enterTransition = { fadeIn(effects) },
                    exitTransition = { fadeOut(effects) },
                    popEnterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) }
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MediaViewerScreen(onClose = { navController.popBackStack() })
                    }
                }
                composable<RoomDetailsRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        RoomDetailsScreen(
                            onBack = { navController.popBackStack() },
                            onLeft = { navController.popBackStack(RoomListRoute, inclusive = false) },
                            onOpenMedia = { roomId, entryId ->
                                navController.navigate(MediaViewerRoute(roomId, entryId, fromGallery = true))
                            }
                        )
                    }
                }
                composable<AccountRoute> {
                    AccountScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDevices = { navController.navigate(DevicesRoute) }
                    )
                }
                composable<DevicesRoute> {
                    DevicesScreen(onBack = { navController.popBackStack() })
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { navController.navigate(AccountRoute) },
                        onOpenCategory = { navController.navigate(SettingsCategoryRoute(it.name)) }
                    )
                }
                composable<SettingsCategoryRoute> { entry ->
                    val name = entry.toRoute<SettingsCategoryRoute>().category
                    val category = SettingsCategory.entries.firstOrNull { it.name == name }
                        ?: SettingsCategory.Appearance
                    SettingsCategoryScreen(
                        category = category,
                        onBack = { navController.popBackStack() },
                        onOpenDevices = { navController.navigate(DevicesRoute) }
                    )
                }
            }
        }
    }
}

private const val SHIFT_DIVISOR = 4
