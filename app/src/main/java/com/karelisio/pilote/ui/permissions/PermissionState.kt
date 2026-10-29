package com.karelisio.pilote.ui.permissions

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.util.Vendor

@Stable
class PermissionUiState(val spec: PermissionSpec, granted: Boolean, private val onRequest: () -> Unit) {
    var granted by mutableStateOf(granted)
    fun request() = onRequest()
}

/** Tracks a permission and re-checks it every time the app comes back to the foreground. */
@Composable
fun rememberPermissionState(spec: PermissionSpec, onChanged: (Boolean) -> Unit = {}): PermissionUiState {
    val context = LocalContext.current
    val graph = LocalGraph.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    lateinit var state: PermissionUiState

    fun recheck() {
        val now = spec.isGranted(context, graph)
        if (now != state.granted) {
            state.granted = now
            onChanged(now)
            if (now) {
                graph.registry.restartMonitors()
                graph.registry.reRegisterAll()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        recheck()
        if (!state.granted) Vendor.openAppDetails(context)
    }

    state = remember(spec.id) {
        PermissionUiState(spec, spec.isGranted(context, graph)) {
            when {
                spec.id == PermissionId.SHIZUKU -> {
                    if (!graph.shizuku.requestPermission()) {
                        Toast.makeText(context, "Démarrez Shizuku, Shevery ou un fork, puis réessayez", Toast.LENGTH_LONG).show()
                    }
                }
                spec.runtime.isNotEmpty() -> launcher.launch(spec.runtime.toTypedArray())
                spec.settingsIntent != null -> {
                    val intent = spec.settingsIntent.invoke(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (runCatching { context.startActivity(intent) }.isFailure) Vendor.openAppDetails(context)
                }
            }
        }
    }

    DisposableEffect(lifecycle, spec.id) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) recheck() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (spec.id == PermissionId.SHIZUKU) {
        LaunchedEffect(Unit) { graph.shizuku.state.collect { recheck() } }
    }
    return state
}
