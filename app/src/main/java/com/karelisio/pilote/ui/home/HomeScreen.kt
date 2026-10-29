package com.karelisio.pilote.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.actions.describe
import com.karelisio.pilote.data.HistoryKind
import com.karelisio.pilote.engine.Evaluator
import com.karelisio.pilote.model.Logic
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.BreathingShape
import com.karelisio.pilote.ui.components.CompassDial
import com.karelisio.pilote.ui.components.InfoCard
import com.karelisio.pilote.ui.components.ProfileAvatar
import com.karelisio.pilote.ui.components.SectionHeader
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.components.rememberHaptics
import com.karelisio.pilote.ui.navigation.sharedBoundsIfAvailable
import com.karelisio.pilote.ui.permissions.PermissionSpecs
import com.karelisio.pilote.ui.theme.accentContainer
import com.karelisio.pilote.ui.theme.profileAccent
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val profiles by graph.profiles.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by graph.engine.active.collectAsStateWithLifecycle()
    val paused by graph.settings.paused.collectAsStateWithLifecycle(initialValue = false)
    val facts by graph.facts.state.collectAsStateWithLifecycle()
    val history by graph.history.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by graph.engine.tests.collectAsStateWithLifecycle()

    val top = active.firstOrNull()
    val reason = top?.let { p -> history.firstOrNull { it.profileId == p.id && it.kind == HistoryKind.ACTIVATED }?.reason }
    var resumeTick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val missing = remember(profiles, resumeTick) {
        PermissionSpecs.filter { !it.optional && !it.isGranted(context, graph) }
    }
    val manual = profiles.filter { it.isManual }.sortedByDescending { it.priority }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Pilote", style = MaterialTheme.typography.displaySmallEmphasized) },
                subtitle = {
                    AnimatedContent(
                        targetState = when {
                            paused -> "En pause"
                            active.isEmpty() -> "En veille · ${profiles.count { it.enabled }} profils surveillés"
                            else -> "${active.size} profil${if (active.size > 1) "s" else ""} actif${if (active.size > 1) "s" else ""}"
                        },
                        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                        label = "subtitle",
                    ) { Text(it) }
                },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, "Historique") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Réglages") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { haptics.confirm(); onNewProfile() },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nouveau profil") },
                expanded = !listState.canScrollBackward,
                modifier = Modifier.sharedBoundsIfAvailable("new-profile"),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                HeroCard(
                    profiles = profiles,
                    active = top,
                    reason = reason,
                    paused = paused,
                    onTogglePause = {
                        haptics.toggle(!paused)
                        scope.launch { graph.engine.setPaused(!paused) }
                    },
                    modifier = Modifier.cascade(0),
                )
            }
            if (missing.isNotEmpty()) {
                item(key = "perm") {
                    InfoCard(
                        icon = Icons.Rounded.WarningAmber,
                        title = "${missing.size} autorisation${if (missing.size > 1) "s" else ""} manquante${if (missing.size > 1) "s" else ""}",
                        text = missing.joinToString { it.title },
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 12.dp).cascade(1),
                        action = { TextButton(onClick = onPermissions) { Text("Vérifier") } },
                    )
                }
            }
            if (manual.isNotEmpty()) {
                item(key = "manual-header") { SectionHeader("Raccourcis", subtitle = "Profils manuels (aussi en tuiles et widget)") }
                item(key = "manual") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.cascade(2)) {
                        manual.forEach { p ->
                            val on = p.id in facts.manualOn
                            ToggleButton(
                                checked = on,
                                onCheckedChange = {
                                    haptics.toggle(it)
                                    graph.engine.setManual(p.id, it)
                                },
                            ) {
                                ProfileAvatar(p, size = 22.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(p.name)
                            }
                        }
                    }
                }
            }
            item(key = "profiles-header") {
                SectionHeader("Profils", subtitle = if (profiles.isEmpty()) null else "Le plus prioritaire l'emporte")
            }
            if (profiles.isEmpty()) {
                item(key = "empty") { EmptyState(onNewProfile, Modifier.cascade(3)) }
            }
            items(profiles, key = { it.id }) { profile ->
                val index = profiles.indexOf(profile)
                ProfileCard(
                    profile = profile,
                    active = active.any { it.id == profile.id },
                    top = top?.id == profile.id,
                    testing = profile.id in tests,
                    onClick = { onOpenProfile(profile.id) },
                    onToggle = { enabled ->
                        haptics.toggle(enabled)
                        scope.launch { graph.profiles.setEnabled(profile.id, enabled) }
                    },
                    onTest = { graph.engine.startTest(profile.id) },
                    onDuplicate = {
                        scope.launch { graph.profiles.save(profile.copy(id = 0, name = profile.name + " (copie)", enabled = false)) }
                    },
                    onDelete = { scope.launch { graph.profiles.delete(profile.id) } },
                    modifier = Modifier
                        .padding(vertical = 5.dp)
                        .animateItem()
                        .cascade(index + 3, profile.id),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    profiles: List<Profile>,
    active: Profile?,
    reason: String?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = active?.let { profileAccent(it) } ?: MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (active != null && !paused) accentContainer(accent) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CompassDial(
                profiles = profiles,
                active = active,
                paused = paused,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = Triple(active?.name, paused, reason),
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "active",
            ) { (name, isPaused, why) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            isPaused -> "En pause"
                            name != null -> name
                            else -> "Aucun profil actif"
                        },
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = when {
                            isPaused -> "Vos réglages ne changent plus automatiquement"
                            name != null -> why ?: "Actif"
                            else -> "Pilote surveille vos déclencheurs"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onTogglePause) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (paused) "Reprendre" else "Pause globale")
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    active: Boolean,
    top: Boolean,
    testing: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    val accent = profileAccent(profile)
    Box(modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (active) accentContainer(accent) else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .sharedBoundsIfAvailable("profile-${profile.id}")
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { haptics.longPress(); menu = true },
                ),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile, size = 52.dp, active = active)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        AnimatedVisibility(active || testing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            ActiveBadge(if (testing) "Test" else if (top) "Actif" else "En pile", accent)
                        }
                    }
                    Text(
                        triggersSummary(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.actions.joinToString(" · ") { it.describe() }.ifEmpty { "Aucune action" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = profile.enabled, onCheckedChange = onToggle)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Simuler 30 s") }, leadingIcon = { Icon(Icons.Rounded.Science, null) }, onClick = { menu = false; onTest() })
            DropdownMenuItem(text = { Text("Dupliquer") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
            DropdownMenuItem(text = { Text("Supprimer") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun ActiveBadge(label: String, accent: androidx.compose.ui.graphics.Color) {
    val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Box(Modifier.size(6.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(accent))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

private fun triggersSummary(profile: Profile): String {
    val joiner = if (profile.triggerLogic == Logic.ALL) " et " else " ou "
    val t = profile.triggers.joinToString(joiner) { Evaluator.describe(it) }
    val c = profile.conditions.takeIf { it.isNotEmpty() }?.joinToString(", ") { Evaluator.describe(it) }
    return listOfNotNull(t.ifEmpty { null }, c?.let { "si $it" }).joinToString(" · ").ifEmpty { "Aucun déclencheur" }
}

@Composable
private fun EmptyState(onNewProfile: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BreathingShape(MaterialTheme.colorScheme.primaryContainer, size = 140.dp)
        Spacer(Modifier.height(20.dp))
        Text("Aucun profil pour l'instant", style = MaterialTheme.typography.titleLarge)
        Text(
            "Partez d'un modèle (Voiture, Nuit, Travail…) et adaptez-le.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(0.9f).padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onNewProfile) { Text("Choisir un modèle") }
    }
}
