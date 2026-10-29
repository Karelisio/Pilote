package com.karelisio.pilote

import android.app.Application
import android.content.Context
import com.karelisio.pilote.actions.ActionExecutor
import com.karelisio.pilote.actions.AdvancedController
import com.karelisio.pilote.actions.SystemController
import com.karelisio.pilote.data.HistoryRepository
import com.karelisio.pilote.data.PiloteDatabase
import com.karelisio.pilote.data.ProfileRepository
import com.karelisio.pilote.data.RuntimeStore
import com.karelisio.pilote.data.SettingsStore
import com.karelisio.pilote.engine.Engine
import com.karelisio.pilote.engine.FactsHub
import com.karelisio.pilote.integrations.AppLauncher
import com.karelisio.pilote.integrations.StatusIntegration
import com.karelisio.pilote.shizuku.ShizukuManager
import com.karelisio.pilote.surfaces.SurfaceUpdater
import com.karelisio.pilote.triggers.TriggerRegistry
import com.karelisio.pilote.update.UpdateManager
import com.karelisio.pilote.util.Channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency graph (no DI framework: the app is small and fully local). */
class AppGraph(app: Application) {
    val context: Context = app
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = PiloteDatabase.build(app)
    val settings = SettingsStore(app)
    val runtime = RuntimeStore(app)
    val profiles = ProfileRepository(db.profiles())
    val history = HistoryRepository(db.history())
    val facts = FactsHub(runtime)
    val shizuku = ShizukuManager(app)
    val status = StatusIntegration(app)
    val launcher = AppLauncher(app)
    val executor = ActionExecutor(app, SystemController(app), AdvancedController(app, shizuku), status, launcher)
    val engine = Engine(appScope, profiles, facts, executor, runtime, db.baselines(), history, settings)
    val registry = TriggerRegistry(app, appScope, profiles, facts)
    val surfaces = SurfaceUpdater(app, appScope, engine, profiles, settings)
    val updates = UpdateManager(app, settings)
}

class PiloteApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Channels.create(this)
        graph.shizuku.init()
        graph.engine.start()
        graph.registry.start()
        graph.surfaces.start()
        graph.appScope.launch { graph.history.prune() }
    }

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as PiloteApp).graph
    }
}
