package com.karelisio.pilote.surfaces

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.pilote.MainActivity
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.R
import com.karelisio.pilote.model.Profile

/** Home screen widget: active profile + one button per manual profile. Dynamic colors via GlanceTheme. */
class PiloteWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 56.dp), DpSize(180.dp, 110.dp), DpSize(260.dp, 180.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = PiloteApp.graph(context)
        provideContent {
            val active by graph.engine.active.collectAsState()
            val manual by graph.surfaces.manualProfiles.collectAsState()
            val facts by graph.facts.state.collectAsState()
            val paused by graph.settings.paused.collectAsState(initial = false)
            GlanceTheme {
                WidgetContent(active.firstOrNull(), manual, facts.manualOn, paused)
            }
        }
    }
}

private val radiusModifier: GlanceModifier
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    else GlanceModifier.cornerRadius(24.dp)

@Composable
private fun WidgetContent(active: Profile?, manual: List<Profile>, manualOn: Set<Long>, paused: Boolean) {
    val size = LocalSize.current
    val compact = size.height < 100.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(radiusModifier)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Box(
                modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp)
                    .background(if (active != null && !paused) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_pilote),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(
                        if (active != null && !paused) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
                    ),
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = if (paused) "En pause" else "Profil actif",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
                Text(
                    text = if (paused) "Pilote" else active?.name ?: "Aucun",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        if (!compact && manual.isNotEmpty()) {
            Spacer(GlanceModifier.height(10.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                items(manual.size, itemId = { manual[it].id }) { index ->
                    val p = manual[index]
                    ManualButton(p, p.id in manualOn)
                }
            }
        }
    }
}

@Composable
private fun ManualButton(profile: Profile, on: Boolean) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(44.dp)
                .cornerRadius(22.dp)
                .background(if (on) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 16.dp)
                .clickable(actionRunCallback<ToggleManualAction>(actionParametersOf(ToggleManualAction.ProfileKey to profile.id))),
        ) {
            Text(
                text = profile.name,
                style = TextStyle(
                    color = if (on) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = if (on) "ON" else "OFF",
                style = TextStyle(
                    color = if (on) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

class ToggleManualAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ProfileKey] ?: return
        PiloteApp.graph(context).engine.toggleManual(id)
    }

    companion object {
        val ProfileKey = ActionParameters.Key<Long>("profile_id")
    }
}

class PiloteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PiloteWidget()
}
