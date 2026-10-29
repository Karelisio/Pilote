package com.karelisio.pilote.surfaces

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

abstract class BaseTile : TileService() {
    protected val graph by lazy { PiloteApp.graph(this) }
    protected var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch { render() }
    }

    override fun onStopListening() {
        scope.cancel()
        super.onStopListening()
    }

    protected abstract suspend fun render()
}

/** Global on/off (pause) tile. */
class PiloteTileService : BaseTile() {
    override suspend fun render() {
        val tile = qsTile ?: return
        val paused = graph.settings.paused.first()
        val active = graph.engine.active.value
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = "Pilote"
        tile.subtitle = when {
            paused -> "En pause"
            active.isEmpty() -> "Actif"
            else -> active.first().name
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_pilote)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val paused = graph.settings.paused.first()
            graph.engine.setPaused(!paused)
            render()
        }
    }
}

/** One tile per manual profile (slots, in priority order). */
abstract class ProfileTileBase(private val slot: Int) : BaseTile() {
    private suspend fun profile() = SurfaceUpdater.manualOrder(graph.profiles.all()).getOrNull(slot)

    override suspend fun render() {
        val tile = qsTile ?: return
        val p = profile()
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_profile)
        if (p == null) {
            tile.label = "Profil manuel ${slot + 1}"
            tile.subtitle = "Non configuré"
            tile.state = Tile.STATE_UNAVAILABLE
        } else {
            val on = graph.facts.isManualOn(p.id)
            val active = graph.engine.active.value.any { it.id == p.id }
            tile.label = p.name
            tile.subtitle = when {
                active -> "Actif"
                on -> "En attente"
                else -> "Désactivé"
            }
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val p = profile() ?: return@launch
            graph.engine.toggleManual(p.id)
            render()
        }
    }
}

class ProfileTile1 : ProfileTileBase(0)
class ProfileTile2 : ProfileTileBase(1)
class ProfileTile3 : ProfileTileBase(2)
class ProfileTile4 : ProfileTileBase(3)
class ProfileTile5 : ProfileTileBase(4)
