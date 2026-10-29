package com.karelisio.pilote.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class EditorRoute(val profileId: Long = 0, val template: String? = null)
@Serializable data object TemplatesRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute
@Serializable data object PermissionsRoute
@Serializable data object AdvancedRoute
@Serializable data object HonorRoute
@Serializable data object OnboardingRoute
