package com.karelisio.pilote.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.actions.AdvancedController
import com.karelisio.pilote.actions.title
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.MorphingShapeBox
import com.karelisio.pilote.ui.components.SectionHeader
import com.karelisio.pilote.ui.components.cascade

@Composable
fun AdvancedScreen(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val shizuku by graph.shizuku.state.collectAsStateWithLifecycle()
    val secure = graph.executor.advanced.hasSecureSettings
    val color by animateColorAsState(
        if (shizuku.ready) MaterialTheme.colorScheme.primary else if (shizuku.running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
        label = "state",
    )
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Accès avancé") },
                subtitle = { Text("Shizuku · Shevery · ADB") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = { graph.shizuku.refresh() }) { Icon(Icons.Rounded.Refresh, "Actualiser") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.cascade(0)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        MorphingShapeBox(
                            if (shizuku.ready) MaterialShapes.Sunny else if (shizuku.running) MaterialShapes.Cookie6Sided else MaterialShapes.Circle,
                            color, size = 88.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                shizuku.ready -> "Shizuku actif"
                                shizuku.running && shizuku.version < 11 -> "Version de Shizuku trop ancienne"
                                shizuku.running -> "Autorisation requise"
                                else -> "Aucun service Shizuku détecté"
                            },
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                        )
                        Text(
                            if (shizuku.running) "API ${shizuku.version} · mode ${shizuku.modeLabel}" else "Démarrez Shizuku, Shevery ou un fork compatible",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (shizuku.running && !shizuku.permissionGranted) {
                            Button(onClick = { graph.shizuku.requestPermission() }) { Text("Autoriser Pilote") }
                        }
                        Text(
                            "Détection par le binder uniquement (pingBinder) : fonctionne avec Shizuku officiel, Shevery et les forks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item { SectionHeader("Sans Shizuku : WRITE_SECURE_SETTINGS") }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.cascade(1)) {
                    Column(Modifier.padding(16.dp)) {
                        StatusLine(secure, if (secure) "Accordée : le DNS privé est appliqué directement" else "Non accordée")
                        Spacer(Modifier.height(8.dp))
                        Text("Depuis un ordinateur avec ADB :", style = MaterialTheme.typography.bodySmall)
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                AdvancedController.ADB_GRANT_COMMAND,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        OutlinedButton(onClick = {
                            context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("adb", AdvancedController.ADB_GRANT_COMMAND))
                            Toast.makeText(context, "Commande copiée", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.ContentCopy, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Copier la commande")
                        }
                    }
                }
            }
            item { SectionHeader("Actions avancées") }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.cascade(2)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(Action.Wifi(), Action.Bluetooth(), Action.MobileData(), Action.DarkMode(), Action.PrivateDns()).forEach { a ->
                            val availability = graph.executor.availability(a)
                            Column(Modifier.alpha(if (availability.available) 1f else 0.6f)) {
                                StatusLine(availability.available, a.title())
                                availability.reason?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 34.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
