package io.github.mrdarkdebug.siderea.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaMotion
import io.github.mrdarkdebug.siderea.device.ShutterKeyBus
import io.github.mrdarkdebug.siderea.ui.camera.CameraScreen
import io.github.mrdarkdebug.siderea.ui.inspector.InspectorScreen
import io.github.mrdarkdebug.siderea.ui.sessions.SessionDetailScreen
import io.github.mrdarkdebug.siderea.ui.sessions.SessionsScreen
import io.github.mrdarkdebug.siderea.ui.settings.LicensesScreen
import io.github.mrdarkdebug.siderea.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object CameraRoute

@Serializable
data object InspectorRoute

@Serializable
data object SessionsRoute

@Serializable
data class SessionRoute(
    val id: String,
)

@Serializable
data object SettingsRoute

@Serializable
data object LicensesRoute

@Composable
fun SideriaNavHost(keys: ShutterKeyBus) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = CameraRoute,
        // Quiet cross-fades only: nothing slides or bounces in a dark field.
        enterTransition = { fadeIn(SideriaMotion.standard()) },
        exitTransition = { fadeOut(SideriaMotion.fast()) },
        popEnterTransition = { fadeIn(SideriaMotion.standard()) },
        popExitTransition = { fadeOut(SideriaMotion.fast()) },
    ) {
        composable<CameraRoute> {
            CameraScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenSessions = { id -> navController.navigate(if (id == null) SessionsRoute else SessionRoute(id)) },
                keys = keys,
            )
        }
        composable<SessionsRoute> {
            SessionsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(SessionRoute(id)) },
            )
        }
        composable<SessionRoute> { entry ->
            SessionDetailScreen(
                id = entry.toRoute<SessionRoute>().id,
                onBack = { navController.popBackStack() },
                onOpenCamera = { navController.popBackStack(CameraRoute, inclusive = false) },
            )
        }
        composable<InspectorRoute> {
            InspectorScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenInspector = { navController.navigate(InspectorRoute) },
                onOpenLicenses = { navController.navigate(LicensesRoute) },
                onOpenSessions = { navController.navigate(SessionsRoute) },
            )
        }
        composable<LicensesRoute> {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
    }
}
