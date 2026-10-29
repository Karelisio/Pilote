package com.karelisio.pilote.data

import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class HistoryRepository(private val dao: HistoryDao) {
    val recent: Flow<List<HistoryEntity>> = dao.observeRecent()

    suspend fun log(kind: HistoryKind, profileId: Long?, profileName: String, reason: String, details: String = "") {
        dao.insert(HistoryEntity(profileId = profileId, profileName = profileName, kind = kind, reason = reason, details = details))
    }

    suspend fun prune(days: Long = 30) = dao.deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days))

    suspend fun clear() = dao.clear()
}
