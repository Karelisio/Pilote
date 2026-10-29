package com.karelisio.pilote.engine

import com.karelisio.pilote.model.BtKind
import com.karelisio.pilote.model.Logic
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.model.Trigger
import java.time.ZonedDateTime

data class TriggerResult(val trigger: Trigger, val matched: Boolean, val label: String)

data class Evaluation(
    val active: Boolean,
    val triggers: List<TriggerResult>,
    val conditions: List<TriggerResult>,
) {
    /** Human readable reason built from the matched triggers / conditions. */
    val reason: String
        get() = (triggers + conditions).filter { it.matched }.joinToString(" · ") { it.label }.ifEmpty { "—" }
}

fun geofenceId(profileId: Long, trigger: Trigger.Location) = "$profileId:${trigger.id}"

/** Pure evaluation of a profile against the current facts. */
object Evaluator {

    fun evaluate(profile: Profile, facts: Facts, now: ZonedDateTime = ZonedDateTime.now()): Evaluation {
        val t = profile.triggers.map { result(profile, it, facts, now) }
        val c = profile.conditions.map { result(profile, it, facts, now) }
        val triggersOk = when {
            t.isEmpty() -> c.isNotEmpty()
            profile.triggerLogic == Logic.ALL -> t.all { it.matched }
            else -> t.any { it.matched }
        }
        val conditionsOk = when {
            c.isEmpty() -> true
            profile.conditionLogic == Logic.ALL -> c.all { it.matched }
            else -> c.any { it.matched }
        }
        return Evaluation(triggersOk && conditionsOk, t, c)
    }

    private fun result(profile: Profile, trigger: Trigger, facts: Facts, now: ZonedDateTime): TriggerResult {
        val raw = matches(profile, trigger, facts, now)
        val matched = if (trigger.negate) !raw else raw
        return TriggerResult(trigger, matched, describe(trigger))
    }

    fun matches(profile: Profile, trigger: Trigger, facts: Facts, now: ZonedDateTime): Boolean = when (trigger) {
        is Trigger.Location -> trigger.isSet && geofenceId(profile.id, trigger) in facts.insideGeofences
        is Trigger.Time -> timeMatches(trigger, now)
        is Trigger.Bluetooth -> when (trigger.kind) {
            BtKind.SPECIFIC -> facts.bluetooth.any { it.address.equals(trigger.address, ignoreCase = true) }
            BtKind.ANY_CAR -> facts.bluetooth.any { it.kind == BtDeviceKind.CAR }
            BtKind.ANY_HEADPHONES -> facts.bluetooth.any { it.kind == BtDeviceKind.HEADPHONES }
            BtKind.ANY -> facts.bluetooth.isNotEmpty()
        }
        is Trigger.WiredHeadset -> facts.wiredHeadset
        is Trigger.Wifi -> trigger.ssid.isNotBlank() && facts.wifiSsid?.equals(trigger.ssid.trim(), ignoreCase = false) == true
        is Trigger.Charging -> facts.charging
        is Trigger.AppOpen -> trigger.packageName.isNotBlank() && facts.foregroundApp == trigger.packageName
        is Trigger.Manual -> profile.id in facts.manualOn
    }

    fun timeMatches(trigger: Trigger.Time, now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        val start = trigger.startMinute
        val end = trigger.endMinute
        return when {
            start == end -> today in trigger.days
            start < end -> today in trigger.days && minute >= start && minute < end
            else -> (today in trigger.days && minute >= start) || (yesterday in trigger.days && minute < end)
        }
    }

    fun describe(trigger: Trigger): String {
        val base = when (trigger) {
            is Trigger.Location -> "Lieu : ${trigger.label.ifBlank { "zone de ${trigger.radiusMeters.toInt()} m" }}"
            is Trigger.Time -> "Heure : ${formatMinute(trigger.startMinute)}–${formatMinute(trigger.endMinute)} ${formatDays(trigger.days)}"
            is Trigger.Bluetooth -> when (trigger.kind) {
                BtKind.SPECIFIC -> "Bluetooth : ${trigger.name.ifBlank { trigger.address }}"
                BtKind.ANY_CAR -> "Bluetooth : une voiture"
                BtKind.ANY_HEADPHONES -> "Bluetooth : des écouteurs"
                BtKind.ANY -> "Bluetooth : un appareil"
            }
            is Trigger.WiredHeadset -> "Écouteurs filaires"
            is Trigger.Wifi -> "Wi-Fi : ${trigger.ssid.ifBlank { "?" }}"
            is Trigger.Charging -> "En charge"
            is Trigger.AppOpen -> "Appli : ${trigger.label.ifBlank { trigger.packageName }}"
            is Trigger.Manual -> "Manuel"
        }
        return if (trigger.negate) "Pas : $base" else base
    }

    fun formatMinute(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    fun formatDays(days: Set<Int>): String = when {
        days.size == 7 -> "tous les jours"
        days == setOf(1, 2, 3, 4, 5) -> "en semaine"
        days == setOf(6, 7) -> "le week-end"
        else -> days.sorted().joinToString(" ") { listOf("lu", "ma", "me", "je", "ve", "sa", "di")[it - 1] }
    }
}
