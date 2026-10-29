package com.karelisio.pilote.integrations

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karelisio.pilote.R
import com.karelisio.pilote.util.Channels

/** Opens an app; falls back to a tap-to-open notification when Android blocks background starts. */
class AppLauncher(private val context: Context) {

    fun label(pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    fun isInstalled(pkg: String) = label(pkg) != null

    fun launch(pkg: String, label: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: error("${label.ifBlank { pkg }} n'est pas installée")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (Settings.canDrawOverlays(context)) {
            context.startActivity(intent)
        } else {
            notifyLaunch(intent, label.ifBlank { label(pkg) ?: pkg }, pkg.hashCode())
        }
    }

    private fun notifyLaunch(intent: Intent, label: String, id: Int) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            error("Ouverture bloquée : autorisez la superposition ou les notifications")
        }
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, Channels.LAUNCH)
            .setSmallIcon(R.drawable.ic_stat_pilote)
            .setContentTitle("Ouvrir $label")
            .setContentText("Touchez pour ouvrir $label")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        NotificationManagerCompat.from(context).notify(LAUNCH_NOTIFICATION_BASE + (id and 0xFFF), n)
    }

    companion object {
        private const val LAUNCH_NOTIFICATION_BASE = 20_000
    }
}
