package com.example.universal.edge.data

import com.example.universal.edge.data.dao.AIDiaryDao
import com.example.universal.edge.data.dao.InteractionLogDao
import com.example.universal.edge.data.dao.UserProfileDao
import com.example.universal.edge.data.entity.AIDiaryEntry
import com.example.universal.edge.data.entity.ContextSnapshot
import com.example.universal.edge.data.entity.InteractionLog
import com.example.universal.edge.data.entity.UserProfile

/**
 * データ層の公開契約。推定層・学習層はこのインターフェースのみに依存する。
 */
interface IContextRepository {
    suspend fun getCurrentContext(): ContextSnapshot
    suspend fun getUserProfile(): UserProfile
    suspend fun logInteraction(log: InteractionLog)
    suspend fun getPendingLogs(limit: Int = 1000): List<InteractionLog>
    suspend fun markLogsConsolidated(ids: List<Long>)
    suspend fun deleteConsolidatedLogs()
    suspend fun updateProfile(profile: UserProfile)
    suspend fun saveDiaryEntry(entry: AIDiaryEntry)
    suspend fun getRecentDiaryEntries(limit: Int = 30): List<AIDiaryEntry>
    suspend fun getDiaryEntryByDate(date: String): AIDiaryEntry?
}

class ContextRepository(
    private val profileDao: UserProfileDao,
    private val logDao: InteractionLogDao,
    private val diaryDao: AIDiaryDao? = null
) : IContextRepository {

    override suspend fun getCurrentContext(): ContextSnapshot {
        return ContextSnapshot.fromTimeOfDay()
    }

    override suspend fun getUserProfile(): UserProfile {
        return profileDao.getProfile()
            ?: UserProfile().also { profileDao.upsertProfile(it) }
    }

    override suspend fun logInteraction(log: InteractionLog) {
        logDao.insert(log)
    }

    override suspend fun getPendingLogs(limit: Int): List<InteractionLog> {
        return logDao.getPendingLogs(limit)
    }

    override suspend fun markLogsConsolidated(ids: List<Long>) {
        logDao.markConsolidated(ids)
    }

    override suspend fun deleteConsolidatedLogs() {
        logDao.deleteConsolidated()
    }

    override suspend fun updateProfile(profile: UserProfile) {
        profileDao.upsertProfile(profile)
    }

    override suspend fun saveDiaryEntry(entry: AIDiaryEntry) {
        diaryDao?.insert(entry)
    }

    override suspend fun getRecentDiaryEntries(limit: Int): List<AIDiaryEntry> {
        return diaryDao?.getRecentEntries(limit) ?: emptyList()
    }

    override suspend fun getDiaryEntryByDate(date: String): AIDiaryEntry? {
        return diaryDao?.getEntryByDate(date)
    }
}
