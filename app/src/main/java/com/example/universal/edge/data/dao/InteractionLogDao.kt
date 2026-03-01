package com.example.universal.edge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.universal.edge.data.entity.InteractionLog

@Dao
interface InteractionLogDao {
    @Insert
    suspend fun insert(log: InteractionLog): Long

    @Query("SELECT * FROM interaction_logs WHERE consolidated = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingLogs(limit: Int = 1000): List<InteractionLog>

    @Query("UPDATE interaction_logs SET consolidated = 1 WHERE id IN (:ids)")
    suspend fun markConsolidated(ids: List<Long>)

    @Query("DELETE FROM interaction_logs WHERE consolidated = 1")
    suspend fun deleteConsolidated()

    @Query("SELECT COUNT(*) FROM interaction_logs WHERE consolidated = 0")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM interaction_logs")
    suspend fun getTotalCount(): Int
}
