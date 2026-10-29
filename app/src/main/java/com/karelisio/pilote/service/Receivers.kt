package com.karelisio.pilote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.triggers.launchReceiver
import kotlinx.coroutines.flow.first

/** Restarts the service and re-registers geofences and alarms after boot / update / clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> Unit
            else -> return
        }
        val graph = PiloteApp.graph(context)
        val pending = goAsync()
        graph.appScope.launchReceiver(pending) {
            graph.registry.reRegisterAll()
            graph.engine.refresh()
            if (graph.settings.onboardingDone.first()) PiloteService.start(context)
        }
    }
}

/** Notification / widget controls. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = PiloteApp.graph(context)
        val pending = goAsync()
        graph.appScope.launchReceiver(pending) {
            when (intent.action) {
                ACTION_TOGGLE_PAUSE -> graph.engine.setPaused(!graph.settings.isPaused())
                ACTION_TOGGLE_MANUAL -> intent.getLongExtra(EXTRA_PROFILE_ID, -1).takeIf { it > 0 }?.let { graph.engine.toggleManual(it) }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PAUSE = "com.karelisio.pilote.TOGGLE_PAUSE"
        const val ACTION_TOGGLE_MANUAL = "com.karelisio.pilote.TOGGLE_MANUAL"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}
