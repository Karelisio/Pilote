package com.karelisio.pilote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** How a list of triggers (or conditions) is combined. */
@Serializable
enum class Logic { ALL, ANY }

/**
 * A profile: when its triggers (combined with [triggerLogic]) AND its conditions (combined with
 * [conditionLogic]) are satisfied, its actions are applied. Higher [priority] wins on conflicts.
 */
@Serializable
data class Profile(
    val id: Long = 0,
    val name: String,
    val shape: ProfileShape = ProfileShape.COOKIE,
    val color: Long? = null,
    val enabled: Boolean = true,
    val priority: Int = 50,
    val triggerLogic: Logic = Logic.ANY,
    val triggers: List<Trigger> = emptyList(),
    val conditionLogic: Logic = Logic.ALL,
    val conditions: List<Trigger> = emptyList(),
    val actions: List<Action> = emptyList(),
    val restoreOnExit: Boolean = true,
    val debounceSeconds: Int = 0,
) {
    val isManual: Boolean get() = triggers.any { it is Trigger.Manual }
    val usesAppTrigger: Boolean get() = (triggers + conditions).any { it is Trigger.AppOpen }
    val locationTriggers: List<Trigger.Location> get() = (triggers + conditions).filterIsInstance<Trigger.Location>()
    val timeTriggers: List<Trigger.Time> get() = (triggers + conditions).filterIsInstance<Trigger.Time>()
}

/** Expressive shape attached to a profile (mapped to MaterialShapes in the UI). */
@Serializable
enum class ProfileShape { COOKIE, SUNNY, CLOVER, GEM, FLOWER, PILL, BURST, HEART, PENTAGON, ARCH, PUFFY, GHOST }

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
sealed interface Trigger {
    val id: String
    /** When true, the trigger matches when its condition is NOT met (e.g. "charger unplugged"). */
    val negate: Boolean

    @Serializable @SerialName("location")
    data class Location(
        override val id: String = newId(),
        override val negate: Boolean = false,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val radiusMeters: Float = 150f,
        val label: String = "",
    ) : Trigger {
        val isSet: Boolean get() = latitude != 0.0 || longitude != 0.0
    }

    /** Time range on given days. Minutes since midnight; range may cross midnight. */
    @Serializable @SerialName("time")
    data class Time(
        override val id: String = newId(),
        override val negate: Boolean = false,
        val startMinute: Int = 22 * 60,
        val endMinute: Int = 7 * 60,
        /** ISO day numbers 1 (Monday) .. 7 (Sunday). */
        val days: Set<Int> = (1..7).toSet(),
    ) : Trigger

    @Serializable @SerialName("bluetooth")
    data class Bluetooth(
        override val id: String = newId(),
        override val negate: Boolean = false,
        val kind: BtKind = BtKind.SPECIFIC,
        val address: String = "",
        val name: String = "",
    ) : Trigger

    @Serializable @SerialName("wired_headset")
    data class WiredHeadset(
        override val id: String = newId(),
        override val negate: Boolean = false,
    ) : Trigger

    @Serializable @SerialName("wifi")
    data class Wifi(
        override val id: String = newId(),
        override val negate: Boolean = false,
        val ssid: String = "",
    ) : Trigger

    @Serializable @SerialName("charging")
    data class Charging(
        override val id: String = newId(),
        override val negate: Boolean = false,
    ) : Trigger

    @Serializable @SerialName("app_open")
    data class AppOpen(
        override val id: String = newId(),
        override val negate: Boolean = false,
        val packageName: String = "",
        val label: String = "",
    ) : Trigger

    /** Device turned on: active during [minutes] after boot. */
    @Serializable @SerialName("boot")
    data class Boot(
        override val id: String = newId(),
        override val negate: Boolean = false,
        val minutes: Int = 5,
    ) : Trigger

    @Serializable @SerialName("manual")
    data class Manual(
        override val id: String = newId(),
        override val negate: Boolean = false,
    ) : Trigger
}

@Serializable
enum class BtKind { SPECIFIC, ANY_CAR, ANY_HEADPHONES, ANY }

@Serializable
enum class RingerMode { NORMAL, VIBRATE, SILENT }

@Serializable
enum class AudioStream { RING, MEDIA, ALARM, NOTIFICATION }

@Serializable
enum class DndMode { OFF, PRIORITY, ALARMS, TOTAL }

@Serializable
enum class PrivateDnsMode { OFF, AUTO, HOSTNAME }

@Serializable
sealed interface Action {
    /**
     * Setting key controlled by this action, or null for one-shot actions (launch app, notify…).
     * Two actions with the same key conflict: the highest-priority active profile wins.
     */
    val key: String?

    @Serializable @SerialName("ringer")
    data class Ringer(val mode: RingerMode = RingerMode.VIBRATE) : Action {
        override val key get() = "ringer"
    }

    @Serializable @SerialName("volume")
    data class Volume(
        val stream: AudioStream = AudioStream.MEDIA,
        val percent: Int = 50,
        /** Exact index used when restoring a snapshot. */
        val rawIndex: Int? = null,
    ) : Action {
        override val key get() = "volume_${stream.name.lowercase()}"
    }

    @Serializable @SerialName("dnd")
    data class Dnd(val mode: DndMode = DndMode.PRIORITY) : Action {
        override val key get() = "dnd"
    }

    @Serializable @SerialName("brightness")
    data class Brightness(
        val auto: Boolean = false,
        val percent: Int = 50,
        val rawValue: Int? = null,
    ) : Action {
        override val key get() = "brightness"
    }

    @Serializable @SerialName("screen_timeout")
    data class ScreenTimeout(val seconds: Int = 60) : Action {
        override val key get() = "screen_timeout"
    }

    @Serializable @SerialName("rotation")
    data class Rotation(val auto: Boolean = true) : Action {
        override val key get() = "rotation"
    }

    @Serializable @SerialName("wifi")
    data class Wifi(val enabled: Boolean = true) : Action {
        override val key get() = "wifi"
    }

    @Serializable @SerialName("bluetooth")
    data class Bluetooth(val enabled: Boolean = true) : Action {
        override val key get() = "bluetooth"
    }

    @Serializable @SerialName("mobile_data")
    data class MobileData(val enabled: Boolean = true) : Action {
        override val key get() = "mobile_data"
    }

    @Serializable @SerialName("dark_mode")
    data class DarkMode(val enabled: Boolean = true) : Action {
        override val key get() = "dark_mode"
    }

    @Serializable @SerialName("private_dns")
    data class PrivateDns(val mode: PrivateDnsMode = PrivateDnsMode.AUTO, val host: String = "") : Action {
        override val key get() = "private_dns"
    }

    @Serializable @SerialName("launch_app")
    data class LaunchApp(val packageName: String = "", val label: String = "") : Action {
        override val key get() = null
    }

    @Serializable @SerialName("media_play")
    data object MediaPlay : Action {
        override val key get() = null
    }

    @Serializable @SerialName("notify")
    data class Notify(val title: String = "", val text: String = "") : Action {
        override val key get() = null
    }

    /** Window, transition and animator scales (0 = no animations). */
    @Serializable @SerialName("animation_scale")
    data class AnimationScale(val scale: Float = 0.5f) : Action {
        override val key get() = "animation_scale"
    }

    /** Screen refresh rate in Hz; 0 = automatic (highest available). Raw values are used to restore. */
    @Serializable @SerialName("refresh_rate")
    data class RefreshRate(
        val hz: Int = 60,
        val rawPeak: String? = null,
        val rawMin: String? = null,
        val rawUser: String? = null,
        val restore: Boolean = false,
    ) : Action {
        override val key get() = "refresh_rate"
    }

    /** Keeps the screen on while the profile is active. */
    @Serializable @SerialName("caffeine")
    data class Caffeine(val enabled: Boolean = true) : Action {
        override val key get() = "caffeine"
    }
}
