package org.phalanx.android.ui.navigation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.phalanx.android.ui.screens.account.AccountScreen
import org.phalanx.android.ui.screens.devices.DevicesScreen
import org.phalanx.android.ui.screens.room.RoomScreen
import org.phalanx.android.ui.screens.roomlist.RoomListScreen
import org.phalanx.android.ui.screens.settings.SettingsScreen

/** Shared-axis transitions on the theme's motion scheme, so reduced motion shortens them too. */
@Composable
fun PhalanxNavHost() {
    val navController = rememberNavController()
    val spatial: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
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
                onOpenAccount = { navController.navigate(AccountRoute) }
            )
        }
        composable<RoomRoute> {
            RoomScreen(onBack = { navController.popBackStack() })
        }
        composable<AccountRoute> {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDevices = { navController.navigate(DevicesRoute) }
            )
        }
        composable<DevicesRoute> {
            DevicesScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

private const val SHIFT_DIVISOR = 4
