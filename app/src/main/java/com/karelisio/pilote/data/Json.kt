package com.karelisio.pilote.data

import kotlinx.serialization.json.Json

val PiloteJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    classDiscriminator = "type"
}
