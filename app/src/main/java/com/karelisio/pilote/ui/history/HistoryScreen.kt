package com.karelisio.pilote.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.data.HistoryEntity
import com.karelisio.pilote.data.HistoryKind
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.BreathingShape
import com.karelisio.pilote.ui.components.cascade
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Filter(val label: String, val kinds: Set<HistoryKind>?) {
    ALL("Tout", null),
    ACTIVATIONS("Activations", setOf(HistoryKind.ACTIVATED, HistoryKind.DEACTIVATED)),
    TESTS("Tests", setOf(HistoryKind.TEST_START, HistoryKind.TEST_END)),
    ERRORS("Erreurs", setOf(HistoryKind.ACTION_FAILED)),
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val entries by graph.history.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(Filter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visible = entries.filter { filter.kinds == null || it.kind in filter.kinds!! }
    val grouped = visible.groupBy { dayLabel(it.timestamp) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Historique") },
                subtitle = { Text("Quoi, quand et pourquoi") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Effacer") } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(Filter.entries) { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BreathingShape(MaterialTheme.colorScheme.secondaryContainer, size = 110.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Rien pour l'instant", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Chaque activation, désactivation et erreur apparaîtra ici.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val positions = visible.withIndex().associate { it.value.id to it.index }
            grouped.forEach { (day, list) ->
                stickyHeader(key = "h-$day") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                items(list, key = { it.id }) { entry ->
                    HistoryRow(entry, Modifier.animateItem().cascade(positions[entry.id] ?: 0, entry.id))
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { graph.history.clear() } }) { Text("Effacer") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, modifier: Modifier = Modifier) {
    val (icon, color) = style(entry.kind)
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.profileName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(timeLabel(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(kindLabel(entry.kind), style = MaterialTheme.typography.labelMedium, color = color)
            if (entry.reason.isNotBlank()) Text("Pourquoi : ${entry.reason}", style = MaterialTheme.typography.bodySmall)
            if (entry.details.isNotBlank()) {
                Text(entry.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun style(kind: HistoryKind): Pair<ImageVector, Color> {
    val s = MaterialTheme.colorScheme
    return when (kind) {
        HistoryKind.ACTIVATED -> Icons.AutoMirrored.Rounded.Login to s.primary
        HistoryKind.DEACTIVATED -> Icons.AutoMirrored.Rounded.Logout to s.secondary
        HistoryKind.TEST_START, HistoryKind.TEST_END -> Icons.Rounded.Science to s.tertiary
        HistoryKind.ACTION_FAILED -> Icons.Rounded.ErrorOutline to s.error
        HistoryKind.PAUSED -> Icons.Rounded.Pause to s.outline
        HistoryKind.RESUMED -> Icons.Rounded.PlayArrow to s.primary
        HistoryKind.INFO -> Icons.Rounded.Info to s.outline
    }
}

private fun kindLabel(kind: HistoryKind) = when (kind) {
    HistoryKind.ACTIVATED -> "Activé"
    HistoryKind.DEACTIVATED -> "Désactivé, réglages restaurés"
    HistoryKind.TEST_START -> "Simulation démarrée"
    HistoryKind.TEST_END -> "Simulation terminée"
    HistoryKind.ACTION_FAILED -> "Action en échec"
    HistoryKind.PAUSED -> "Pause globale"
    HistoryKind.RESUMED -> "Reprise"
    HistoryKind.INFO -> "Information"
}

private fun timeLabel(ts: Long) = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(ts))

private fun dayLabel(ts: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(cal, today) -> "Aujourd'hui"
        same(cal, yesterday) -> "Hier"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(ts)).replaceFirstChar(Char::uppercase)
    }
}
