package com.karelisio.pilote.engine

import com.karelisio.pilote.data.RuntimeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BtDeviceKind { CAR, HEADPHONES, OTHER }

data class BtDevice(val address: String, val name: String, val kind: BtDeviceKind)

/** Snapshot of everything the triggers can observe. */
data class Facts(
    /** False until the listeners have reported their initial state (avoids false deactivations). */
    val ready: Boolean = false,
    val tick: Long = System.currentTimeMillis(),
    val insideGeofences: Set<String> = emptySet(),
    val bluetooth: Set<BtDevice> = emptySet(),
    val wiredHeadset: Boolean = false,
    val wifiSsid: String? = null,
    val charging: Boolean = false,
    val foregroundApp: String? = null,
    val manualOn: Set<Long> = emptySet(),
)

/** Central, observable source of trigger facts. Monitors write here, the engine reads. */
class FactsHub(private val runtime: RuntimeStore) {
    private val _state = MutableStateFlow(
        Facts(insideGeofences = runtime.insideGeofences, manualOn = runtime.manualOn)
    )
    val state: StateFlow<Facts> = _state.asStateFlow()

    fun tick() = _state.update { it.copy(tick = System.currentTimeMillis()) }

    fun setReady(ready: Boolean) = _state.update { it.copy(ready = ready) }

    fun setGeofence(id: String, inside: Boolean) {
        val next = runtime.insideGeofences.let { if (inside) it + id else it - id }
        runtime.insideGeofences = next
        _state.update { it.copy(insideGeofences = next) }
    }

    /** Drops geofence ids that are no longer registered. */
    fun retainGeofences(ids: Set<String>) {
        val next = runtime.insideGeofences.intersect(ids)
        runtime.insideGeofences = next
        _state.update { it.copy(insideGeofences = next) }
    }

    fun btConnected(device: BtDevice) = _state.update { f ->
        f.copy(bluetooth = f.bluetooth.filterNot { it.address == device.address }.toSet() + device)
    }

    fun btDisconnected(address: String) = _state.update { f ->
        f.copy(bluetooth = f.bluetooth.filterNot { it.address == address }.toSet())
    }

    fun setBluetooth(devices: Set<BtDevice>) = _state.update { it.copy(bluetooth = devices) }
    fun setWiredHeadset(connected: Boolean) = _state.update { it.copy(wiredHeadset = connected) }
    fun setWifi(ssid: String?) = _state.update { it.copy(wifiSsid = ssid) }
    fun setCharging(charging: Boolean) = _state.update { it.copy(charging = charging) }
    fun setForegroundApp(pkg: String?) = _state.update { if (it.foregroundApp == pkg) it else it.copy(foregroundApp = pkg) }

    fun setManual(profileId: Long, on: Boolean) {
        val next = runtime.manualOn.let { if (on) it + profileId else it - profileId }
        runtime.manualOn = next
        _state.update { it.copy(manualOn = next) }
    }

    fun isManualOn(profileId: Long) = profileId in _state.value.manualOn
}
