package com.karelisio.pilote.actions

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.AudioStream
import com.karelisio.pilote.model.DndMode
import com.karelisio.pilote.model.RingerMode
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Settings reachable with regular permissions (audio, DND, WRITE_SETTINGS). */
class SystemController(private val context: Context) {
    private val audio = context.getSystemService<AudioManager>()!!
    private val notifications = context.getSystemService<NotificationManager>()!!
    private val resolver get() = context.contentResolver

    val hasDndAccess: Boolean get() = notifications.isNotificationPolicyAccessGranted
    val canWriteSettings: Boolean get() = Settings.System.canWrite(context)

    private fun AudioStream.id() = when (this) {
        AudioStream.RING -> AudioManager.STREAM_RING
        AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
        AudioStream.ALARM -> AudioManager.STREAM_ALARM
        AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    }

    fun setRinger(mode: RingerMode) {
        audio.ringerMode = when (mode) {
            RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
    }

    fun captureRinger() = Action.Ringer(
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
    )

    fun setVolume(action: Action.Volume) {
        val stream = action.stream.id()
        val max = audio.getStreamMaxVolume(stream)
        val min = audio.getStreamMinVolume(stream)
        val index = (action.rawIndex ?: (action.percent / 100f * max).roundToInt()).coerceIn(min, max)
        audio.setStreamVolume(stream, index, 0)
    }

    fun captureVolume(streamType: AudioStream): Action.Volume {
        val stream = streamType.id()
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        val current = audio.getStreamVolume(stream)
        return Action.Volume(streamType, (current * 100f / max).roundToInt(), current)
    }

    fun setDnd(mode: DndMode) {
        notifications.setInterruptionFilter(
            when (mode) {
                DndMode.OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
                DndMode.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                DndMode.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                DndMode.TOTAL -> NotificationManager.INTERRUPTION_FILTER_NONE
            }
        )
    }

    fun captureDnd() = Action.Dnd(
        when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DndMode.PRIORITY
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> DndMode.ALARMS
            NotificationManager.INTERRUPTION_FILTER_NONE -> DndMode.TOTAL
            else -> DndMode.OFF
        }
    )

    fun setBrightness(action: Action.Brightness) {
        Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (action.auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        if (!action.auto || action.rawValue != null) {
            // Perceptual curve: the system slider is roughly quadratic.
            val raw = action.rawValue ?: (255f * (action.percent / 100f).pow(2)).roundToInt().coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        }
    }

    fun captureBrightness(): Action.Brightness {
        val auto = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return Action.Brightness(auto, (sqrt(raw / 255f) * 100).roundToInt(), raw)
    }

    fun setScreenTimeout(seconds: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
    }

    fun captureScreenTimeout() = Action.ScreenTimeout(Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000) / 1000)

    fun setRotation(auto: Boolean) {
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (auto) 1 else 0)
    }

    fun captureRotation() = Action.Rotation(Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1)
}
