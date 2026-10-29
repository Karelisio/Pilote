package com.karelisio.pilote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.karelisio.pilote.engine.EngineEvent
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.ui.theme.LocalReduceMotion
import com.karelisio.pilote.ui.theme.polygon
import com.karelisio.pilote.ui.theme.profileAccent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.math.hypot

private data class Wave(val profile: Profile, val key: Long)

private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Wraps the app: when a profile activates, a wave of its color sweeps the screen while its shape morphs in. */
@Composable
fun ActivationWaveHost(events: Flow<EngineEvent>, content: @Composable () -> Unit) {
    var wave by remember { mutableStateOf<Wave?>(null) }
    LaunchedEffect(events) {
        events.filterIsInstance<EngineEvent.Activated>().collect { wave = Wave(it.profile, System.nanoTime()) }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        wave?.let { w -> ActivationWave(w) { if (wave?.key == w.key) wave = null } }
    }
}

@Composable
private fun ActivationWave(wave: Wave, onDone: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val view = LocalView.current
    val accent = profileAccent(wave.profile)
    val progress = remember(wave.key) { Animatable(0f) }
    val morph = remember(wave.profile.shape) { Morph(MaterialShapes.Circle, wave.profile.shape.polygon()) }

    LaunchedEffect(wave.key) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (reduceMotion) {
            kotlinx.coroutines.delay(700)
        } else {
            progress.animateTo(1f, tween(1400, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        }
        onDone()
    }
    val p = progress.value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val origin = Offset(constraints.maxWidth / 2f, constraints.maxHeight * 0.3f)
        if (!reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                val maxR = hypot(size.width, size.height)
                drawCircle(accent.copy(alpha = 0.28f * (1f - p)), radius = maxR * p, center = origin)
                drawCircle(
                    accent.copy(alpha = 0.55f * (1f - p)),
                    radius = maxR * p * 0.82f,
                    center = origin,
                    style = Stroke(width = 28.dp.toPx() * (1f - p)),
                )
                drawCircle(accent.copy(alpha = 0.35f * (1f - p)), radius = maxR * (p * 0.6f), center = origin, style = Stroke(width = 6.dp.toPx()))
            }
        }
        val shapeIn = EaseOutBack.transform((p * 2.2f).coerceIn(0f, 1f))
        val fade = if (reduceMotion) 1f else (1f - ((p - 0.65f) / 0.35f)).coerceIn(0f, 1f)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { (origin.y).toDp() - 60.dp })
                .graphicsLayer { alpha = fade },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        val s = if (reduceMotion) 1f else 0.4f + 0.6f * shapeIn
                        scaleX = s; scaleY = s
                        rotationZ = if (reduceMotion) 0f else 90f * (1f - shapeIn)
                    }
                    .clip(MorphShape(morph, if (reduceMotion) 1f else (p * 1.8f).coerceIn(0f, 1f)))
                    .background(accent),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                wave.profile.name,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
