package com.karelisio.pilote.data

import com.karelisio.pilote.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

class ProfileRepository(private val dao: ProfileDao) {

    val profiles: Flow<List<Profile>> = dao.observeAll().map { list -> list.mapNotNull { it.toProfile() } }

    suspend fun all(): List<Profile> = dao.getAll().mapNotNull { it.toProfile() }

    suspend fun get(id: Long): Profile? = dao.get(id)?.toProfile()

    suspend fun save(profile: Profile): Long {
        val entity = ProfileEntity(
            id = profile.id,
            name = profile.name,
            enabled = profile.enabled,
            priority = profile.priority,
            json = PiloteJson.encodeToString(Profile.serializer(), profile.copy(id = 0)),
        )
        return if (profile.id == 0L) dao.insert(entity.copy(id = 0)) else { dao.upsert(entity); profile.id }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val p = get(id) ?: return
        save(p.copy(enabled = enabled))
    }

    /** Export all profiles to a JSON document (ids stripped). */
    suspend fun exportJson(): String {
        val export = ProfileExport(profiles = all().map { it.copy(id = 0) })
        return PrettyJson.encodeToString(ProfileExport.serializer(), export)
    }

    /** Import profiles from JSON; returns the number imported. Accepts an export document or a bare list. */
    suspend fun importJson(json: String): Int {
        val list = runCatching { PiloteJson.decodeFromString(ProfileExport.serializer(), json).profiles }
            .recoverCatching { PiloteJson.decodeFromString(ListSerializer(Profile.serializer()), json) }
            .getOrThrow()
        list.forEach { save(it.copy(id = 0)) }
        return list.size
    }

    private fun ProfileEntity.toProfile(): Profile? =
        runCatching { PiloteJson.decodeFromString(Profile.serializer(), json).copy(id = id, enabled = enabled, priority = priority, name = name) }
            .getOrNull()
}

@kotlinx.serialization.Serializable
data class ProfileExport(
    val app: String = "pilote",
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val profiles: List<Profile>,
)

private val PrettyJson = kotlinx.serialization.json.Json(PiloteJson) { prettyPrint = true }
