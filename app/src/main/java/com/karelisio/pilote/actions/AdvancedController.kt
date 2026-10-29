package com.karelisio.pilote.actions

import android.Manifest
import android.app.UiModeManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import androidx.core.content.getSystemService
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.PrivateDnsMode
import com.karelisio.pilote.shizuku.ShizukuManager
import kotlin.math.roundToInt

/**
 * Settings that need elevated access: through Shizuku/Shevery (shell commands) or,
 * for what is a plain secure/global setting, directly when WRITE_SECURE_SETTINGS was granted by ADB.
 */
class AdvancedController(private val context: Context, private val shizuku: ShizukuManager) {

    val hasSecureSettings: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    val shizukuReady: Boolean get() = shizuku.state.value.ready

    private suspend fun shell(vararg commands: String): Result<String> {
        var last: Result<String> = Result.failure(IllegalStateException("Aucune commande"))
        for (c in commands) {
            last = shizuku.exec(c)
            if (last.isSuccess) return last
        }
        return last
    }

    suspend fun setWifi(on: Boolean) = shell(
        "cmd wifi set-wifi-enabled ${if (on) "enabled" else "disabled"}",
        "svc wifi ${if (on) "enable" else "disable"}",
    ).map { }

    @Suppress("DEPRECATION")
    fun captureWifi() = Action.Wifi(context.getSystemService<WifiManager>()?.isWifiEnabled == true)

    suspend fun setBluetooth(on: Boolean) = shell(
        "cmd bluetooth_manager ${if (on) "enable" else "disable"}",
        "svc bluetooth ${if (on) "enable" else "disable"}",
    ).map { }

    fun captureBluetooth() = Action.Bluetooth(
        runCatching { context.getSystemService<BluetoothManager>()?.adapter?.isEnabled == true }.getOrDefault(false)
    )

    suspend fun setMobileData(on: Boolean) = shell("svc data ${if (on) "enable" else "disable"}").map { }

    suspend fun captureMobileData(): Action.MobileData {
        val viaShell = shell("settings get global mobile_data").getOrNull()?.trim()
        val enabled = viaShell?.let { it == "1" }
            ?: runCatching { Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1 }.getOrDefault(true)
        return Action.MobileData(enabled)
    }

    suspend fun setDarkMode(on: Boolean) = shell("cmd uimode night ${if (on) "yes" else "no"}").map { }

    fun captureDarkMode() = Action.DarkMode(context.getSystemService<UiModeManager>()?.nightMode == UiModeManager.MODE_NIGHT_YES)

    suspend fun setPrivateDns(action: Action.PrivateDns): Result<Unit> {
        val mode = when (action.mode) {
            PrivateDnsMode.OFF -> "off"
            PrivateDnsMode.AUTO -> "opportunistic"
            PrivateDnsMode.HOSTNAME -> "hostname"
        }
        if (hasSecureSettings) {
            return runCatching {
                val cr = context.contentResolver
                if (action.mode == PrivateDnsMode.HOSTNAME) Settings.Global.putString(cr, "private_dns_specifier", action.host.trim())
                Settings.Global.putString(cr, "private_dns_mode", mode)
            }
        }
        val host = action.host.trim().replace(Regex("[^A-Za-z0-9.-]"), "")
        val cmd = buildString {
            if (action.mode == PrivateDnsMode.HOSTNAME) append("settings put global private_dns_specifier $host && ")
            append("settings put global private_dns_mode $mode")
        }
        return shell(cmd).map { }
    }

    suspend fun capturePrivateDns(): Action.PrivateDns {
        val cr = context.contentResolver
        val mode = runCatching { Settings.Global.getString(cr, "private_dns_mode") }.getOrNull()
            ?: shell("settings get global private_dns_mode").getOrNull()?.trim()
        val host = runCatching { Settings.Global.getString(cr, "private_dns_specifier") }.getOrNull()
            ?: shell("settings get global private_dns_specifier").getOrNull()?.trim()
        return Action.PrivateDns(
            when (mode) {
                "off" -> PrivateDnsMode.OFF
                "hostname" -> PrivateDnsMode.HOSTNAME
                else -> PrivateDnsMode.AUTO
            },
            host?.takeUnless { it == "null" }.orEmpty(),
        )
    }

    private val animationKeys = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    suspend fun setAnimationScale(scale: Float): Result<Unit> {
        if (hasSecureSettings) {
            val direct = runCatching { animationKeys.forEach { Settings.Global.putFloat(context.contentResolver, it, scale) } }
            if (direct.isSuccess) return direct
        }
        return shell(animationKeys.joinToString(" && ") { "settings put global $it $scale" }).map { }
    }

    fun captureAnimationScale() = Action.AnimationScale(
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    )

    private val maxRefreshRate: Int
        get() = runCatching {
            context.getSystemService<DisplayManager>()!!.getDisplay(Display.DEFAULT_DISPLAY).supportedModes.maxOf { it.refreshRate }.roundToInt()
        }.getOrDefault(120)

    /** Reads a (possibly hidden) system setting; null when unset. */
    private suspend fun getSystem(key: String): String? {
        val direct = runCatching { Settings.System.getString(context.contentResolver, key) }
        val value = if (direct.isSuccess) direct.getOrNull() else shell("settings get system $key").getOrNull()?.trim()
        return value?.takeUnless { it.isBlank() || it == "null" }
    }

    /** Writes or deletes (value = null) a system setting, directly if allowed, else through Shizuku. */
    private suspend fun putSystem(key: String, value: String?): Result<Unit> {
        if (hasSecureSettings && value != null) {
            val direct = runCatching { check(Settings.System.putString(context.contentResolver, key, value)) }
            if (direct.isSuccess) return direct
        }
        return shell(if (value == null) "settings delete system $key" else "settings put system $key $value").map { }
    }

    suspend fun setRefreshRate(action: Action.RefreshRate): Result<Unit> = runCatching {
        if (action.restore) {
            putSystem(PEAK_REFRESH, action.rawPeak).getOrThrow()
            putSystem(MIN_REFRESH, action.rawMin).getOrThrow()
            if (action.rawUser != null) putSystem(HONOR_REFRESH, action.rawUser).getOrThrow()
            return@runCatching
        }
        val peak = if (action.hz == 0) maxRefreshRate else action.hz
        putSystem(PEAK_REFRESH, "$peak.0").getOrThrow()
        putSystem(MIN_REFRESH, if (action.hz == 0) null else "${action.hz}.0").getOrThrow()
        // Honor / MagicOS keeps its own user choice: update it when present.
        if (getSystem(HONOR_REFRESH) != null) putSystem(HONOR_REFRESH, "$peak").getOrThrow()
    }

    suspend fun captureRefreshRate() = Action.RefreshRate(
        hz = 0,
        rawPeak = getSystem(PEAK_REFRESH),
        rawMin = getSystem(MIN_REFRESH),
        rawUser = getSystem(HONOR_REFRESH),
        restore = true,
    )

    companion object {
        private const val PEAK_REFRESH = "peak_refresh_rate"
        private const val MIN_REFRESH = "min_refresh_rate"
        private const val HONOR_REFRESH = "user_refresh_rate"

        const val ADB_GRANT_COMMAND = "adb shell pm grant com.karelisio.pilote android.permission.WRITE_SECURE_SETTINGS"
    }
}
