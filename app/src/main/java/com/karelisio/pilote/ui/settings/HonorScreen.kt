package com.karelisio.pilote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.permissions.PermissionId
import com.karelisio.pilote.ui.permissions.PermissionSpecs
import com.karelisio.pilote.ui.permissions.rememberPermissionState
import com.karelisio.pilote.util.Vendor
import kotlinx.coroutines.launch

@Composable
fun HonorScreen(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmed by graph.settings.honorConfirmed.collectAsStateWithLifecycle(initialValue = false)
    val battery = rememberPermissionState(PermissionSpecs.first { it.id == PermissionId.BATTERY })
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Batterie et Honor") },
                subtitle = { Text(if (Vendor.isHonorOrHuawei) "Appareil Honor détecté" else "Recommandé sur tous les téléphones") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Step(1, "Pas d'optimisation de batterie", "Obligatoire : sinon Android met Pilote en veille et les déclencheurs se perdent.", battery.granted, Modifier.cascade(0)) {
                    Button(onClick = { battery.request() }) { Text("Exclure Pilote") }
                }
            }
            item {
                Step(
                    2, "Lancement d'applis (gestion manuelle)",
                    "Dans Lancement d'applis, trouvez Pilote, désactivez « Gérer automatiquement » puis activez les trois interrupteurs : lancement auto, lancement secondaire et exécution en arrière-plan.",
                    confirmed, Modifier.cascade(1),
                ) {
                    Column {
                        Button(onClick = { Vendor.openAppLaunch(context) }) { Text("Ouvrir Lancement d'applis") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = confirmed, onCheckedChange = { v -> scope.launch { graph.settings.setHonorConfirmed(v) } })
                            Text("C'est fait")
                        }
                    }
                }
            }
            item {
                Step(
                    3, "Verrouiller Pilote dans les applis récentes",
                    "Ouvrez les applis récentes, faites glisser Pilote vers le bas et touchez le cadenas : MagicOS ne la fermera plus lors du nettoyage.",
                    null, Modifier.cascade(2),
                ) {}
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    text: String,
    done: Boolean?,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("$number", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (done == true) Icon(Icons.Rounded.CheckCircle, "Fait", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}
