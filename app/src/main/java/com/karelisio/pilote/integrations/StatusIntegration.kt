package com.karelisio.pilote.integrations

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StatusPresetInfo(val id: String, val name: String)

/**
 * Bridge with the "Status" app (same signing key). Contract:
 * - explicit broadcast [ACTION_APPLY_PRESET] to package [PACKAGE], extra [EXTRA_PRESET];
 * - protected by the signature permission [PERMISSION];
 * - presets listed by the ContentProvider at [PRESETS_URI] (columns: id, name).
 */
class StatusIntegration(private val context: Context) {

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    }.getOrDefault(false)

    fun hasPermission(): Boolean = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun presets(): List<StatusPresetInfo> = withContext(Dispatchers.IO) {
        if (!isInstalled()) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(PRESETS_URI, null, null, null, null)?.use { c ->
                val idCol = c.getColumnIndex("id").takeIf { it >= 0 } ?: c.getColumnIndex("_id")
                val nameCol = c.getColumnIndex("name")
                buildList {
                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getString(idCol) else continue
                        add(StatusPresetInfo(id, if (nameCol >= 0) c.getString(nameCol) ?: id else id))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun applyPreset(presetId: String) {
        check(isInstalled()) { "Status n'est pas installée" }
        val intent = Intent(ACTION_APPLY_PRESET)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(EXTRA_PRESET, presetId)
        context.sendBroadcast(intent, PERMISSION)
    }

    companion object {
        const val PACKAGE = "com.karelisio.status"
        const val ACTION_APPLY_PRESET = "com.karelisio.status.action.APPLY_PRESET"
        const val EXTRA_PRESET = "preset"
        const val PERMISSION = "com.karelisio.status.permission.APPLY_PRESET"
        const val AUTHORITY = "com.karelisio.status.presets"
        val PRESETS_URI: Uri = Uri.parse("content://$AUTHORITY/presets")
    }
}
