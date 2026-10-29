package com.karelisio.pilote.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.karelisio.pilote.AppGraph

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph non fourni") }
