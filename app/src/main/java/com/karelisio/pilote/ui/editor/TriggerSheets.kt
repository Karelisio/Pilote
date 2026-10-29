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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.engine.Evaluator
import com.karelisio.pilote.model.BtKind
import com.karelisio.pilote.model.Trigger
import com.karelisio.pilote.triggers.BluetoothMonitor
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.components.rememberHaptics

@Composable
fun AddTriggerSheet(onDismiss: () -> Unit, onPick: (Trigger) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Ajouter un déclencheur", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(bottom = 8.dp))
            TriggerPrototypes.forEachIndexed { i, proto ->
                ListItem(
                    headlineContent = { Text(proto.typeLabel()) },
                    supportingContent = { Text(triggerHelp(proto)) },
                    leadingContent = { Icon(proto.icon(), null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onPick(proto) }.cascade(i),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun triggerHelp(t: Trigger) = when (t) {
    is Trigger.Location -> "Entrée dans une zone choisie sur la carte"
    is Trigger.Time -> "Plage horaire, jours de la semaine"
    is Trigger.Bluetooth -> "Voiture, écouteurs ou appareil précis connecté"
    is Trigger.WiredHeadset -> "Casque branché (jack ou USB-C)"
    is Trigger.Wifi -> "Connecté à un réseau précis"
    is Trigger.Charging -> "Chargeur branché (ou débranché si inversé)"
    is Trigger.AppOpen -> "Une appli est au premier plan"
    is Trigger.Manual -> "Bouton dans l'appli, tuile et widget"
}

@Composable
fun TriggerEditSheet(
    initial: Trigger,
    profileId: Long,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Trigger) -> Unit,
    onDelete: () -> Unit,
) {
    var trigger by remember { mutableStateOf(initial) }
    val haptics = rememberHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = trigger !is Trigger.Location,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(trigger.icon(), null)
                Spacer(Modifier.width(12.dp))
                Text(trigger.typeLabel(), style = MaterialTheme.typography.titleLargeEmphasized)
            }
            Spacer(Modifier.height(12.dp))
            when (val t = trigger) {
                is Trigger.Location -> LocationEditor(t) { trigger = it }
                is Trigger.Time -> TimeEditor(t) { trigger = it }
                is Trigger.Bluetooth -> BluetoothEditor(t) { trigger = it }
                is Trigger.Wifi -> WifiEditor(t) { trigger = it }
                is Trigger.AppOpen -> AppPicker(t.packageName) { app -> trigger = t.copy(packageName = app.packageName, label = app.label) }
                is Trigger.WiredHeadset -> Help("Actif tant qu'un casque filaire (jack ou USB-C) est branché.")
                is Trigger.Charging -> Help("Actif tant que le téléphone est en charge. Inversez pour « chargeur débranché ».")
                is Trigger.Manual -> Help("Activez ce profil à la main : raccourci de l'accueil, tuile des réglages rapides ou widget.")
            }
            Spacer(Modifier.height(8.dp))
            if (trigger !is Trigger.Manual) {
                SwitchRow(
                    "Inverser",
                    "Rempli quand la condition n'est PAS vraie (ex. Bluetooth déconnecté)",
                    trigger.negate,
                ) { v -> trigger = trigger.withNegate(v) }
            }
            Text(
                "Résumé : " + Evaluator.describe(trigger),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) OutlinedButton(onClick = onDelete) { Text("Supprimer") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { haptics.confirm(); onSave(trigger) }) { Text(if (isNew) "Ajouter" else "Valider") }
            }
        }
    }
}

private fun Trigger.withNegate(v: Boolean): Trigger = when (this) {
    is Trigger.Location -> copy(negate = v)
    is Trigger.Time -> copy(negate = v)
    is Trigger.Bluetooth -> copy(negate = v)
    is Trigger.WiredHeadset -> copy(negate = v)
    is Trigger.Wifi -> copy(negate = v)
    is Trigger.Charging -> copy(negate = v)
    is Trigger.AppOpen -> copy(negate = v)
    is Trigger.Manual -> copy(negate = v)
}

@Composable
private fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LocationEditor(t: Trigger.Location, onChange: (Trigger.Location) -> Unit) {
    Help("Touchez la carte pour placer la zone, ou utilisez votre position.")
    Spacer(Modifier.height(8.dp))
    MapPicker(
        latitude = t.latitude, longitude = t.longitude, radiusMeters = t.radiusMeters,
        onPick = { lat, lng -> onChange(t.copy(latitude = lat, longitude = lng)) },
        modifier = Modifier.fillMaxWidth().height(320.dp).clip(MaterialTheme.shapes.large),
    )
    Spacer(Modifier.height(12.dp))
    LabeledSlider(
        label = "Rayon", value = "${t.radiusMeters.toInt()} m", hint = "100 m minimum conseillé pour la fiabilité",
        current = t.radiusMeters, range = 80f..1000f, steps = 0,
        onChange = { onChange(t.copy(radiusMeters = (it / 10f).toInt() * 10f)) },
    )
    OutlinedTextField(
        value = t.label, onValueChange = { onChange(t.copy(label = it)) },
        label = { Text("Nom du lieu") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimeEditor(t: Trigger.Time, onChange: (Trigger.Time) -> Unit) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeButton("Début", t.startMinute, Modifier.weight(1f)) { editing = true }
        TimeButton("Fin", t.endMinute, Modifier.weight(1f)) { editing = false }
    }
    Spacer(Modifier.height(12.dp))
    Text("Jours", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, label ->
            val day = i + 1
            FilterChip(
                selected = day in t.days,
                onClick = {
                    val next = if (day in t.days) t.days - day else t.days + day
                    if (next.isNotEmpty()) onChange(t.copy(days = next))
                },
                label = { Text(label) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SuggestionChip(onClick = { onChange(t.copy(days = (1..7).toSet())) }, label = { Text("Tous les jours") })
        SuggestionChip(onClick = { onChange(t.copy(days = (1..5).toSet())) }, label = { Text("Semaine") })
        SuggestionChip(onClick = { onChange(t.copy(days = setOf(6, 7))) }, label = { Text("Week-end") })
    }
    if (t.startMinute > t.endMinute) Help("La plage passe minuit : elle se termine le lendemain.")

    editing?.let { start ->
        val initial = if (start) t.startMinute else t.endMinute
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (start) "Heure de début" else "Heure de fin") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    onChange(if (start) t.copy(startMinute = m) else t.copy(endMinute = m))
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(Evaluator.formatMinute(minute), style = MaterialTheme.typography.headlineMediumEmphasized)
        }
    }
}

@Composable
private fun BluetoothEditor(t: Trigger.Bluetooth, onChange: (Trigger.Bluetooth) -> Unit) {
    val context = LocalContext.current
    val devices = remember { BluetoothMonitor.bonded(context) }
    val kinds = listOf(
        BtKind.ANY_CAR to "N'importe quelle voiture",
        BtKind.ANY_HEADPHONES to "N'importe quels écouteurs",
        BtKind.ANY to "N'importe quel appareil",
        BtKind.SPECIFIC to "Un appareil précis",
    )
    kinds.forEach { (kind, label) ->
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange(t.copy(kind = kind)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = t.kind == kind, onClick = { onChange(t.copy(kind = kind)) })
            Text(label)
        }
    }
    if (t.kind == BtKind.SPECIFIC) {
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Help("Aucun appareil appairé visible. Autorisez « Appareils à proximité » et appairez l'appareil dans les réglages Bluetooth.")
        }
        devices.forEach { d ->
            ListItem(
                headlineContent = { Text(d.name) },
                supportingContent = { Text(d.address) },
                leadingContent = { RadioButton(selected = d.address == t.address, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { onChange(t.copy(address = d.address, name = d.name)) },
            )
        }
    }
}

@Composable
private fun WifiEditor(t: Trigger.Wifi, onChange: (Trigger.Wifi) -> Unit) {
    val facts by LocalGraph.current.facts.state.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = t.ssid, onValueChange = { onChange(t.copy(ssid = it)) },
        label = { Text("Nom du réseau (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    val current = facts.wifiSsid
    if (current != null) {
        SuggestionChip(onClick = { onChange(t.copy(ssid = current)) }, label = { Text("Réseau actuel : $current") })
    } else {
        Help("Connectez-vous au réseau pour le sélectionner (la localisation est nécessaire pour lire son nom).")
    }
}
