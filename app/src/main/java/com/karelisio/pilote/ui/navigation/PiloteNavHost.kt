package com.karelisio.pilote.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.karelisio.pilote.ui.editor.EditorScreen
import com.karelisio.pilote.ui.history.HistoryScreen
import com.karelisio.pilote.ui.home.HomeScreen
import com.karelisio.pilote.ui.onboarding.OnboardingScreen
import com.karelisio.pilote.ui.settings.AdvancedScreen
import com.karelisio.pilote.ui.settings.HonorScreen
import com.karelisio.pilote.ui.settings.PermissionsScreen
import com.karelisio.pilote.ui.settings.SettingsScreen
import com.karelisio.pilote.ui.templates.TemplatesScreen

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun PiloteNavHost(startWithOnboarding: Boolean) {
    val nav = rememberNavController()
    val spatial: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = if (startWithOnboarding) OnboardingRoute else HomeRoute,
                enterTransition = { slideInHorizontally(spatial) { it / 3 } + fadeIn(effects) },
                exitTransition = { slideOutHorizontally(spatial) { -it / 10 } + fadeOut(effects) },
                popEnterTransition = { slideInHorizontally(spatial) { -it / 10 } + fadeIn(effects) },
                popExitTransition = { slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects) + scaleOut(targetScale = 0.92f) },
            ) {
                composable<OnboardingRoute> {
                    Scoped(this) {
                        OnboardingScreen(onFinished = {
                            if (nav.previousBackStackEntry != null) nav.popBackStack()
                            else nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                        })
                    }
                }
                composable<HomeRoute> {
                    Scoped(this) {
                        HomeScreen(
                            onOpenProfile = { nav.navigate(EditorRoute(profileId = it)) },
                            onNewProfile = { nav.navigate(TemplatesRoute) },
                            onHistory = { nav.navigate(HistoryRoute) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onPermissions = { nav.navigate(PermissionsRoute) },
                        )
                    }
                }
                composable<EditorRoute> { entry ->
                    val route = entry.toRoute<EditorRoute>()
                    Scoped(this) {
                        EditorScreen(profileId = route.profileId, template = route.template, onBack = { nav.popBackStack() })
                    }
                }
                composable<TemplatesRoute> {
                    Scoped(this) {
                        TemplatesScreen(
                            onBack = { nav.popBackStack() },
                            onPick = { template ->
                                nav.navigate(EditorRoute(template = template)) { popUpTo(HomeRoute) }
                            },
                        )
                    }
                }
                composable<HistoryRoute> { Scoped(this) { HistoryScreen(onBack = { nav.popBackStack() }) } }
                composable<SettingsRoute> {
                    Scoped(this) {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onPermissions = { nav.navigate(PermissionsRoute) },
                            onAdvanced = { nav.navigate(AdvancedRoute) },
                            onHonor = { nav.navigate(HonorRoute) },
                            onOnboarding = { nav.navigate(OnboardingRoute) },
                        )
                    }
                }
                composable<PermissionsRoute> {
                    Scoped(this) {
                        PermissionsScreen(onBack = { nav.popBackStack() }, onAdvanced = { nav.navigate(AdvancedRoute) }, onHonor = { nav.navigate(HonorRoute) })
                    }
                }
                composable<AdvancedRoute> { Scoped(this) { AdvancedScreen(onBack = { nav.popBackStack() }) } }
                composable<HonorRoute> { Scoped(this) { HonorScreen(onBack = { nav.popBackStack() }) } }
            }
        }
    }
}

@Composable
private fun Scoped(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavScope provides scope) { content() }
}
