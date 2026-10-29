package com.karelisio.pilote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY priority DESC, name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY priority DESC, name")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileEntity): Long

    @Upsert
    suspend fun upsert(entity: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE profiles SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface BaselineDao {
    @Query("SELECT * FROM baselines")
    suspend fun getAll(): List<BaselineEntity>

    @Query("SELECT * FROM baselines WHERE `key` = :key")
    suspend fun get(key: String): BaselineEntity?

    @Upsert
    suspend fun upsert(entity: BaselineEntity)

    @Query("DELETE FROM baselines WHERE `key` = :key")
    suspend fun delete(key: String)
}
