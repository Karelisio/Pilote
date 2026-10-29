package com.karelisio.pilote.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.data.HistoryKind
import com.karelisio.pilote.engine.geofenceId
import com.karelisio.pilote.model.Profile
import kotlinx.coroutines.tasks.await

class GeofenceRegistrar(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, 2,
            Intent(context, GeofenceReceiver::class.java).setAction(ACTION_GEOFENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Replaces all registered geofences; returns the ids now registered. */
    @SuppressLint("MissingPermission")
    suspend fun register(profiles: List<Profile>): Set<String> {
        val fences = profiles.filter { it.enabled }.flatMap { p ->
            p.locationTriggers.filter { it.isSet }.map { t ->
                Geofence.Builder()
                    .setRequestId(geofenceId(p.id, t))
                    .setCircularRegion(t.latitude, t.longitude, t.radiusMeters.coerceAtLeast(80f))
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setNotificationResponsiveness(30_000)
                    .build()
            }
        }.take(100)
        runCatching { client.removeGeofences(pendingIntent).await() }
        if (fences.isEmpty() || !hasPermission()) return emptySet()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        return runCatching {
            client.addGeofences(request, pendingIntent).await()
            fences.map { it.requestId }.toSet()
        }.onFailure { Log.w("Pilote", "Géorepérage refusé", it) }.getOrDefault(emptySet())
    }

    companion object {
        const val ACTION_GEOFENCE = "com.karelisio.pilote.GEOFENCE"
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val graph = PiloteApp.graph(context)
        val pending = goAsync()
        graph.appScope.launchReceiver(pending) {
            if (event.hasError()) {
                graph.history.log(HistoryKind.INFO, null, "Lieu", "Erreur de géorepérage : ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
                return@launchReceiver
            }
            val inside = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> true
                Geofence.GEOFENCE_TRANSITION_EXIT -> false
                else -> return@launchReceiver
            }
            event.triggeringGeofences?.forEach { graph.facts.setGeofence(it.requestId, inside) }
            com.karelisio.pilote.service.PiloteService.start(context)
        }
    }
}
