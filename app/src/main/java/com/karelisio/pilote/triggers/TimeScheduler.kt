package com.karelisio.pilote.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.karelisio.pilote.PiloteApp
import com.karelisio.pilote.model.Profile
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Schedules one exact alarm at the next time-range boundary of any enabled profile. */
class TimeScheduler(private val context: Context) {
    private val alarms = context.getSystemService<AlarmManager>()!!

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, TimeAlarmReceiver::class.java).setAction(ACTION_TIME_BOUNDARY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun reschedule(profiles: List<Profile>) {
        val minutes = profiles.filter { it.enabled }
            .flatMap { it.timeTriggers }
            .flatMap { listOf(it.startMinute, it.endMinute) }
            .toSortedSet()
        val pi = pendingIntent()
        if (minutes.isEmpty()) {
            alarms.cancel(pi)
            return
        }
        val now = ZonedDateTime.now()
        val today = now.truncatedTo(ChronoUnit.DAYS)
        val next = (0..1).asSequence()
            .flatMap { d -> minutes.asSequence().map { today.plusDays(d.toLong()).plusMinutes(it.toLong()) } }
            .first { it.isAfter(now) }
        // A couple of seconds after the boundary so the evaluation clearly lands on the new minute.
        val at = next.toInstant().toEpochMilli() + 2_000
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    companion object {
        const val ACTION_TIME_BOUNDARY = "com.karelisio.pilote.TIME_BOUNDARY"
    }
}

class TimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = PiloteApp.graph(context)
        val pending = goAsync()
        graph.appScope.launchReceiver(pending) {
            graph.engine.refresh()
            graph.registry.rescheduleTime()
            com.karelisio.pilote.service.PiloteService.start(context)
        }
    }
}
