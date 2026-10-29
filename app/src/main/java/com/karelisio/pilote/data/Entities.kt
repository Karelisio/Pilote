package com.karelisio.pilote.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    /** Full profile serialized as JSON (triggers, conditions, actions…). */
    val json: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class HistoryKind { ACTIVATED, DEACTIVATED, TEST_START, TEST_END, ACTION_FAILED, PAUSED, RESUMED, INFO }

@Entity(tableName = "history", indices = [Index("timestamp")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val profileId: Long?,
    val profileName: String,
    val kind: HistoryKind,
    /** Why it happened (matched triggers) or error detail. */
    val reason: String,
    /** Summary of what was applied/restored. */
    val details: String = "",
)

/** Value of a setting captured before a profile first took control of it. */
@Entity(tableName = "baselines")
data class BaselineEntity(
    @PrimaryKey val key: String,
    /** Serialized [com.karelisio.pilote.model.Action] that restores the previous value. */
    val actionJson: String,
    val capturedAt: Long = System.currentTimeMillis(),
)
