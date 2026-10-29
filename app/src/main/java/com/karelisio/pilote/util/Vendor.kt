package com.karelisio.pilote.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Honor / Huawei (MagicOS / EMUI) specific screens. */
object Vendor {
    val isHonorOrHuawei: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("honor", "huawei") || Build.BRAND.lowercase() in setOf("honor", "huawei")

    private val appLaunchComponents = listOf(
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.mainscreen.MainScreenActivity"),
    )

    /** Opens the "App launch" manager (manual management), falling back to the app details. */
    fun openAppLaunch(context: Context): Boolean {
        for (component in appLaunchComponents) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
