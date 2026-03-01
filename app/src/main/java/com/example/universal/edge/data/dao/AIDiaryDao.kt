package com.example.universal.edge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.universal.edge.data.entity.AIDiaryEntry

@Dao
interface AIDiaryDao {
    @Insert
    suspend fun insert(entry: AIDiaryEntry): Long

    @Query("SELECT * FROM ai_diary_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int = 30): List<AIDiaryEntry>

    @Query("SELECT * FROM ai_diary_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: String): AIDiaryEntry?

    @Query("SELECT * FROM ai_diary_entries WHERE id = :id")
    suspend fun getEntry(id: Long): AIDiaryEntry?

    @Query("SELECT COUNT(*) FROM ai_diary_entries")
    suspend fun getEntryCount(): Int

    /** 古い日記は90日で自動削除（ストレージ節約） */
    @Query("DELETE FROM ai_diary_entries WHERE createdAt < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
