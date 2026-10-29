package com.karelisio.pilote.data

import android.content.Context
import androidx.core.content.edit
import com.karelisio.pilote.model.Action
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Which profile currently controls a setting key, and with which value. */
@Serializable
data class AppliedEntry(val profileId: Long, val action: Action)

/**
 * Small synchronous store for engine runtime state that must survive process death
 * (broadcast receivers may start a fresh process).
 */
class RuntimeStore(context: Context) {
    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
    private val appliedSerializer = MapSerializer(String.serializer(), AppliedEntry.serializer())

    var manualOn: Set<Long>
        get() = prefs.getStringSet("manual_on", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(value) = prefs.edit { putStringSet("manual_on", value.map { it.toString() }.toSet()) }

    var insideGeofences: Set<String>
        get() = prefs.getStringSet("inside_geofences", emptySet())!!.toSet()
        set(value) = prefs.edit { putStringSet("inside_geofences", value) }

    var activeProfiles: Set<Long>
        get() = prefs.getStringSet("active_profiles", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(value) = prefs.edit { putStringSet("active_profiles", value.map { it.toString() }.toSet()) }

    var applied: Map<String, AppliedEntry>
        get() = prefs.getString("applied", null)?.let {
            runCatching { PiloteJson.decodeFromString(appliedSerializer, it) }.getOrNull()
        } ?: emptyMap()
        set(value) = prefs.edit { putString("applied", PiloteJson.encodeToString(appliedSerializer, value)) }
}
