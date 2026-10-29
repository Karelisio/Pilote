package com.karelisio.pilote.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.permissions.PermissionId
import com.karelisio.pilote.ui.permissions.PermissionSpecs
import com.karelisio.pilote.ui.permissions.rememberPermissionState

@Composable
fun PermissionsScreen(onBack: () -> Unit, onAdvanced: () -> Unit, onHonor: () -> Unit) {
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Autorisations") },
                subtitle = { Text("Vérifiées à chaque retour dans l'appli") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(PermissionSpecs, key = { _, s -> s.id }) { i, spec ->
                val state = rememberPermissionState(spec)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (state.granted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).cascade(i),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(spec.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(spec.title, style = MaterialTheme.typography.titleSmall)
                                if (spec.optional) Text("Optionnelle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AnimatedContent(
                                state.granted,
                                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                                label = "granted",
                            ) { granted ->
                                if (granted) {
                                    Icon(Icons.Rounded.CheckCircle, "Accordée", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                } else {
                                    FilledTonalButton(onClick = { state.request() }) { Text("Autoriser") }
                                }
                            }
                        }
                        Text(
                            spec.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                        )
                        when (spec.id) {
                            PermissionId.SHIZUKU -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                TextButton(onClick = onAdvanced) { Text("Accès avancé") }
                            }
                            PermissionId.BATTERY -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                TextButton(onClick = onHonor) { Text("Lancement d'applis Honor") }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
