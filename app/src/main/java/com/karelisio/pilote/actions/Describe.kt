package com.karelisio.pilote.actions

import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.AudioStream
import com.karelisio.pilote.model.DndMode
import com.karelisio.pilote.model.PrivateDnsMode
import com.karelisio.pilote.model.RingerMode

fun RingerMode.label() = when (this) {
    RingerMode.NORMAL -> "Sonnerie"
    RingerMode.VIBRATE -> "Vibreur"
    RingerMode.SILENT -> "Silencieux"
}

fun AudioStream.label() = when (this) {
    AudioStream.RING -> "Sonnerie"
    AudioStream.MEDIA -> "Média"
    AudioStream.ALARM -> "Alarme"
    AudioStream.NOTIFICATION -> "Notifications"
}

fun DndMode.label() = when (this) {
    DndMode.OFF -> "désactivé"
    DndMode.PRIORITY -> "prioritaires uniquement"
    DndMode.ALARMS -> "alarmes uniquement"
    DndMode.TOTAL -> "silence total"
}

fun PrivateDnsMode.label() = when (this) {
    PrivateDnsMode.OFF -> "désactivé"
    PrivateDnsMode.AUTO -> "automatique"
    PrivateDnsMode.HOSTNAME -> "personnalisé"
}

private fun onOff(b: Boolean) = if (b) "activé" else "désactivé"

fun Action.title(): String = when (this) {
    is Action.Ringer -> "Mode sonnerie"
    is Action.Volume -> "Volume ${stream.label().lowercase()}"
    is Action.Dnd -> "Ne pas déranger"
    is Action.Brightness -> "Luminosité"
    is Action.ScreenTimeout -> "Mise en veille"
    is Action.Rotation -> "Rotation auto"
    is Action.Wifi -> "Wi-Fi"
    is Action.Bluetooth -> "Bluetooth"
    is Action.MobileData -> "Données mobiles"
    is Action.DarkMode -> "Mode sombre"
    is Action.PrivateDns -> "DNS privé"
    is Action.LaunchApp -> "Ouvrir une appli"
    Action.MediaPlay -> "Lancer la lecture"
    is Action.Notify -> "Notification"
    is Action.AnimationScale -> "Échelle des animations"
    is Action.RefreshRate -> "Fréquence d'écran"
    is Action.Caffeine -> "Caféine"
}

fun Action.describe(): String = when (this) {
    is Action.Ringer -> mode.label()
    is Action.Volume -> "Volume ${stream.label().lowercase()} $percent %"
    is Action.Dnd -> "Ne pas déranger ${mode.label()}"
    is Action.Brightness -> if (auto) "Luminosité auto" else "Luminosité $percent %"
    is Action.ScreenTimeout -> "Veille après ${formatSeconds(seconds)}"
    is Action.Rotation -> "Rotation ${if (auto) "auto" else "verrouillée"}"
    is Action.Wifi -> "Wi-Fi ${onOff(enabled)}"
    is Action.Bluetooth -> "Bluetooth ${onOff(enabled)}"
    is Action.MobileData -> "Données mobiles ${onOff(enabled)}"
    is Action.DarkMode -> "Mode sombre ${onOff(enabled)}"
    is Action.PrivateDns -> "DNS privé ${if (mode == PrivateDnsMode.HOSTNAME) host else mode.label()}"
    is Action.LaunchApp -> "Ouvrir ${label.ifBlank { packageName }}"
    Action.MediaPlay -> "Lecture média"
    is Action.Notify -> "Notification « ${title.ifBlank { text }} »"
    is Action.AnimationScale -> if (scale == 0f) "Animations désactivées" else "Animations ×${formatScale(scale)}"
    is Action.RefreshRate -> if (restore) "Fréquence d'écran précédente" else if (hz == 0) "Fréquence d'écran auto" else "Écran $hz Hz"
    is Action.Caffeine -> if (enabled) "Écran toujours allumé" else "Caféine désactivée"
}

fun formatScale(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString().replace('.', ',')

fun formatSeconds(s: Int): String = when {
    s < 60 -> "$s s"
    s % 60 == 0 && s < 3600 -> "${s / 60} min"
    s >= 3600 -> "${s / 3600} h"
    else -> "${s / 60} min ${s % 60} s"
}
