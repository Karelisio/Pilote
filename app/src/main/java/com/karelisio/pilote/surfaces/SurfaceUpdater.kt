package com.karelisio.pilote.surfaces

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import com.karelisio.pilote.data.ProfileRepository
import com.karelisio.pilote.data.SettingsStore
import com.karelisio.pilote.engine.Engine
import com.karelisio.pilote.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Keeps quick settings tiles and the home screen widget in sync with the engine. */
class SurfaceUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
    profiles: ProfileRepository,
    private val settings: SettingsStore,
) {
    /** Manual profiles in tile/widget order. */
    val manualProfiles: StateFlow<List<Profile>> = profiles.profiles
        .map { list -> manualOrder(list) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(manualProfiles, engine.active, settings.paused) { a, b, c -> Triple(a, b, c) }
                .debounce(250)
                .collect {
                    requestTiles()
                    runCatching { PiloteWidget().updateAll(context) }
                }
        }
    }

    fun requestTiles() {
        val classes = listOf(PiloteTileService::class.java) + PROFILE_TILES
        classes.forEach { cls -> runCatching { TileService.requestListeningState(context, ComponentName(context, cls)) } }
    }

    companion object {
        val PROFILE_TILES = listOf(
            ProfileTile1::class.java, ProfileTile2::class.java, ProfileTile3::class.java,
            ProfileTile4::class.java, ProfileTile5::class.java,
        )

        fun manualOrder(list: List<Profile>) = list.filter { it.isManual }.sortedWith(compareByDescending<Profile> { it.priority }.thenBy { it.id })
    }
}
