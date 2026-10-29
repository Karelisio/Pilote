package com.karelisio.pilote.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.BuildConfig
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.SectionHeader
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.components.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onAdvanced: () -> Unit,
    onHonor: () -> Unit,
    onOnboarding: () -> Unit,
) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val paused by graph.settings.paused.collectAsStateWithLifecycle(initialValue = false)
    val dynamic by graph.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val rushio by graph.settings.rushioPackage.collectAsStateWithLifecycle(initialValue = "")
    val shizuku by graph.shizuku.state.collectAsStateWithLifecycle()
    var editRushio by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = graph.profiles.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Profils exportés" else "Export impossible", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                graph.profiles.importJson(json ?: error("Fichier vide"))
            }
            Toast.makeText(
                context,
                result.fold({ "$it profil(s) importé(s)" }, { "Import impossible : ${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionHeader("Général") }
            item {
                Group(Modifier.cascade(0)) {
                    SettingRow(Icons.Rounded.Pause, "Pause globale", "Suspend tous les profils et restaure les réglages",
                        trailing = { Switch(paused, { haptics.toggle(it); scope.launch { graph.engine.setPaused(it) } }) })
                    SettingRow(Icons.Rounded.Palette, "Couleurs dynamiques", "Material You (sinon thème indigo)",
                        trailing = { Switch(dynamic, { haptics.toggle(it); scope.launch { graph.settings.setDynamicColor(it) } }) })
                }
            }
            item { SectionHeader("Accès et fiabilité") }
            item {
                Group(Modifier.cascade(1)) {
                    SettingRow(Icons.Rounded.Security, "Autorisations", "État de chaque autorisation", onClick = onPermissions)
                    SettingRow(
                        Icons.Rounded.AdminPanelSettings, "Accès avancé",
                        if (shizuku.ready) "Shizuku/Shevery actif (${shizuku.modeLabel})" else "Shizuku, Shevery, WRITE_SECURE_SETTINGS",
                        onClick = onAdvanced,
                    )
                    SettingRow(Icons.Rounded.BatterySaver, "Batterie et Honor", "Empêcher MagicOS de fermer Pilote", onClick = onHonor)
                }
            }
            item { SectionHeader("Intégrations") }
            item {
                Group(Modifier.cascade(2)) {
                    SettingRow(Icons.AutoMirrored.Rounded.OpenInNew, "Rushio", "Paquet : $rushio", onClick = { editRushio = true })
                }
            }
            item { SectionHeader("Profils") }
            item {
                Group(Modifier.cascade(3)) {
                    SettingRow(Icons.Rounded.Upload, "Exporter en JSON", "Sauvegarde de tous les profils", onClick = { exportLauncher.launch("pilote-profils.json") })
                    SettingRow(Icons.Rounded.Download, "Importer un JSON", "Ajoute les profils du fichier", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                }
            }
            item { SectionHeader("Application") }
            item {
                Group(Modifier.cascade(4)) {
                    SettingRow(
                        Icons.Rounded.SystemUpdate, "Rechercher une mise à jour",
                        if (checking) "Recherche…" else "Version ${BuildConfig.VERSION_NAME}",
                        onClick = {
                            checking = true
                            scope.launch {
                                val result = graph.updates.check(force = true)
                                checking = false
                                val msg = result.fold(
                                    { if (it == null) "Pilote est à jour" else "Version ${it.version} disponible" },
                                    { "Vérification impossible : ${it.message}" },
                                )
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    SettingRow(Icons.Rounded.WavingHand, "Revoir la présentation", null, onClick = onOnboarding)
                    SettingRow(Icons.Rounded.Info, "À propos", "Pilote ${BuildConfig.VERSION_NAME} · 100 % local, sans compte")
                }
            }
        }
    }

    if (editRushio) {
        var value by remember { mutableStateOf(rushio) }
        AlertDialog(
            onDismissRequest = { editRushio = false },
            title = { Text("Paquet de Rushio") },
            text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("ex. com.karelisio.rushio") }) },
            confirmButton = { TextButton(onClick = { editRushio = false; scope.launch { graph.settings.setRushioPackage(value) } }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { editRushio = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}
