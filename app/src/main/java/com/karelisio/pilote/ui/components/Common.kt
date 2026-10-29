package com.karelisio.pilote.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.ui.theme.LocalReduceMotion
import com.karelisio.pilote.ui.theme.polygon
import com.karelisio.pilote.ui.theme.profileAccent

/** Subtle haptics, mapped to the closest platform constant. */
class Haptics(private val view: View) {
    fun tick() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    )
    fun confirm() = view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    fun reject() = view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    fun toggle(on: Boolean) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        else HapticFeedbackConstants.CONTEXT_CLICK
    )
    fun longPress() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** Cascade entrance for list items: fade + rise + spring, delayed by index. */
fun Modifier.cascade(index: Int, key: Any = Unit): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val offsetPx = with(LocalDensity.current) { 28.dp.toPx() }
    val progress = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            kotlinx.coroutines.delay((index.coerceAtMost(12) * 45L))
            progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * offsetPx
        val s = 0.94f + 0.06f * p
        scaleX = s; scaleY = s
    }
}

/** Gentle fade-in for whole screens' content. */
fun Modifier.fadeIn(delayMs: Int = 0): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        alpha.animateTo(1f, tween(400))
    }
    graphicsLayer { this.alpha = alpha.value }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Profile avatar: its expressive shape filled with its accent, initial in the middle. */
@Composable
fun ProfileAvatar(profile: Profile, modifier: Modifier = Modifier, size: Dp = 48.dp, active: Boolean = false) {
    val accent = profileAccent(profile)
    MorphingShapeBox(
        polygon = profile.shape.polygon(),
        color = if (profile.enabled) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        size = size,
        modifier = modifier,
    ) {
        Text(
            profile.name.trim().take(1).uppercase().ifEmpty { "•" },
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = if (profile.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
        )
    }
}

@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (action != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { action() }
            }
        }
    }
}
