package com.karelisio.pilote.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karelisio.pilote.actions.describe
import com.karelisio.pilote.actions.title
import com.karelisio.pilote.engine.Evaluator
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.Logic
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.model.ProfileShape
import com.karelisio.pilote.model.Trigger
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.FlowAnchors
import com.karelisio.pilote.ui.components.ForgetPort
import com.karelisio.pilote.ui.components.MorphingShapeBox
import com.karelisio.pilote.ui.components.PortKind
import com.karelisio.pilote.ui.components.SectionHeader
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.components.flowConnectors
import com.karelisio.pilote.ui.components.flowPort
import com.karelisio.pilote.ui.components.rememberHaptics
import com.karelisio.pilote.ui.navigation.sharedBoundsIfAvailable
import com.karelisio.pilote.ui.theme.ProfilePalette
import com.karelisio.pilote.ui.theme.accentContainer
import com.karelisio.pilote.ui.theme.polygon
import com.karelisio.pilote.ui.theme.profileAccent
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch

private sealed interface Sheet {
    data class AddTrigger(val condition: Boolean) : Sheet
    data class EditTrigger(val condition: Boolean, val index: Int, val trigger: Trigger) : Sheet
    data object AddAction : Sheet
    data class EditAction(val index: Int, val action: Action) : Sheet
}

private val Gutter = 36.dp

@Composable
fun EditorScreen(profileId: Long, template: String?, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: EditorViewModel = viewModel(key = "editor-$profileId-$template") { EditorViewModel(graph, profileId, template) }
    val draft = vm.draft
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val facts by graph.facts.state.collectAsStateWithLifecycle()
    val tests by graph.engine.tests.collectAsStateWithLifecycle()
    val active by graph.engine.active.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(enabled = vm.dirty) { confirmExit = true }

    val key = if (profileId > 0) "profile-$profileId" else "new-profile"
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).sharedBoundsIfAvailable(key),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(draft?.name?.ifBlank { "Sans nom" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(if (vm.isNew) "Nouveau profil" else "Modifier le profil") },
                navigationIcon = {
                    IconButton(onClick = { if (vm.dirty) confirmExit = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour")
                    }
                },
                actions = {
                    if (draft != null && draft.id > 0) {
                        val testing = draft.id in tests
                        IconButton(onClick = {
                            haptics.confirm()
                            scope.launch {
                                vm.save()
                                if (testing) graph.engine.stopTest(draft.id) else graph.engine.startTest(draft.id)
                            }
                        }) { Icon(if (testing) Icons.Rounded.Stop else Icons.Rounded.Science, if (testing) "Arrêter le test" else "Simuler 30 s") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Supprimer") }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptics.confirm()
                    scope.launch { vm.save(); onBack() }
                },
                icon = { Icon(Icons.Rounded.Check, null) },
                text = { Text("Enregistrer") },
            )
        },
    ) { padding ->
        if (draft == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        val eval = remember(draft, facts) { Evaluator.evaluate(draft, facts) }
        val accent = profileAccent(draft)
        val anchors = remember { FlowAnchors() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp))
                .padding(horizontal = 16.dp),
        ) {
            IdentitySection(draft, accent, onChange = vm::update, modifier = Modifier.cascade(0))

            Column(
                Modifier.flowConnectors(anchors, Gutter, litColor = accent, idleColor = MaterialTheme.colorScheme.outlineVariant)
            ) {
                // Triggers
                LogicHeader(
                    title = "Déclencheurs",
                    subtitle = "Quand…",
                    logic = draft.triggerLogic,
                    show = draft.triggers.size > 1,
                    onLogic = { l -> vm.update { it.copy(triggerLogic = l) } },
                )
                draft.triggers.forEachIndexed { i, t ->
                    val matched = eval.triggers.getOrNull(i)?.matched == true
                    ForgetPort(anchors, "t-${t.id}")
                    TriggerCard(
                        trigger = t, matched = matched,
                        onClick = { sheet = Sheet.EditTrigger(false, i, t) },
                        modifier = Modifier.padding(start = Gutter, top = 4.dp, bottom = 4.dp)
                            .flowPort(anchors, "t-${t.id}", PortKind.INPUT, matched)
                            .cascade(i + 1, t.id),
                    )
                }
                AddButton("Ajouter un déclencheur") { sheet = Sheet.AddTrigger(false) }

                val triggersOk = eval.triggers.isEmpty() || if (draft.triggerLogic == Logic.ALL) eval.triggers.all { it.matched } else eval.triggers.any { it.matched }
                HubRow(
                    label = if (draft.triggers.size > 1) (if (draft.triggerLogic == Logic.ALL) "ET" else "OU") else "→",
                    lit = triggersOk && draft.triggers.isNotEmpty(),
                    accent = accent,
                    modifier = Modifier.flowPort(anchors, "hub-1", PortKind.HUB, triggersOk && draft.triggers.isNotEmpty()),
                )

                // Conditions
                LogicHeader(
                    title = "Conditions",
                    subtitle = "…et seulement si (optionnel)",
                    logic = draft.conditionLogic,
                    show = draft.conditions.size > 1,
                    onLogic = { l -> vm.update { it.copy(conditionLogic = l) } },
                )
                draft.conditions.forEachIndexed { i, t ->
                    val matched = eval.conditions.getOrNull(i)?.matched == true
                    ForgetPort(anchors, "c-${t.id}")
                    TriggerCard(
                        trigger = t, matched = matched,
                        onClick = { sheet = Sheet.EditTrigger(true, i, t) },
                        modifier = Modifier.padding(start = Gutter, top = 4.dp, bottom = 4.dp)
                            .flowPort(anchors, "c-${t.id}", PortKind.INPUT, matched)
                            .cascade(i + 1, t.id),
                    )
                }
                AddButton("Ajouter une condition") { sheet = Sheet.AddTrigger(true) }

                HubRow(
                    label = if (eval.active) "Actif" else "Alors",
                    lit = eval.active,
                    accent = accent,
                    modifier = Modifier.flowPort(anchors, "hub-2", PortKind.HUB, eval.active),
                )

                // Actions
                SectionHeader("Actions", modifier = Modifier.padding(start = Gutter), subtitle = "…Pilote applique")
                draft.actions.forEachIndexed { i, a ->
                    val availability = graph.executor.availability(a)
                    ForgetPort(anchors, "a-$i")
                    ActionCard(
                        action = a,
                        available = availability.available,
                        reason = availability.reason,
                        onClick = { sheet = Sheet.EditAction(i, a) },
                        modifier = Modifier.padding(start = Gutter, top = 4.dp, bottom = 4.dp)
                            .flowPort(anchors, "a-$i", PortKind.OUTPUT, availability.available)
                            .cascade(i + 1, "a-$i-${a::class.simpleName}"),
                    )
                }
                AddButton("Ajouter une action") { sheet = Sheet.AddAction }
            }

            OptionsSection(draft, onChange = vm::update, isActive = active.any { it.id == draft.id })
        }
    }

    when (val s = sheet) {
        is Sheet.AddTrigger -> AddTriggerSheet(
            onDismiss = { sheet = null },
            onPick = { proto -> sheet = Sheet.EditTrigger(s.condition, -1, proto) },
        )
        is Sheet.EditTrigger -> TriggerEditSheet(
            initial = s.trigger,
            profileId = draft?.id ?: 0,
            isNew = s.index < 0,
            onDismiss = { sheet = null },
            onSave = { t ->
                vm.update { p ->
                    val list = if (s.condition) p.conditions else p.triggers
                    val next = if (s.index < 0) list + t else list.toMutableList().also { it[s.index] = t }
                    if (s.condition) p.copy(conditions = next) else p.copy(triggers = next)
                }
                sheet = null
            },
            onDelete = {
                vm.update { p ->
                    if (s.condition) p.copy(conditions = p.conditions.filterIndexed { i, _ -> i != s.index })
                    else p.copy(triggers = p.triggers.filterIndexed { i, _ -> i != s.index })
                }
                sheet = null
            },
        )
        Sheet.AddAction -> AddActionSheet(
            onDismiss = { sheet = null },
            onPick = { proto -> sheet = Sheet.EditAction(-1, proto) },
        )
        is Sheet.EditAction -> ActionEditSheet(
            initial = s.action,
            isNew = s.index < 0,
            onDismiss = { sheet = null },
            onSave = { a ->
                vm.update { p ->
                    val next = if (s.index < 0) p.actions + a else p.actions.toMutableList().also { it[s.index] = a }
                    p.copy(actions = next)
                }
                sheet = null
            },
            onDelete = {
                vm.update { p -> p.copy(actions = p.actions.filterIndexed { i, _ -> i != s.index }) }
                sheet = null
            },
        )
        null -> Unit
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Modifications non enregistrées") },
            text = { Text("Enregistrer avant de quitter ?") },
            confirmButton = { TextButton(onClick = { confirmExit = false; scope.launch { vm.save(); onBack() } }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Abandonner") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce profil ?") },
            text = { Text("Les réglages qu'il contrôle seront restaurés.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun IdentitySection(profile: Profile, accent: Color, onChange: ((Profile) -> Profile) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val dark = isSystemInDarkTheme()
    Column(modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingShapeBox(profile.shape.polygon(), accent, size = 64.dp) {
                Text(
                    profile.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.surface,
                )
            }
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = profile.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = { Text("Nom du profil") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(ProfileShape.entries) { _, shape ->
                val selected = shape == profile.shape
                val size by animateDpAsState(if (selected) 44.dp else 36.dp, label = "shape")
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable { haptics.tick(); onChange { it.copy(shape = shape) } },
                    contentAlignment = Alignment.Center,
                ) {
                    MorphingShapeBox(
                        shape.polygon(),
                        if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                        size = size,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfilePalette.forEach { (light, darkColor) ->
                val c = Color(if (dark) darkColor else light)
                val selected = profile.color == light
                val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "ring")
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(2.dp, ring, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickable { haptics.tick(); onChange { it.copy(color = light) } },
                )
            }
        }
    }
}

@Composable
private fun LogicHeader(title: String, subtitle: String, logic: Logic, show: Boolean, onLogic: (Logic) -> Unit) {
    SectionHeader(
        title, subtitle = subtitle, modifier = Modifier.padding(start = Gutter),
        trailing = {
            AnimatedVisibility(show, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = logic == Logic.ALL, onClick = { onLogic(Logic.ALL) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("ET") }
                    SegmentedButton(
                        selected = logic == Logic.ANY, onClick = { onLogic(Logic.ANY) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("OU") }
                }
            }
        },
    )
}

@Composable
private fun HubRow(label: String, lit: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (lit) accent else MaterialTheme.colorScheme.surfaceContainerHighest, label = "hub")
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(Gutter))
        Surface(shape = CircleShape, color = bg) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (lit) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(start = Gutter, top = 4.dp)) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun TriggerCard(trigger: Trigger, matched: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ItemCard(
        icon = { Icon(trigger.icon(), null) },
        title = trigger.typeLabel() + if (trigger.negate) " (inversé)" else "",
        subtitle = Evaluator.describe(trigger),
        statusLabel = if (matched) "Rempli" else "En attente",
        statusOn = matched,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ActionCard(action: Action, available: Boolean, reason: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ItemCard(
        icon = { Icon(action.icon(), null) },
        title = action.title(),
        subtitle = if (available) action.describe() else reason ?: "Indisponible",
        statusLabel = if (available) null else "Indisponible",
        statusOn = false,
        onClick = onClick,
        modifier = modifier.alpha(if (available) 1f else 0.55f),
    )
}

@Composable
private fun ItemCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    statusLabel: String?,
    statusOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (statusOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "card",
    )
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (statusLabel != null) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (statusOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OptionsSection(profile: Profile, onChange: ((Profile) -> Profile) -> Unit, isActive: Boolean) {
    val haptics = rememberHaptics()
    SectionHeader("Options")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            LabeledSlider(
                label = "Priorité", value = "${profile.priority}",
                hint = "Si plusieurs profils sont actifs, le plus prioritaire l'emporte",
                current = profile.priority.toFloat(), range = 0f..100f, steps = 19,
                onChange = { v -> onChange { it.copy(priority = v.toInt()) } },
                onTick = { haptics.tick() },
            )
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Anti-rebond", value = if (profile.debounceSeconds == 0) "aucun" else "${profile.debounceSeconds} s",
                hint = "Délai avant de quitter le profil (ex. 10 s si le Bluetooth de la voiture coupe brièvement)",
                current = profile.debounceSeconds.toFloat(), range = 0f..120f, steps = 23,
                onChange = { v -> onChange { it.copy(debounceSeconds = v.toInt()) } },
                onTick = { haptics.tick() },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("Restaurer à la sortie", "Remet les réglages tels qu'ils étaient avant", profile.restoreOnExit) { v ->
                onChange { it.copy(restoreOnExit = v) }
            }
            SwitchRow("Profil activé", if (isActive) "Actif en ce moment" else "Surveillé par Pilote", profile.enabled) { v ->
                onChange { it.copy(enabled = v) }
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: String,
    hint: String?,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onTick: () -> Unit = {},
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = current,
            onValueChange = { if (it != current) onTick(); onChange(it) },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { haptics.toggle(!checked); onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { haptics.toggle(it); onChange(it) }, enabled = enabled)
    }
}
