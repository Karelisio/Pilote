package com.karelisio.pilote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared-bounds transition between screens when both scopes are available (no-op otherwise). */
@Composable
fun Modifier.sharedBoundsIfAvailable(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedBoundsIfAvailable.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = nav,
        )
    }
}
