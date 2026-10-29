package com.karelisio.pilote

import com.karelisio.pilote.data.PiloteJson
import com.karelisio.pilote.engine.BtDevice
import com.karelisio.pilote.engine.BtDeviceKind
import com.karelisio.pilote.engine.Evaluator
import com.karelisio.pilote.engine.Facts
import com.karelisio.pilote.engine.geofenceId
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.BtKind
import com.karelisio.pilote.model.Logic
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.model.Template
import com.karelisio.pilote.model.Trigger
import com.karelisio.pilote.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EvaluatorTest {
    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        // 2026-10-26 is a Monday (ISO day 1).
        ZonedDateTime.of(2026, 10, 25 + day, hour, minute, 0, 0, ZoneId.of("Europe/Paris"))

    @Test fun timeRangeSameDay() {
        val t = Trigger.Time(startMinute = 9 * 60, endMinute = 18 * 60, days = setOf(1, 2, 3, 4, 5))
        assertTrue(Evaluator.timeMatches(t, at(1, 10)))
        assertFalse(Evaluator.timeMatches(t, at(1, 18)))
        assertFalse(Evaluator.timeMatches(t, at(6, 10)))
    }

    @Test fun timeRangeCrossingMidnight() {
        val t = Trigger.Time(startMinute = 22 * 60 + 30, endMinute = 7 * 60, days = setOf(5)) // Friday night
        assertTrue(Evaluator.timeMatches(t, at(5, 23)))
        assertTrue(Evaluator.timeMatches(t, at(6, 6, 59))) // Saturday morning belongs to Friday's range
        assertFalse(Evaluator.timeMatches(t, at(6, 7)))
        assertFalse(Evaluator.timeMatches(t, at(4, 23)))
    }

    @Test fun anyVersusAllLogic() {
        val car = Trigger.Bluetooth(kind = BtKind.ANY_CAR)
        val charging = Trigger.Charging()
        val facts = Facts(bluetooth = setOf(BtDevice("AA", "Clio", BtDeviceKind.CAR)))
        val any = Profile(id = 1, name = "p", triggerLogic = Logic.ANY, triggers = listOf(car, charging))
        val all = any.copy(triggerLogic = Logic.ALL)
        assertTrue(Evaluator.evaluate(any, facts).active)
        assertFalse(Evaluator.evaluate(all, facts).active)
        assertTrue(Evaluator.evaluate(all, facts.copy(charging = true)).active)
    }

    @Test fun conditionsAndNegation() {
        val p = Profile(
            id = 2, name = "p",
            triggers = listOf(Trigger.Wifi(ssid = "Maison")),
            conditions = listOf(Trigger.Charging(negate = true)),
        )
        assertTrue(Evaluator.evaluate(p, Facts(wifiSsid = "Maison")).active)
        assertFalse(Evaluator.evaluate(p, Facts(wifiSsid = "Maison", charging = true)).active)
        assertFalse(Evaluator.evaluate(p, Facts(wifiSsid = "Bureau")).active)
    }

    @Test fun locationUsesProfileScopedGeofenceId() {
        val loc = Trigger.Location(latitude = 48.8, longitude = 2.3)
        val p = Profile(id = 7, name = "p", triggers = listOf(loc))
        assertTrue(Evaluator.evaluate(p, Facts(insideGeofences = setOf(geofenceId(7, loc)))).active)
        assertFalse(Evaluator.evaluate(p, Facts(insideGeofences = setOf(geofenceId(8, loc)))).active)
    }

    @Test fun manualTrigger() {
        val p = Profile(id = 3, name = "Cinéma", triggers = listOf(Trigger.Manual()))
        assertFalse(Evaluator.evaluate(p, Facts()).active)
        assertTrue(Evaluator.evaluate(p, Facts(manualOn = setOf(3))).active)
    }

    @Test fun profileJsonRoundTrip() {
        Template.entries.forEach { template ->
            val p = template.build("com.karelisio.rushio")
            val json = PiloteJson.encodeToString(Profile.serializer(), p)
            assertEquals(p, PiloteJson.decodeFromString(Profile.serializer(), json))
        }
        val media = PiloteJson.encodeToString(Action.serializer(), Action.MediaPlay)
        assertEquals(Action.MediaPlay, PiloteJson.decodeFromString(Action.serializer(), media))
    }

    @Test fun versionComparison() {
        assertTrue(UpdateManager.isNewer("1.2.0", "1.1.9"))
        assertTrue(UpdateManager.isNewer("1.10.0", "1.9.3"))
        assertFalse(UpdateManager.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.2.0", "1.3.0-debug"))
    }
}
