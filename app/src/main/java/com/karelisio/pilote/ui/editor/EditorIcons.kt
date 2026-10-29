package com.karelisio.pilote.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.Trigger

fun Trigger.icon(): ImageVector = when (this) {
    is Trigger.Location -> Icons.Rounded.Place
    is Trigger.Time -> Icons.Rounded.Schedule
    is Trigger.Bluetooth -> Icons.Rounded.Bluetooth
    is Trigger.WiredHeadset -> Icons.Rounded.Headphones
    is Trigger.Wifi -> Icons.Rounded.Wifi
    is Trigger.Charging -> Icons.Rounded.BatteryChargingFull
    is Trigger.AppOpen -> Icons.Rounded.Apps
    is Trigger.Boot -> Icons.Rounded.PowerSettingsNew
    is Trigger.Manual -> Icons.Rounded.TouchApp
}

fun Trigger.typeLabel(): String = when (this) {
    is Trigger.Location -> "Lieu"
    is Trigger.Time -> "Heure et jours"
    is Trigger.Bluetooth -> "Bluetooth"
    is Trigger.WiredHeadset -> "Écouteurs filaires"
    is Trigger.Wifi -> "Réseau Wi-Fi"
    is Trigger.Charging -> "Charge"
    is Trigger.AppOpen -> "Appli ouverte"
    is Trigger.Boot -> "Allumage de l'appareil"
    is Trigger.Manual -> "Manuel (tuile, widget)"
}

/** Trigger types offered in the "add" sheet. */
val TriggerPrototypes: List<Trigger> = listOf(
    Trigger.Location(), Trigger.Time(), Trigger.Bluetooth(), Trigger.Wifi(),
    Trigger.WiredHeadset(), Trigger.Charging(), Trigger.AppOpen(), Trigger.Boot(), Trigger.Manual(),
)

fun Action.icon(): ImageVector = when (this) {
    is Action.Ringer -> Icons.Rounded.Vibration
    is Action.Volume -> Icons.AutoMirrored.Rounded.VolumeUp
    is Action.Dnd -> Icons.Rounded.DoNotDisturbOn
    is Action.Brightness -> Icons.Rounded.BrightnessMedium
    is Action.ScreenTimeout -> Icons.Rounded.Timer
    is Action.Rotation -> Icons.Rounded.ScreenRotation
    is Action.Wifi -> Icons.Rounded.Wifi
    is Action.Bluetooth -> Icons.Rounded.Bluetooth
    is Action.MobileData -> Icons.Rounded.SignalCellularAlt
    is Action.DarkMode -> Icons.Rounded.DarkMode
    is Action.PrivateDns -> Icons.Rounded.Dns
    is Action.LaunchApp -> Icons.AutoMirrored.Rounded.OpenInNew
    Action.MediaPlay -> Icons.Rounded.PlayCircle
    is Action.Notify -> Icons.Rounded.Notifications
    is Action.AnimationScale -> Icons.Rounded.Animation
    is Action.RefreshRate -> Icons.Rounded.Speed
    is Action.Caffeine -> Icons.Rounded.Coffee
}

data class ActionGroup(val title: String, val actions: List<Action>)

val ActionGroups: List<ActionGroup> = listOf(
    ActionGroup("Son", listOf(Action.Ringer(), Action.Volume(), Action.Dnd())),
    ActionGroup("Écran", listOf(Action.Brightness(), Action.ScreenTimeout(), Action.Rotation(), Action.Caffeine())),
    ActionGroup("Affichage · Shizuku", listOf(Action.RefreshRate(), Action.AnimationScale())),
    ActionGroup("Connectivité · Shizuku", listOf(Action.Wifi(), Action.Bluetooth(), Action.MobileData(), Action.DarkMode(), Action.PrivateDns())),
    ActionGroup("Applis", listOf(Action.LaunchApp(), Action.MediaPlay, Action.Notify())),
)
