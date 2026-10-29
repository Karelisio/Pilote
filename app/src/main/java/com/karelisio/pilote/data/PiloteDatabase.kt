package com.karelisio.pilote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromKind(kind: HistoryKind): String = kind.name
    @TypeConverter fun toKind(value: String): HistoryKind = runCatching { HistoryKind.valueOf(value) }.getOrDefault(HistoryKind.INFO)
}

@Database(
    entities = [ProfileEntity::class, HistoryEntity::class, BaselineEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PiloteDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun history(): HistoryDao
    abstract fun baselines(): BaselineDao

    companion object {
        fun build(context: Context): PiloteDatabase =
            Room.databaseBuilder(context, PiloteDatabase::class.java, "pilote.db")
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
