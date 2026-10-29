package com.karelisio.pilote.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.karelisio.pilote.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

data class ShizukuState(
    /** A Shizuku-compatible service (Shizuku, Shevery, fork…) answered pingBinder(). */
    val running: Boolean = false,
    val permissionGranted: Boolean = false,
    val version: Int = -1,
    /** 0 = root, 2000 = shell (adb). */
    val uid: Int = -1,
) {
    val ready: Boolean get() = running && permissionGranted && version >= 11
    val modeLabel: String get() = when (uid) { 0 -> "root"; 2000 -> "adb (shell)"; -1 -> "—"; else -> "uid $uid" }
}

/**
 * Shizuku access, detected exclusively through the binder (pingBinder + binder listeners),
 * never through a hard-coded package name, so Shevery and Shizuku forks work out of the box.
 */
class ShizukuManager(private val context: Context) {
    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val bindMutex = Mutex()
    private var service: IShellService? = null

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private var connectionWaiter: CompletableDeferred<IShellService>? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val s = IShellService.Stub.asInterface(binder)
                service = s
                connectionWaiter?.complete(s)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { service = null; refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            _state.value = ShizukuState()
            return
        }
        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(true)
        val granted = !preV11 && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        _state.value = ShizukuState(
            running = true,
            permissionGranted = granted,
            version = runCatching { Shizuku.getVersion() }.getOrDefault(-1),
            uid = runCatching { Shizuku.getUid() }.getOrDefault(-1),
        )
    }

    /** Returns false if the request could not be made (service not running / too old). */
    fun requestPermission(): Boolean {
        refresh()
        val s = _state.value
        if (!s.running || s.version < 11) return false
        if (s.permissionGranted) return true
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }.onFailure { return false }
        return true
    }

    /** Runs a shell command through the Shizuku user service. */
    suspend fun exec(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            refresh()
            check(_state.value.ready) { "Shizuku/Shevery indisponible ou non autorisé" }
            val shell = obtainService()
            val raw = shell.exec(command)
            val code = raw.substringBefore('\n').toIntOrNull() ?: -1
            val out = raw.substringAfter('\n').trim()
            check(code == 0) { "« $command » a échoué ($code) : $out" }
            out
        }
    }

    private suspend fun obtainService(): IShellService = bindMutex.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        val waiter = CompletableDeferred<IShellService>()
        connectionWaiter = waiter
        withContext(Dispatchers.Main) { Shizuku.bindUserService(serviceArgs, connection) }
        withTimeout(10_000) { waiter.await() }
    }

    companion object {
        const val REQUEST_CODE = 4242
    }
}
