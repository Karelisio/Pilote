package com.karelisio.pilote.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.karelisio.pilote.MainActivity
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.R
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.util.Channels
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight foreground service ("Pilote actif") that keeps trigger listeners alive and shows
 * the active profile in a discreet notification.
 */
class PiloteService : LifecycleService() {
    private val graph by lazy { PiloteApp.graph(this) }

    override fun onCreate() {
        super.onCreate()
        startInForeground(buildNotification(emptyList(), paused = false))
        graph.registry.startMonitors()
        lifecycleScope.launch {
            combine(graph.engine.active, graph.settings.paused) { active, paused -> active to paused }
                .collect { (active, paused) ->
                    runCatching {
                        NotificationManagerCompat.from(this@PiloteService).notify(NOTIFICATION_ID, buildNotification(active, paused))
                    }
                }
        }
        // Safety net: re-evaluate every minute (exact alarms remain the primary time source).
        lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                graph.engine.refresh()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        graph.registry.stopMonitors()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun buildNotification(active: List<Profile>, paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val togglePause = PendingIntent.getBroadcast(
            this, 10, Intent(this, ControlReceiver::class.java).setAction(ControlReceiver.ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when {
            paused -> "Pilote en pause"
            active.isEmpty() -> "Aucun profil actif"
            else -> active.first().name
        }
        val text = when {
            paused -> "Les profils ne s'appliquent plus"
            active.size > 1 -> "Aussi actifs : " + active.drop(1).joinToString { it.name }
            active.size == 1 -> "Profil actif"
            else -> "Pilote surveille vos déclencheurs"
        }
        return NotificationCompat.Builder(this, Channels.SERVICE)
            .setSmallIcon(R.drawable.ic_stat_pilote)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (paused) "Reprendre" else "Pause", togglePause)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PiloteService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PiloteService::class.java))
        }
    }
}
