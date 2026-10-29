package com.karelisio.pilote

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.karelisio.pilote.service.PiloteService
import com.karelisio.pilote.ui.LocalGraph
import com.karelisio.pilote.ui.components.ActivationWaveHost
import com.karelisio.pilote.ui.navigation.PiloteNavHost
import com.karelisio.pilote.ui.theme.PiloteTheme
import com.karelisio.pilote.ui.update.UpdateHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val onboardingState = MutableStateFlow<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = PiloteApp.graph(this)

        splash.setKeepOnScreenCondition { onboardingState.value == null }
        splash.setOnExitAnimationListener { provider ->
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            val set = AnimatorSet()
            if (icon != null) {
                set.playTogether(
                    fade,
                    ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.6f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.6f),
                    ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 25f),
                )
            } else {
                set.play(fade)
            }
            set.duration = 420
            set.interpolator = AnticipateInterpolator(0.6f)
            set.doOnEndCompat { provider.remove() }
            set.start()
        }

        lifecycleScope.launch {
            val done = graph.settings.onboardingDone.first()
            onboardingState.value = done
            if (done) {
                PiloteService.start(this@MainActivity)
                graph.updates.check()
            }
        }

        setContent {
            val dynamic by graph.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val done by onboardingState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalGraph provides graph) {
                PiloteTheme(dynamicColor = dynamic) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ActivationWaveHost(graph.engine.events) {
                            done?.let { PiloteNavHost(startWithOnboarding = !it) }
                        }
                        UpdateHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PiloteApp.graph(this).shizuku.refresh()
    }
}

private fun AnimatorSet.doOnEndCompat(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
    })
}
