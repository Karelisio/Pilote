package com.karelisio.pilote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.pilote.ui.theme.LocalReduceMotion

/** Shape drawing a [Morph] between two normalized polygons at [progress]. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        val matrix = Matrix()
        matrix.translate(size.width / 2f, size.height / 2f)
        matrix.rotateZ(rotationDegrees)
        matrix.scale(size.width, size.height)
        matrix.translate(-0.5f, -0.5f)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

fun RoundedPolygon.asShape(rotation: Float = 0f): Shape = MorphShape(Morph(this, this), 0f, rotation)

/**
 * A box clipped to [polygon]; when the polygon changes, the outline morphs with a spring.
 */
@Composable
fun MorphingShapeBox(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    rotation: Float = 0f,
    content: @Composable () -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    var from by remember { mutableStateOf(polygon) }
    var to by remember { mutableStateOf(polygon) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(polygon) {
        if (polygon === to) return@LaunchedEffect
        from = if (progress.value >= 0.5f) to else from
        to = polygon
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    Box(
        modifier = modifier
            .size(size)
            .clip(MorphShape(morph, progress.value.coerceIn(0f, 1f), rotation))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Shapes cycled by decorative animations. */
val DecorativeShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf,
    MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Puffy,
)

/** Slowly rotating + morphing decorative blob, alive but subtle. */
@Composable
fun BreathingShape(color: Color, modifier: Modifier = Modifier, size: Dp = 120.dp, periodMs: Int = 2600) {
    val reduceMotion = LocalReduceMotion.current
    var index by remember { mutableStateOf(0) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(periodMs.toLong())
            index = (index + 1) % DecorativeShapes.size
        }
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            rotation.animateTo(rotation.value + 360f, androidx.compose.animation.core.tween(24_000, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    MorphingShapeBox(
        polygon = DecorativeShapes[index],
        color = color,
        size = size,
        modifier = modifier.graphicsLayer { rotationZ = rotation.value },
    )
}
