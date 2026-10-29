package com.karelisio.pilote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.ui.theme.LocalReduceMotion
import com.karelisio.pilote.ui.theme.polygon
import com.karelisio.pilote.ui.theme.profileAccent
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home dial: every profile has a mark on the dial; the compass needle springs toward the active one.
 */
@Composable
fun CompassDial(
    profiles: List<Profile>,
    active: Profile?,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    val marks = profiles.filter { it.enabled }.take(24)
    val accents = marks.map { profileAccent(it) }
    val activeIndex = marks.indexOfFirst { it.id == active?.id }

    fun angleOf(index: Int) = if (marks.isEmpty()) 0f else index * 360f / marks.size

    val target = when {
        paused -> 180f
        activeIndex >= 0 -> angleOf(activeIndex)
        else -> 0f
    }
    val needle = remember { Animatable(target) }
    LaunchedEffect(target) {
        // Shortest path toward the new angle.
        val current = needle.value
        var delta = (target - current) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        if (reduceMotion) {
            needle.snapTo(current + delta)
        } else {
            if (delta != 0f) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            needle.animateTo(current + delta, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessVeryLow))
        }
    }

    val infinite = rememberInfiniteTransition(label = "dial")
    val idle by infinite.animateFloat(
        initialValue = -3.5f, targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "idle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse",
    )
    val wobble = if (reduceMotion || activeIndex >= 0 || paused) 0f else idle
    val pulseValue = if (reduceMotion) 0f else pulse

    val activeAccent = if (activeIndex >= 0) accents[activeIndex] else scheme.primary

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        // Living shape behind the hub, morphing to the active profile's shape.
        MorphingShapeBox(
            polygon = active?.shape?.polygon() ?: androidx.compose.material3.MaterialShapes.Cookie12Sided,
            color = if (paused) scheme.surfaceContainerHighest else activeAccent.copy(alpha = 0.16f),
            size = 150.dp,
            modifier = Modifier.graphicsLayer { rotationZ = needle.value * 0.25f },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val c = center
            // Track
            drawCircle(scheme.surfaceContainerHigh, radius = radius * 0.94f, style = Stroke(width = radius * 0.1f))
            // Ticks
            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0 - 90).toFloat()
                val long = i % 6 == 0
                val r1 = radius * (if (long) 0.80f else 0.84f)
                val r2 = radius * 0.87f
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = if (long) 0.9f else 0.5f),
                    start = c + Offset(cos(a) * r1, sin(a) * r1),
                    end = c + Offset(cos(a) * r2, sin(a) * r2),
                    strokeWidth = if (long) 2.5f else 1.5f,
                    cap = StrokeCap.Round,
                )
            }
            // Profile marks on the track
            marks.forEachIndexed { i, _ ->
                val a = Math.toRadians(angleOf(i) - 90.0).toFloat()
                val p = c + Offset(cos(a), sin(a)) * (radius * 0.94f)
                val isActive = i == activeIndex && !paused
                if (isActive) {
                    drawCircle(accents[i].copy(alpha = 0.35f * (1f - pulseValue)), radius = radius * (0.06f + 0.07f * pulseValue), center = p)
                }
                drawCircle(accents[i], radius = radius * (if (isActive) 0.055f else 0.032f), center = p)
            }
            // Needle
            rotate(needle.value + wobble, c) {
                drawNeedle(c, radius * 0.66f, north = if (paused) scheme.outline else activeAccent, south = scheme.outlineVariant)
            }
            drawCircle(scheme.surface, radius = radius * 0.075f, center = c)
            drawCircle(if (paused) scheme.outline else activeAccent, radius = radius * 0.045f, center = c)
        }
    }
}

private fun DrawScope.drawNeedle(c: Offset, length: Float, north: Color, south: Color) {
    val w = length * 0.16f
    val northPath = Path().apply {
        moveTo(c.x, c.y - length)
        lineTo(c.x + w, c.y)
        lineTo(c.x - w, c.y)
        close()
    }
    val southPath = Path().apply {
        moveTo(c.x, c.y + length * 0.82f)
        lineTo(c.x + w, c.y)
        lineTo(c.x - w, c.y)
        close()
    }
    drawPath(southPath, south)
    drawPath(northPath, north)
    // Subtle highlight on half of the north blade.
    val shine = Path().apply {
        moveTo(c.x, c.y - length)
        lineTo(c.x + w, c.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(shine, Color.White.copy(alpha = 0.18f))
}
