package com.karelisio.pilote.actions

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService

/** Keeps the screen on through a screen wake lock held by the (foreground) process. */
class Caffeine(context: Context) {
    private val power = context.getSystemService<PowerManager>()!!
    private var lock: PowerManager.WakeLock? = null

    val isOn: Boolean get() = lock?.isHeld == true

    @Suppress("DEPRECATION")
    @Synchronized
    fun set(enabled: Boolean) {
        if (enabled) {
            if (isOn) return
            lock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "Pilote:caffeine",
            ).apply { setReferenceCounted(false); acquire() }
        } else {
            lock?.takeIf { it.isHeld }?.release()
            lock = null
        }
    }
}
