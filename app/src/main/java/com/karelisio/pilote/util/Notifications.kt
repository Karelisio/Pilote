package com.karelisio.pilote.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object Channels {
    const val SERVICE = "service"
    const val INFO = "info"
    const val LAUNCH = "launch"
    const val UPDATE = "update"

    fun create(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(SERVICE, "Pilote actif", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Notification discrète indiquant le profil actif"
                    setShowBadge(false)
                },
                NotificationChannel(INFO, "Notifications des profils", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications d'information envoyées par vos profils"
                },
                NotificationChannel(LAUNCH, "Ouverture d'applis", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Propose d'ouvrir une appli quand Android bloque l'ouverture automatique"
                },
                NotificationChannel(UPDATE, "Mises à jour", NotificationManager.IMPORTANCE_LOW),
            )
        )
    }
}
