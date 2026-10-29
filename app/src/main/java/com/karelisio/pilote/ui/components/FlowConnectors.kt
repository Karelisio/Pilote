package com.karelisio.pilote.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.pilote.ui.theme.LocalReduceMotion

enum class PortKind { INPUT, HUB, OUTPUT }

data class Port(val kind: PortKind, val position: Offset, val lit: Boolean)

/** Collects card positions inside a container so connectors can be drawn between them. */
class FlowAnchors {
    var container: LayoutCoordinates? by mutableStateOf(null)
    val ports = mutableStateMapOf<String, Port>()
}

/** Marks a card (left edge, vertical center) as a connector port. */
fun Modifier.flowPort(anchors: FlowAnchors, key: String, kind: PortKind, lit: Boolean): Modifier =
    onGloballyPositioned { coords ->
        val container = anchors.container ?: return@onGloballyPositioned
        if (!container.isAttached || !coords.isAttached) return@onGloballyPositioned
        val pos = container.localPositionOf(coords, Offset(0f, coords.size.height / 2f))
        val port = Port(kind, pos, lit)
        if (anchors.ports[key] != port) anchors.ports[key] = port
    }

@Composable
fun ForgetPort(anchors: FlowAnchors, key: String) {
    DisposableEffect(key) { onDispose { anchors.ports.remove(key) } }
}

/**
 * Draws animated connectors in the left gutter: inputs (triggers/conditions) flow into the hub
 * below them, hubs chain together, and the last hub feeds every output (action).
 */
@Composable
fun Modifier.flowConnectors(anchors: FlowAnchors, gutter: Dp, litColor: Color, idleColor: Color): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "flow")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase")
    val t = if (reduceMotion) 0f else phase
    return this
        .onGloballyPositioned { anchors.container = it }
        .drawBehind { drawConnectors(anchors, gutter.toPx(), t, litColor, idleColor, animate = !reduceMotion) }
}

private fun DrawScope.drawConnectors(anchors: FlowAnchors, gutter: Float, phase: Float, lit: Color, idle: Color, animate: Boolean) {
    val ports = anchors.ports.values.toList()
    val hubs = ports.filter { it.kind == PortKind.HUB }.sortedBy { it.position.y }
    if (hubs.isEmpty()) return
    val busX = gutter * 0.5f
    val dash = 9.dp.toPx()
    val gap = 11.dp.toPx()
    val width = 2.5.dp.toPx()

    fun hubPoint(h: Port) = Offset(busX, h.position.y)

    fun drawFlow(path: Path, on: Boolean, reverse: Boolean = false) {
        val color = if (on) lit else idle
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = width, cap = StrokeCap.Round))
        val p = if (reverse) -phase else phase
        drawPath(
            path, color,
            style = Stroke(
                width = width, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -(dash + gap) * p),
            ),
        )
        if (on && animate) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val len = measure.length
            if (len > 0f) {
                val pos = measure.getPosition(len * phase)
                drawCircle(color.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = pos)
                drawCircle(color, radius = 3.5.dp.toPx(), center = pos)
            }
        }
    }

    // Inputs -> first hub below them.
    ports.filter { it.kind == PortKind.INPUT }.forEach { input ->
        val hub = hubs.firstOrNull { it.position.y > input.position.y } ?: return@forEach
        val start = Offset(gutter, input.position.y)
        val end = hubPoint(hub)
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(busX, start.y, busX, start.y + (end.y - start.y) * 0.35f, end.x, end.y)
        }
        drawFlow(path, input.lit)
        drawCircle(if (input.lit) lit else idle, radius = 4.dp.toPx(), center = start)
    }
    // Hub chain.
    hubs.zipWithNext().forEach { (a, b) ->
        val path = Path().apply { moveTo(busX, a.position.y); lineTo(busX, b.position.y) }
        drawFlow(path, a.lit)
    }
    // Last hub above an output -> output.
    ports.filter { it.kind == PortKind.OUTPUT }.forEach { out ->
        val hub = hubs.lastOrNull { it.position.y < out.position.y } ?: return@forEach
        val start = hubPoint(hub)
        val end = Offset(gutter, out.position.y)
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(busX, start.y + (end.y - start.y) * 0.65f, busX, end.y, end.x, end.y)
        }
        drawFlow(path, hub.lit && out.lit)
        drawCircle(if (hub.lit && out.lit) lit else idle, radius = 4.dp.toPx(), center = end)
    }
    hubs.forEach { h ->
        drawCircle(if (h.lit) lit else idle, radius = 6.dp.toPx(), center = hubPoint(h))
    }
}
