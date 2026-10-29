package com.karelisio.pilote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.model.ProfileShape

fun ProfileShape.polygon(): RoundedPolygon = when (this) {
    ProfileShape.COOKIE -> MaterialShapes.Cookie9Sided
    ProfileShape.SUNNY -> MaterialShapes.Sunny
    ProfileShape.CLOVER -> MaterialShapes.Clover4Leaf
    ProfileShape.GEM -> MaterialShapes.Gem
    ProfileShape.FLOWER -> MaterialShapes.Flower
    ProfileShape.PILL -> MaterialShapes.Pill
    ProfileShape.BURST -> MaterialShapes.SoftBurst
    ProfileShape.HEART -> MaterialShapes.Heart
    ProfileShape.PENTAGON -> MaterialShapes.Pentagon
    ProfileShape.ARCH -> MaterialShapes.Arch
    ProfileShape.PUFFY -> MaterialShapes.Puffy
    ProfileShape.GHOST -> MaterialShapes.Ghostish
}

/** Accent palette for profiles: (light, dark) pairs that sit well next to dynamic colors. */
val ProfilePalette: List<Pair<Long, Long>> = listOf(
    0xFF4355B9 to 0xFFBAC3FF, // indigo
    0xFF006A60 to 0xFF53DBC9, // teal
    0xFF9C4146 to 0xFFFFB3B4, // rose
    0xFF7A5900 to 0xFFF8BD2A, // amber
    0xFF6750A4 to 0xFFD0BCFF, // violet
    0xFF33691E to 0xFFA5D67E, // leaf
    0xFF0061A4 to 0xFF9ECAFF, // sky
    0xFF8B5000 to 0xFFFFB870, // orange
)

@Composable
fun profileAccent(profile: Profile): Color {
    val dark = isSystemInDarkTheme()
    val pair = profile.color?.let { c -> ProfilePalette.firstOrNull { it.first == c } ?: (c to c) }
        ?: ProfilePalette[(profile.shape.ordinal + profile.id.toInt()).mod(ProfilePalette.size)]
    return Color(if (dark) pair.second else pair.first)
}

@Composable
fun accentContainer(accent: Color): Color {
    val dark = isSystemInDarkTheme()
    return accent.copy(alpha = if (dark) 0.24f else 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
}
