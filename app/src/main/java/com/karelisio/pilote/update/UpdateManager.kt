package com.karelisio.pilote.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.karelisio.pilote.BuildConfig
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val version: String,
    val tag: String,
    val changelog: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Installing : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** In-app updates from GitHub Releases (latest release, APK asset, installed via PackageInstaller). */
class UpdateManager(private val context: Context, private val settings: SettingsStore) {
    private val _available = MutableStateFlow<ReleaseInfo?>(null)
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Checks GitHub at most every 6 h unless [force]. Returns the newer release, if any. */
    suspend fun check(force: Boolean = false): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - settings.lastUpdateCheck() < TimeUnit.HOURS.toMillis(6)) {
            return@withContext Result.success(_available.value)
        }
        runCatching {
            val release = fetchLatest()
            settings.setLastUpdateCheck(now)
            val newer = release?.takeIf { isNewer(it.version, currentVersion) }
            _available.value = newer
            newer
        }
    }

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Pilote/${BuildConfig.VERSION_NAME}")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode == 404) return null
            check(conn.responseCode == 200) { "GitHub a répondu ${conn.responseCode}" }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
            val asset = root["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it.string("name")?.endsWith(".apk") == true }
                ?: return null
            return ReleaseInfo(
                version = tag.removePrefix("v"),
                tag = tag,
                changelog = root.string("body").orEmpty().trim(),
                apkUrl = asset.string("browser_download_url") ?: return null,
                sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

    suspend fun dismiss() {
        _available.value?.let { settings.setDismissedVersion(it.version) }
        _available.value = null
    }

    suspend fun downloadAndInstall(release: ReleaseInfo) = withContext(Dispatchers.IO) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _download.value = DownloadState.Failed("Autorisez l'installation d'applis inconnues pour Pilote")
            return@withContext
        }
        runCatching {
            _download.value = DownloadState.Downloading(0f)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "pilote-${release.version}.apk")
            download(release.apkUrl, file, release.sizeBytes)
            _download.value = DownloadState.Installing
            install(file)
        }.onFailure { _download.value = DownloadState.Failed(it.message ?: "Erreur inconnue") }
    }

    private fun download(url: String, target: File, expected: Long) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Pilote/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/octet-stream")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            check(conn.responseCode == 200) { "Téléchargement impossible (${conn.responseCode})" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expected
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) _download.value = DownloadState.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("pilote.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            PackageInstaller.STATUS_SUCCESS -> _download.value = DownloadState.Idle
            else -> _download.value = DownloadState.Failed(message ?: "Installation refusée ($status)")
        }
    }

    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.karelisio.pilote.INSTALL_RESULT"

        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        PiloteApp.graph(context).updates.onInstallResult(status, message)
    }
}
