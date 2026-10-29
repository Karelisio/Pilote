package com.karelisio.pilote.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.MorphingShapeBox
import com.karelisio.pilote.update.DownloadState
import kotlinx.coroutines.launch

/** Bottom sheet shown when a newer GitHub release exists: changelog, animated download, install. */
@Composable
fun UpdateHost() {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val release by graph.updates.available.collectAsStateWithLifecycle()
    val dismissed by graph.settings.dismissedVersion.collectAsStateWithLifecycle(initialValue = null)
    val download by graph.updates.download.collectAsStateWithLifecycle()
    val r = release ?: return
    if (dismissed == r.version && download is DownloadState.Idle) return

    ModalBottomSheet(
        onDismissRequest = { if (download !is DownloadState.Downloading) scope.launch { graph.updates.dismiss() } },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingShapeBox(
                    if (download is DownloadState.Downloading) MaterialShapes.SoftBurst else MaterialShapes.Cookie9Sided,
                    MaterialTheme.colorScheme.primary, size = 52.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Mise à jour disponible", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text("Version ${r.version} · actuelle ${graph.updates.currentVersion}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Nouveautés", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                r.changelog.ifBlank { "Améliorations et corrections." },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(download, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "dl") { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        val progress by animateFloatAsState(state.progress, label = "progress")
                        Column {
                            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("Téléchargement… ${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    DownloadState.Installing -> Column {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Installation…", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    is DownloadState.Failed -> Column {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }) { Text("Autoriser l'installation") }
                        }
                    }
                    DownloadState.Idle -> Spacer(Modifier.height(0.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { graph.updates.dismiss(); graph.updates.resetDownload() } }) { Text("Plus tard") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { scope.launch { graph.updates.downloadAndInstall(r) } },
                    enabled = download !is DownloadState.Downloading && download !is DownloadState.Installing,
                ) { Text(if (download is DownloadState.Failed) "Réessayer" else "Mettre à jour") }
            }
        }
    }
}
