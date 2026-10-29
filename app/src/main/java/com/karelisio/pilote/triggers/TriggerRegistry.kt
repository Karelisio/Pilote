package com.karelisio.pilote.triggers

import android.content.Context
import com.karelisio.pilote.data.ProfileRepository
import com.karelisio.pilote.engine.FactsHub
import com.karelisio.pilote.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps system registrations (exact alarms, geofences, listeners) in sync with the profiles.
 * Listeners only run while the foreground service is alive.
 */
class TriggerRegistry(
    context: Context,
    private val scope: CoroutineScope,
    private val profiles: ProfileRepository,
    private val facts: FactsHub,
) {
    private val time = TimeScheduler(context)
    private val geofences = GeofenceRegistrar(context)
    private val appUsage = AppUsageMonitor(context, facts, scope)
    private val monitors: List<Monitor> = listOf(
        BluetoothMonitor(context, facts),
        HeadsetMonitor(context, facts),
        WifiMonitor(context, facts),
        PowerMonitor(context, facts),
    )
    private val mutex = Mutex()
    private var latest: List<Profile> = emptyList()
    private var geofenceSignature: String? = null
    private var monitorsRunning = false
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            profiles.profiles.collect { list ->
                latest = list
                sync(force = false)
            }
        }
    }

    private suspend fun sync(force: Boolean) = mutex.withLock {
        time.reschedule(latest)
        val signature = latest.filter { it.enabled }.flatMap { p -> p.locationTriggers.map { "${p.id}:$it" } }.sorted().joinToString("|")
        if (force || signature != geofenceSignature) {
            val ids = geofences.register(latest)
            facts.retainGeofences(ids)
            geofenceSignature = signature
        }
        appUsage.enabled = monitorsRunning && latest.any { it.enabled && it.usesAppTrigger }
    }

    /** After boot / package update / permission change: register everything again. */
    fun reRegisterAll() {
        scope.launch {
            if (latest.isEmpty()) latest = profiles.all()
            sync(force = true)
        }
    }

    fun rescheduleTime() {
        scope.launch { mutex.withLock { time.reschedule(latest.ifEmpty { profiles.all() }) } }
    }

    fun startMonitors() {
        if (monitorsRunning) return
        monitorsRunning = true
        monitors.forEach { runCatching { it.start() } }
        appUsage.enabled = latest.any { it.enabled && it.usesAppTrigger }
    }

    /** Restart listeners whose permission may have just been granted. */
    fun restartMonitors() {
        if (!monitorsRunning) return
        monitors.forEach { runCatching { it.stop(); it.start() } }
    }

    fun stopMonitors() {
        monitorsRunning = false
        monitors.forEach { runCatching { it.stop() } }
        appUsage.enabled = false
    }
}
