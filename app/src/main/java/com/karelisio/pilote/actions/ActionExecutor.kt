package com.karelisio.pilote.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.karelisio.pilote.R
import com.karelisio.pilote.integrations.AppLauncher
import com.karelisio.pilote.integrations.StatusIntegration
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.RingerMode
import com.karelisio.pilote.util.Channels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Availability(val available: Boolean, val reason: String? = null) {
    companion object {
        val OK = Availability(true)
        fun missing(reason: String) = Availability(false, reason)
    }
}

class ActionExecutor(
    private val context: Context,
    val system: SystemController,
    val advanced: AdvancedController,
    private val status: StatusIntegration,
    private val launcher: AppLauncher,
) {
    private val shizukuReason = "Nécessite Shizuku ou Shevery actif et autorisé (écran Accès avancé)"

    fun availability(action: Action): Availability = when (action) {
        is Action.Ringer ->
            if (action.mode == RingerMode.SILENT && !system.hasDndAccess) Availability.missing("Le mode silencieux nécessite l'accès « Ne pas déranger »")
            else Availability.OK
        is Action.Volume -> Availability.OK
        is Action.Dnd -> if (system.hasDndAccess) Availability.OK else Availability.missing("Autorisez l'accès « Ne pas déranger »")
        is Action.Brightness, is Action.ScreenTimeout, is Action.Rotation ->
            if (system.canWriteSettings) Availability.OK else Availability.missing("Autorisez la modification des paramètres système")
        is Action.Wifi, is Action.Bluetooth, is Action.MobileData, is Action.DarkMode ->
            if (advanced.shizukuReady) Availability.OK else Availability.missing(shizukuReason)
        is Action.PrivateDns ->
            if (advanced.shizukuReady || advanced.hasSecureSettings) Availability.OK
            else Availability.missing("Nécessite Shizuku/Shevery ou WRITE_SECURE_SETTINGS accordé par ADB")
        is Action.LaunchApp -> Availability.OK
        Action.MediaPlay -> Availability.OK
        is Action.Notify ->
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) Availability.OK
            else Availability.missing("Autorisez les notifications")
        is Action.StatusPreset -> when {
            !status.isInstalled() -> Availability.missing("Status n'est pas installée")
            !status.hasPermission() -> Availability.missing("Status doit être signée avec la même clé que Pilote")
            else -> Availability.OK
        }
    }

    suspend fun apply(action: Action): Result<Unit> = withContext(Dispatchers.Default) {
        val availability = availability(action)
        if (!availability.available) return@withContext Result.failure(IllegalStateException(availability.reason))
        when (action) {
            is Action.Ringer -> runCatching { system.setRinger(action.mode) }
            is Action.Volume -> runCatching { system.setVolume(action) }
            is Action.Dnd -> runCatching { system.setDnd(action.mode) }
            is Action.Brightness -> runCatching { system.setBrightness(action) }
            is Action.ScreenTimeout -> runCatching { system.setScreenTimeout(action.seconds) }
            is Action.Rotation -> runCatching { system.setRotation(action.auto) }
            is Action.Wifi -> advanced.setWifi(action.enabled)
            is Action.Bluetooth -> advanced.setBluetooth(action.enabled)
            is Action.MobileData -> advanced.setMobileData(action.enabled)
            is Action.DarkMode -> advanced.setDarkMode(action.enabled)
            is Action.PrivateDns -> advanced.setPrivateDns(action)
            is Action.LaunchApp -> runCatching { withContext(Dispatchers.Main) { launcher.launch(action.packageName, action.label) } }
            Action.MediaPlay -> runCatching { playMedia() }
            is Action.Notify -> runCatching { notifyInfo(action) }
            is Action.StatusPreset -> runCatching { status.applyPreset(action.presetId) }
        }
    }

    /** Returns an action that restores the current value of the setting [action] is about to change. */
    suspend fun capture(action: Action): Action? = withContext(Dispatchers.Default) {
        runCatching {
            when (action) {
                is Action.Ringer -> system.captureRinger()
                is Action.Volume -> system.captureVolume(action.stream)
                is Action.Dnd -> system.captureDnd()
                is Action.Brightness -> system.captureBrightness()
                is Action.ScreenTimeout -> system.captureScreenTimeout()
                is Action.Rotation -> system.captureRotation()
                is Action.Wifi -> advanced.captureWifi()
                is Action.Bluetooth -> advanced.captureBluetooth()
                is Action.MobileData -> advanced.captureMobileData()
                is Action.DarkMode -> advanced.captureDarkMode()
                is Action.PrivateDns -> advanced.capturePrivateDns()
                else -> null
            }
        }.getOrNull()
    }

    private fun playMedia() {
        val audio = context.getSystemService<AudioManager>()!!
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }

    private fun notifyInfo(action: Action.Notify) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            error("Notifications non autorisées")
        }
        val n = NotificationCompat.Builder(context, Channels.INFO)
            .setSmallIcon(R.drawable.ic_stat_pilote)
            .setContentTitle(action.title.ifBlank { "Pilote" })
            .setContentText(action.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(action.text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10_000).toInt() + 1000, n)
    }
}
