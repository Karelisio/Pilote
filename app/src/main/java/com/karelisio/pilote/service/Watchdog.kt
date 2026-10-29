package com.karelisio.pilote.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.triggers.launchReceiver
import kotlinx.coroutines.flow.first

/**
 * Self-restart: an exact alarm every 15 min relaunches the service if the system
 * (e.g. MagicOS) killed it. Exact alarms are allowed to start a foreground service.
 */
object Watchdog {
    private const val INTERVAL = 15 * 60_000L

    fun schedule(context: Context) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val pi = PendingIntent.getBroadcast(
            context, 3, Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = SystemClock.elapsedRealtime() + INTERVAL
        runCatching {
            if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = PiloteApp.graph(context)
        val pending = goAsync()
        graph.appScope.launchReceiver(pending) {
            if (graph.settings.onboardingDone.first()) {
                PiloteService.start(context)
                Watchdog.schedule(context)
            }
        }
    }
}
