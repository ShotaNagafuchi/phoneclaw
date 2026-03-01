package com.example.universal.edge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.universal.edge.data.converter.FloatArrayConverter
import com.example.universal.edge.data.dao.AIDiaryDao
import com.example.universal.edge.data.dao.InteractionLogDao
import com.example.universal.edge.data.dao.UserProfileDao
import com.example.universal.edge.data.entity.AIDiaryEntry
import com.example.universal.edge.data.entity.ContextSnapshot
import com.example.universal.edge.data.entity.InteractionLog
import com.example.universal.edge.data.entity.UserProfile

@Database(
    entities = [UserProfile::class, InteractionLog::class, ContextSnapshot::class, AIDiaryEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(FloatArrayConverter::class)
abstract class EdgeDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun interactionLogDao(): InteractionLogDao
    abstract fun aiDiaryDao(): AIDiaryDao

    companion object {
        @Volatile
        private var INSTANCE: EdgeDatabase? = null

        fun getInstance(context: Context): EdgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EdgeDatabase::class.java,
                    "edge_ai.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
