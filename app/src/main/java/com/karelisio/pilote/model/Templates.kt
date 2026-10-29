package com.karelisio.pilote.model

/** Ready-to-adapt profiles. */
enum class Template(val title: String, val description: String, val shape: ProfileShape) {
    CAR("Voiture", "Bluetooth de la voiture → volume média, Ne pas déranger, Rushio et lecture", ProfileShape.GEM),
    NIGHT("Nuit", "22:30–07:00 → Ne pas déranger, vibreur, écran sombre", ProfileShape.COOKIE),
    WORK("Travail", "Au bureau en semaine → vibreur, notifications discrètes", ProfileShape.PENTAGON),
    CINEMA("Cinéma", "Bouton manuel → silence total, luminosité au minimum", ProfileShape.BURST),
    HOME("Maison", "Wi-Fi de la maison → sonnerie normale, données mobiles coupées", ProfileShape.PUFFY);

    fun build(rushioPackage: String): Profile = when (this) {
        CAR -> Profile(
            name = title, shape = shape, color = 0xFF7A5900, priority = 80, debounceSeconds = 10,
            triggerLogic = Logic.ANY,
            triggers = listOf(Trigger.Bluetooth(kind = BtKind.ANY_CAR)),
            actions = listOf(
                Action.Volume(AudioStream.MEDIA, 70),
                Action.Dnd(DndMode.PRIORITY),
                Action.LaunchApp(rushioPackage, "Rushio"),
                Action.MediaPlay,
            ),
        )
        NIGHT -> Profile(
            name = title, shape = shape, color = 0xFF6750A4, priority = 60,
            triggers = listOf(Trigger.Time(startMinute = 22 * 60 + 30, endMinute = 7 * 60)),
            actions = listOf(
                Action.Dnd(DndMode.PRIORITY),
                Action.Ringer(RingerMode.VIBRATE),
                Action.Brightness(auto = false, percent = 10),
                Action.DarkMode(true),
            ),
        )
        WORK -> Profile(
            name = title, shape = shape, color = 0xFF0061A4, priority = 50,
            triggerLogic = Logic.ALL,
            triggers = listOf(
                Trigger.Location(label = "Bureau", radiusMeters = 200f),
                Trigger.Time(startMinute = 9 * 60, endMinute = 18 * 60, days = setOf(1, 2, 3, 4, 5)),
            ),
            actions = listOf(
                Action.Ringer(RingerMode.VIBRATE),
                Action.Volume(AudioStream.NOTIFICATION, 30),
            ),
        )
        CINEMA -> Profile(
            name = title, shape = shape, color = 0xFF9C4146, priority = 90,
            triggers = listOf(Trigger.Manual()),
            actions = listOf(
                Action.Ringer(RingerMode.SILENT),
                Action.Dnd(DndMode.TOTAL),
                Action.Brightness(auto = false, percent = 5),
            ),
        )
        HOME -> Profile(
            name = title, shape = shape, color = 0xFF33691E, priority = 40,
            triggers = listOf(Trigger.Wifi(ssid = "")),
            actions = listOf(
                Action.Ringer(RingerMode.NORMAL),
                Action.Volume(AudioStream.RING, 80),
                Action.MobileData(false),
            ),
        )
    }
}
