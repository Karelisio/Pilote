package com.karelisio.pilote.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.karelisio.pilote.engine.BtDevice
import com.karelisio.pilote.engine.BtDeviceKind
import com.karelisio.pilote.engine.FactsHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface Monitor {
    fun start()
    fun stop()
}

private fun Context.has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Bluetooth ACL connections (car, headphones, specific device). */
class BluetoothMonitor(private val context: Context, private val facts: FactsHub) : Monitor {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> facts.btConnected(device.toBtDevice())
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> facts.btDisconnected(device.address)
                BluetoothAdapter.ACTION_STATE_CHANGED -> Unit
            }
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF
            ) facts.setBluetooth(emptySet())
        }
    }

    override fun start() {
        if (registered || !context.has(Manifest.permission.BLUETOOTH_CONNECT)) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        loadInitial()
    }

    /** ACL broadcasts are not sticky: read the currently connected audio devices once. */
    @SuppressLint("MissingPermission")
    private fun loadInitial() {
        val adapter = context.getSystemService<BluetoothManager>()?.adapter ?: return
        if (!adapter.isEnabled) return
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profileId ->
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching { proxy.connectedDevices.forEach { facts.btConnected(it.toBtDevice()) } }
                    adapter.closeProfileProxy(profile, proxy)
                }
                override fun onServiceDisconnected(profile: Int) = Unit
            }, profileId)
        }
    }

    override fun stop() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun BluetoothDevice.toBtDevice(): BtDevice {
            val name = runCatching { alias ?: name }.getOrNull() ?: address
            return BtDevice(address, name, kindOf(runCatching { bluetoothClass?.deviceClass }.getOrNull()))
        }

        fun kindOf(deviceClass: Int?): BtDeviceKind = when (deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BtDeviceKind.CAR
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> BtDeviceKind.HEADPHONES
            else -> BtDeviceKind.OTHER
        }

        /** Paired devices for the trigger picker. */
        @SuppressLint("MissingPermission")
        fun bonded(context: Context): List<BtDevice> {
            if (!context.has(Manifest.permission.BLUETOOTH_CONNECT)) return emptyList()
            return runCatching {
                context.getSystemService<BluetoothManager>()?.adapter?.bondedDevices.orEmpty().map { it.toBtDevice() }.sortedBy { it.name }
            }.getOrDefault(emptyList())
        }
    }
}

/** Wired (jack / USB-C) headsets. */
class HeadsetMonitor(private val context: Context, private val facts: FactsHub) : Monitor {
    private val audio = context.getSystemService<AudioManager>()!!
    private val wiredTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
    )
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    private fun refresh() {
        facts.setWiredHeadset(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in wiredTypes })
    }

    override fun start() {
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    override fun stop() = audio.unregisterAudioDeviceCallback(callback)
}

/** Current Wi-Fi SSID (needs location permission for the SSID to be visible). */
class WifiMonitor(private val context: Context, private val facts: FactsHub) : Monitor {
    private val cm = context.getSystemService<ConnectivityManager>()!!
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val info = caps.transportInfo as? WifiInfo ?: return
            facts.setWifi(cleanSsid(info.ssid))
        }

        override fun onLost(network: Network) = facts.setWifi(null)
    }

    override fun start() {
        if (registered) return
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { cm.registerNetworkCallback(request, callback); registered = true }
    }

    override fun stop() {
        if (registered) runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
    }

    companion object {
        fun cleanSsid(raw: String?): String? =
            raw?.removeSurrounding("\"")?.takeUnless { it.isBlank() || it == WifiManagerUnknownSsid }

        private const val WifiManagerUnknownSsid = "<unknown ssid>"
    }
}

/** Charger plugged / unplugged. */
class PowerMonitor(private val context: Context, private val facts: FactsHub) : Monitor {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> facts.setCharging(true)
                Intent.ACTION_POWER_DISCONNECTED -> facts.setCharging(false)
            }
        }
    }

    override fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        val battery = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        facts.setCharging((battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
    }

    override fun stop() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}

/** Foreground app via UsageStatsManager; only polls while a profile uses the trigger. */
class AppUsageMonitor(
    private val context: Context,
    private val facts: FactsHub,
    private val scope: CoroutineScope,
) : Monitor {
    private val usage = context.getSystemService<UsageStatsManager>()!!
    private val power = context.getSystemService<PowerManager>()!!
    private var job: Job? = null
    private var lastQuery = 0L

    var enabled: Boolean = false
        set(value) {
            field = value
            if (value) start() else stop()
        }

    override fun start() {
        if (!enabled || job?.isActive == true) return
        lastQuery = System.currentTimeMillis() - 60_000
        job = scope.launch {
            while (isActive) {
                if (power.isInteractive) poll() else facts.setForegroundApp(null)
                delay(if (power.isInteractive) 2_000 else 10_000)
            }
        }
    }

    private fun poll() {
        val now = System.currentTimeMillis()
        val events = runCatching { usage.queryEvents(lastQuery, now) }.getOrNull() ?: return
        lastQuery = now - 1_000
        val e = UsageEvents.Event()
        var resumed: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) resumed = e.packageName
        }
        if (resumed != null) facts.setForegroundApp(resumed)
    }

    override fun stop() {
        job?.cancel()
        job = null
        facts.setForegroundApp(null)
    }
}
