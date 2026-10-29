package com.karelisio.pilote.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.model.ProfileShape
import com.karelisio.pilote.service.PiloteService
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.BreathingShape
import com.karelisio.pilote.ui.components.CompassDial
import com.karelisio.pilote.ui.components.MorphingShapeBox
import com.karelisio.pilote.ui.components.rememberHaptics
import com.karelisio.pilote.ui.permissions.PermissionId
import com.karelisio.pilote.ui.permissions.PermissionSpec
import com.karelisio.pilote.ui.permissions.PermissionSpecs
import com.karelisio.pilote.ui.permissions.rememberPermissionState
import com.karelisio.pilote.ui.theme.LocalReduceMotion
import com.karelisio.pilote.util.Vendor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Page {
    data object Welcome : Page
    data object HowItWorks : Page
    data class Permission(val spec: PermissionSpec) : Page
    data object Done : Page
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pages = remember { listOf(Page.Welcome, Page.HowItWorks) + PermissionSpecs.map { Page.Permission(it) } + Page.Done }
    val pager = rememberPagerState { pages.size }

    fun next() {
        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex), animationSpec = spring(stiffness = Spring.StiffnessLow)) }
    }

    fun finish() {
        haptics.confirm()
        scope.launch {
            graph.settings.setOnboardingDone(true)
            PiloteService.start(context)
            graph.registry.reRegisterAll()
            onFinished()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { index ->
            val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // Parallax + depth while swiping.
                    val o = offset.coerceIn(-1f, 1f)
                    alpha = 1f - kotlin.math.abs(o) * 0.6f
                    val s = 1f - kotlin.math.abs(o) * 0.08f
                    scaleX = s; scaleY = s
                },
            ) {
                when (val page = pages[index]) {
                    Page.Welcome -> WelcomePage()
                    Page.HowItWorks -> HowItWorksPage()
                    is Page.Permission -> PermissionPage(page.spec, onGranted = {
                        if (pager.currentPage == index) scope.launch { delay(700); next() }
                    })
                    Page.Done -> DonePage()
                }
            }
        }
        // Indicator + navigation
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                pages.indices.forEach { i ->
                    val selected = i == pager.currentPage
                    val width by animateDpAsState(if (selected) 22.dp else 7.dp, spring(dampingRatio = 0.6f), label = "dot")
                    Box(
                        Modifier.height(7.dp).width(width).clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            val page = pages[pager.currentPage]
            if (page is Page.Done) {
                Button(onClick = ::finish) { Text("C'est parti") }
            } else {
                if (page is Page.Permission) TextButton(onClick = ::next) { Text("Passer") }
                FilledTonalButton(onClick = { haptics.tick(); next() }) {
                    Text("Suivant")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
        if (Vendor.isHonorOrHuawei && pages[pager.currentPage].let { it is Page.Permission && it.spec.id == PermissionId.BATTERY }) {
            TextButton(onClick = { Vendor.openAppLaunch(context) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Puis : Lancement d'applis Honor → gestion manuelle")
            }
        }
    }
}

@Composable
private fun PageLayout(illustration: @Composable () -> Unit, title: String, text: String, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun WelcomePage() {
    val demo = remember {
        listOf(
            Profile(id = 1, name = "Voiture", shape = ProfileShape.GEM, color = 0xFF7A5900),
            Profile(id = 2, name = "Nuit", shape = ProfileShape.COOKIE, color = 0xFF6750A4),
            Profile(id = 3, name = "Travail", shape = ProfileShape.PENTAGON, color = 0xFF0061A4),
            Profile(id = 4, name = "Maison", shape = ProfileShape.PUFFY, color = 0xFF33691E),
            Profile(id = 5, name = "Cinéma", shape = ProfileShape.BURST, color = 0xFF9C4146),
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        while (true) { delay(1600); index = (index + 1) % demo.size }
    }
    PageLayout(
        illustration = { CompassDial(demo, demo[index], paused = false, modifier = Modifier.size(250.dp)) },
        title = "Bienvenue dans Pilote",
        text = "Votre téléphone s'adapte tout seul : son, Ne pas déranger, écran, Wi-Fi… selon le lieu, l'heure, la voiture ou vos écouteurs.",
    )
}

@Composable
private fun HowItWorksPage() {
    val inputs = listOf(Icons.Rounded.Place, Icons.Rounded.Schedule, Icons.Rounded.Bluetooth, Icons.Rounded.Wifi)
    val outputs = listOf(Icons.Rounded.DoNotDisturbOn, Icons.AutoMirrored.Rounded.VolumeUp, Icons.Rounded.DarkMode)
    var tick by remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        while (true) { delay(1300); tick++ }
    }
    val flow by rememberInfiniteTransition(label = "how").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "f")
    PageLayout(
        illustration = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlob(inputs[tick % inputs.size], MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                FlowArrow(if (reduceMotion) 0f else flow, Modifier.width(56.dp).height(24.dp))
                MorphingShapeBox(
                    listOf(MaterialShapes.Sunny, MaterialShapes.Gem, MaterialShapes.Flower)[tick % 3],
                    MaterialTheme.colorScheme.tertiary, size = 64.dp,
                )
                FlowArrow(if (reduceMotion) 0f else flow, Modifier.width(56.dp).height(24.dp))
                IconBlob(outputs[tick % outputs.size], MaterialShapes.Clover4Leaf, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
        title = "Déclencheurs → actions",
        text = "Un profil réunit des déclencheurs (ET / OU), des conditions et des actions. À la sortie du profil, Pilote restaure vos réglages.",
    )
}

@Composable
private fun IconBlob(icon: ImageVector, polygon: androidx.graphics.shapes.RoundedPolygon, bg: Color, fg: Color) {
    MorphingShapeBox(polygon, bg, size = 64.dp) {
        AnimatedContent(icon, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "icon") {
            Icon(it, null, tint = fg)
        }
    }
}

@Composable
private fun FlowArrow(phase: Float, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val y = size.height / 2
        drawLine(color.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = 4f, cap = StrokeCap.Round)
        val x = size.width * phase
        drawCircle(color, radius = 6f, center = Offset(x, y))
        drawCircle(color.copy(alpha = 0.3f), radius = 12f, center = Offset(x, y))
    }
}

@Composable
private fun PermissionPage(spec: PermissionSpec, onGranted: () -> Unit) {
    val haptics = rememberHaptics()
    val state = rememberPermissionState(spec) { granted -> if (granted) { haptics.confirm(); onGranted() } }
    PageLayout(
        illustration = { PermissionIllustration(spec.icon, state.granted) },
        title = spec.title + if (spec.optional) " (optionnel)" else "",
        text = spec.description,
    ) {
        AnimatedContent(state.granted, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "btn") { granted ->
            if (granted) {
                Text("Autorisation accordée", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = { state.request() }, modifier = Modifier.height(52.dp)) { Text("Autoriser") }
            }
        }
    }
}

@Composable
private fun PermissionIllustration(icon: ImageVector, granted: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    val pulse by rememberInfiniteTransition(label = "perm").animateFloat(
        0.92f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse",
    )
    Box(contentAlignment = Alignment.Center) {
        BreathingShape(
            if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            size = 220.dp,
        )
        MorphingShapeBox(
            if (granted) MaterialShapes.Sunny else MaterialShapes.Cookie12Sided,
            if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            size = 120.dp,
            modifier = Modifier.graphicsLayer {
                val s = if (reduceMotion || granted) 1f else pulse
                scaleX = s; scaleY = s
            },
        ) {
            AnimatedContent(granted, transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith fadeOut() }, label = "check") { ok ->
                if (ok) AnimatedCheck(MaterialTheme.colorScheme.onPrimary, Modifier.size(56.dp))
                else Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(52.dp))
            }
        }
    }
}

/** Check mark drawn progressively. */
@Composable
fun AnimatedCheck(color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(450)) }
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.74f)
            lineTo(size.width * 0.8f, size.height * 0.3f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(partial, color, style = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round))
    }
}

@Composable
private fun DonePage() {
    PageLayout(
        illustration = { BreathingShape(MaterialTheme.colorScheme.primary, size = 200.dp, periodMs = 1400) },
        title = "Tout est prêt",
        text = "Créez votre premier profil à partir d'un modèle. Vous pourrez revoir les autorisations dans Réglages.",
    )
}
