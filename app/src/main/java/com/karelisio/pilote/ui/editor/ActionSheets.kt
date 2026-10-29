package com.karelisio.pilote.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.actions.describe
import com.karelisio.pilote.actions.formatScale
import com.karelisio.pilote.actions.formatSeconds
import com.karelisio.pilote.actions.label
import com.karelisio.pilote.actions.title
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.AudioStream
import com.karelisio.pilote.model.DndMode
import com.karelisio.pilote.model.PrivateDnsMode
import com.karelisio.pilote.model.RingerMode
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.SectionHeader
import com.karelisio.pilote.ui.components.rememberHaptics

@Composable
fun AddActionSheet(onDismiss: () -> Unit, onPick: (Action) -> Unit) {
    val graph = LocalGraph.current
    val rushio by graph.settings.rushioPackage.collectAsStateWithLifecycle(initialValue = "")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Ajouter une action", style = MaterialTheme.typography.titleLargeEmphasized)
            ActionGroups.forEach { group ->
                SectionHeader(group.title)
                group.actions.forEach { proto ->
                    val availability = graph.executor.availability(proto)
                    ListItem(
                        headlineContent = { Text(proto.title()) },
                        supportingContent = availability.reason?.let { { Text(it) } },
                        leadingContent = { Icon(proto.icon(), null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .alpha(if (availability.available) 1f else 0.5f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPick(proto) },
                    )
                }
                if (group.title == "Applis" && rushio.isNotBlank() && graph.launcher.isInstalled(rushio)) {
                    ListItem(
                        headlineContent = { Text("Ouvrir Rushio") },
                        supportingContent = { Text(rushio) },
                        leadingContent = { AppIcon(rushio) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onPick(Action.LaunchApp(rushio, "Rushio")) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActionEditSheet(
    initial: Action,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Action) -> Unit,
    onDelete: () -> Unit,
) {
    val graph = LocalGraph.current
    val haptics = rememberHaptics()
    var action by remember { mutableStateOf(initial) }
    val availability = graph.executor.availability(action)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(action.icon(), null)
                Spacer(Modifier.width(12.dp))
                Text(action.title(), style = MaterialTheme.typography.titleLargeEmphasized)
            }
            if (!availability.available) {
                Text(
                    availability.reason.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            when (val a = action) {
                is Action.Ringer -> Segmented(RingerMode.entries, a.mode, { it.label() }) { action = a.copy(mode = it) }
                is Action.Volume -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioStream.entries.forEach { s ->
                            FilterChip(selected = a.stream == s, onClick = { action = a.copy(stream = s) }, label = { Text(s.label()) })
                        }
                    }
                    LabeledSlider("Volume", "${a.percent} %", null, a.percent.toFloat(), 0f..100f, 19, { action = a.copy(percent = it.toInt(), rawIndex = null) }, { haptics.tick() })
                }
                is Action.Dnd -> Segmented(DndMode.entries, a.mode, { it.label().replaceFirstChar(Char::uppercase) }) { action = a.copy(mode = it) }
                is Action.Brightness -> {
                    SwitchRow("Luminosité automatique", null, a.auto) { action = a.copy(auto = it, rawValue = null) }
                    if (!a.auto) {
                        LabeledSlider("Niveau", "${a.percent} %", null, a.percent.toFloat(), 1f..100f, 0, { action = a.copy(percent = it.toInt(), rawValue = null) })
                    }
                }
                is Action.ScreenTimeout -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60, 120, 300, 600, 1800).forEach { s ->
                        FilterChip(selected = a.seconds == s, onClick = { action = a.copy(seconds = s) }, label = { Text(formatSeconds(s)) })
                    }
                }
                is Action.Rotation -> SwitchRow("Rotation automatique", "Désactivé = orientation verrouillée", a.auto) { action = a.copy(auto = it) }
                is Action.Wifi -> SwitchRow("Activer le Wi-Fi", null, a.enabled) { action = a.copy(enabled = it) }
                is Action.Bluetooth -> SwitchRow("Activer le Bluetooth", null, a.enabled) { action = a.copy(enabled = it) }
                is Action.MobileData -> SwitchRow("Activer les données mobiles", null, a.enabled) { action = a.copy(enabled = it) }
                is Action.DarkMode -> SwitchRow("Activer le mode sombre", null, a.enabled) { action = a.copy(enabled = it) }
                is Action.PrivateDns -> {
                    Segmented(PrivateDnsMode.entries, a.mode, { it.label().replaceFirstChar(Char::uppercase) }) { action = a.copy(mode = it) }
                    if (a.mode == PrivateDnsMode.HOSTNAME) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = a.host, onValueChange = { action = a.copy(host = it) },
                            label = { Text("Fournisseur (ex. dns.adguard-dns.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is Action.LaunchApp -> AppPicker(a.packageName) { app -> action = a.copy(packageName = app.packageName, label = app.label) }
                Action.MediaPlay -> Text("Envoie « Lecture » à la dernière appli média utilisée.", style = MaterialTheme.typography.bodyMedium)
                is Action.Notify -> {
                    OutlinedTextField(a.title, { action = a.copy(title = it) }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(a.text, { action = a.copy(text = it) }, label = { Text("Texte") }, modifier = Modifier.fillMaxWidth())
                }
                is Action.AnimationScale -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0f, 0.5f, 1f, 1.5f, 2f).forEach { v ->
                        FilterChip(
                            selected = a.scale == v, onClick = { action = a.copy(scale = v) },
                            label = { Text(if (v == 0f) "Désactivées" else "×" + formatScale(v)) },
                        )
                    }
                }
                is Action.RefreshRate -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 60, 90, 120, 144).forEach { v ->
                        FilterChip(
                            selected = a.hz == v, onClick = { action = Action.RefreshRate(hz = v) },
                            label = { Text(if (v == 0) "Auto" else "$v Hz") },
                        )
                    }
                }
                is Action.Caffeine -> Text(
                    "L'écran reste allumé tant que le profil est actif, puis se remet en veille normalement.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Résumé : " + action.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) OutlinedButton(onClick = onDelete) { Text("Supprimer") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { haptics.confirm(); onSave(action) }, enabled = action.isComplete()) { Text(if (isNew) "Ajouter" else "Valider") }
            }
        }
    }
}

private fun Action.isComplete(): Boolean = when (this) {
    is Action.LaunchApp -> packageName.isNotBlank()
    is Action.PrivateDns -> mode != PrivateDnsMode.HOSTNAME || host.isNotBlank()
    else -> true
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val haptics = rememberHaptics()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { haptics.tick(); onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(o), maxLines = 1) }
        }
    }
}
