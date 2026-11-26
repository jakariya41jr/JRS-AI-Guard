package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TimelineEventEntity::class,
        NetworkSessionEntity::class,
        AppTrustEntity::class,
        ScanCacheEntity::class,
        SecuritySnapshotEntity::class,
        AiInsightEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JrsGuardDatabase : RoomDatabase() {
    abstract fun dao(): JrsGuardDao

    companion object {
        @Volatile
        private var INSTANCE: JrsGuardDatabase? = null

        fun getInstance(context: Context): JrsGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JrsGuardDatabase::class.java,
                    "jrs_ai_guard_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
