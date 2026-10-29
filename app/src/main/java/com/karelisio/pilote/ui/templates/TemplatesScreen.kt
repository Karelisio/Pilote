package com.karelisio.pilote.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karelisio.pilote.model.Template
import com.karelisio.pilote.ui.components.MorphingShapeBox
import com.karelisio.pilote.ui.components.cascade
import com.karelisio.pilote.ui.components.rememberHaptics
import com.karelisio.pilote.ui.editor.EditorViewModel
import com.karelisio.pilote.ui.navigation.sharedBoundsIfAvailable
import com.karelisio.pilote.ui.theme.ProfilePalette
import com.karelisio.pilote.ui.theme.polygon
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun TemplatesScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    val haptics = rememberHaptics()
    val dark = isSystemInDarkTheme()
    Scaffold(
        modifier = Modifier.sharedBoundsIfAvailable("new-profile"),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Nouveau profil") },
                subtitle = { Text("Partez d'un modèle prêt à adapter") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = { haptics.confirm(); onPick(EditorViewModel.EMPTY) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.cascade(0),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        MorphingShapeBox(MaterialShapes.Cookie4Sided, MaterialTheme.colorScheme.primary, size = 52.dp) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Profil vide", style = MaterialTheme.typography.titleLargeEmphasized)
                        Text("Composez vos déclencheurs et actions de zéro", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            itemsIndexed(Template.entries) { i, template ->
                val color = Color(ProfilePalette[(i + 1) % ProfilePalette.size].let { if (dark) it.second else it.first })
                Card(
                    onClick = { haptics.confirm(); onPick(template.name) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().cascade(i + 1),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        MorphingShapeBox(template.shape.polygon(), color, size = 56.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(template.title, style = MaterialTheme.typography.titleLargeEmphasized)
                        Spacer(Modifier.height(4.dp))
                        Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
