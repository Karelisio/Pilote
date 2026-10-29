package com.karelisio.pilote.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.getSystemService
import com.karelisio.pilote.AppGraph

enum class PermissionId { NOTIFICATIONS, LOCATION, BACKGROUND_LOCATION, BLUETOOTH, DND, WRITE_SETTINGS, EXACT_ALARMS, USAGE, BATTERY, OVERLAY, SHIZUKU, INSTALL }

data class PermissionSpec(
    val id: PermissionId,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val optional: Boolean = false,
    /** Runtime permissions requested through the system dialog. */
    val runtime: List<String> = emptyList(),
    /** Settings screen to open (checked again when the user comes back). */
    val settingsIntent: ((Context) -> Intent)? = null,
    val isGranted: (Context, AppGraph) -> Boolean,
)

private fun Context.has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")

@SuppressLint("BatteryLife")
val PermissionSpecs: List<PermissionSpec> = listOf(
    PermissionSpec(
        PermissionId.NOTIFICATIONS, "Notifications",
        "Pour la notification discrète « Pilote actif », les notifications de vos profils et les mises à jour.",
        Icons.Rounded.Notifications,
        runtime = listOf(Manifest.permission.POST_NOTIFICATIONS),
        isGranted = { c, _ -> Build.VERSION.SDK_INT < 33 || c.has(Manifest.permission.POST_NOTIFICATIONS) },
    ),
    PermissionSpec(
        PermissionId.LOCATION, "Localisation précise",
        "Pour les déclencheurs « Lieu » et pour lire le nom du réseau Wi-Fi. Rien ne quitte votre téléphone.",
        Icons.Rounded.LocationOn,
        runtime = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        isGranted = { c, _ -> c.has(Manifest.permission.ACCESS_FINE_LOCATION) },
    ),
    PermissionSpec(
        PermissionId.BACKGROUND_LOCATION, "Localisation en arrière-plan",
        "Choisissez « Toujours autoriser » : les géorepérages fonctionnent ainsi même quand Pilote est fermée.",
        Icons.Rounded.MyLocation,
        runtime = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        isGranted = { c, _ -> c.has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
    ),
    PermissionSpec(
        PermissionId.BLUETOOTH, "Appareils à proximité",
        "Pour reconnaître la voiture, les écouteurs et vos appareils Bluetooth.",
        Icons.Rounded.Bluetooth,
        runtime = listOf(Manifest.permission.BLUETOOTH_CONNECT),
        isGranted = { c, _ -> c.has(Manifest.permission.BLUETOOTH_CONNECT) },
    ),
    PermissionSpec(
        PermissionId.DND, "Accès « Ne pas déranger »",
        "Pour activer Ne pas déranger et le mode silencieux. Activez Pilote dans la liste.",
        Icons.Rounded.DoNotDisturbOn,
        settingsIntent = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        isGranted = { c, _ -> c.getSystemService<NotificationManager>()?.isNotificationPolicyAccessGranted == true },
    ),
    PermissionSpec(
        PermissionId.WRITE_SETTINGS, "Modifier les paramètres système",
        "Pour la luminosité, le délai de mise en veille et la rotation automatique.",
        Icons.Rounded.Tune,
        settingsIntent = { Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri(it)) },
        isGranted = { c, _ -> Settings.System.canWrite(c) },
    ),
    PermissionSpec(
        PermissionId.EXACT_ALARMS, "Alarmes exactes",
        "Pour que les plages horaires démarrent et s'arrêtent à la minute près.",
        Icons.Rounded.Alarm,
        settingsIntent = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(it)) },
        isGranted = { c, _ -> c.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true },
    ),
    PermissionSpec(
        PermissionId.USAGE, "Accès aux données d'utilisation",
        "Pour le déclencheur « Appli ouverte ». Interrogé seulement si un profil l'utilise.",
        Icons.Rounded.QueryStats,
        settingsIntent = { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri(it)) },
        isGranted = { c, _ ->
            val ops = c.getSystemService<AppOpsManager>()
            ops?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
        },
    ),
    PermissionSpec(
        PermissionId.BATTERY, "Pas d'optimisation de batterie",
        "Indispensable sur Honor (MagicOS ferme les applis) : sinon les profils ne se déclenchent plus.",
        Icons.Rounded.BatteryChargingFull,
        settingsIntent = { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(it)) },
        isGranted = { c, _ -> c.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(c.packageName) == true },
    ),
    PermissionSpec(
        PermissionId.OVERLAY, "Ouvrir des applis automatiquement",
        "Optionnel : permet à l'action « Ouvrir une appli » (ex. Rushio) de s'ouvrir seule. Sinon, une notification vous le propose.",
        Icons.Rounded.Layers,
        optional = true,
        settingsIntent = { Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(it)) },
        isGranted = { c, _ -> Settings.canDrawOverlays(c) },
    ),
    PermissionSpec(
        PermissionId.SHIZUKU, "Shizuku ou Shevery",
        "Optionnel : Wi-Fi, Bluetooth, données mobiles, mode sombre et DNS privé. Démarrez Shizuku, Shevery ou un fork, puis autorisez Pilote.",
        Icons.Rounded.AdminPanelSettings,
        optional = true,
        isGranted = { _, g -> g.shizuku.state.value.ready },
    ),
    PermissionSpec(
        PermissionId.INSTALL, "Installer les mises à jour",
        "Pour installer les nouvelles versions de Pilote directement depuis l'appli.",
        Icons.Rounded.InstallMobile,
        optional = true,
        settingsIntent = { Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri(it)) },
        isGranted = { c, _ -> c.packageManager.canRequestPackageInstalls() },
    ),
)
