package com.example.universal.edge.learning

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.universal.edge.data.ContextRepository
import com.example.universal.edge.data.EdgeDatabase
import java.util.concurrent.TimeUnit

/**
 * 夜間メモリ統合ワーカー。
 *
 * 端末が充電中かつWiFi接続中のアイドル時に実行され、
 * 日中溜まった短期記憶（InteractionLog）を長期記憶（UserProfile）に統合する。
 *
 * 処理フロー:
 * 1. 未統合ログを取得
 * 2. EmotionType別に報酬を集計
 * 3. ThompsonSamplingBandit.consolidate()でα/βをバッチ更新
 * 4. AI日記を生成・保存（ユーザーが「AIが何を見て学んだか」を確認できる）
 * 5. 統合済みログを削除
 * 6. 90日以上前の古い日記を削除
 *
 * 人間の睡眠中の記憶整理に相当する。
 */
class MemoryConsolidationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MemoryConsolidation"
        private const val WORK_NAME = "edge_ai_memory_consolidation"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
                6, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Consolidation worker scheduled (every 6h, charging+WiFi)")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting memory consolidation")

        return try {
            val db = EdgeDatabase.getInstance(applicationContext)
            val repository = ContextRepository(
                db.userProfileDao(), db.interactionLogDao(), db.aiDiaryDao()
            )
            val bandit = ThompsonSamplingBandit()

            // 1. 未統合ログ取得
            val pendingLogs = repository.getPendingLogs(1000)
            if (pendingLogs.isEmpty()) {
                Log.d(TAG, "No pending logs to consolidate")
                return Result.success()
            }

            // 2. EmotionType別に報酬を集計
            val actionRewards = mutableMapOf<Int, MutableList<Float>>()
            for (log in pendingLogs) {
                // 確信度が低すぎるログはスキップ
                if (log.rewardConfidence < 0.2f) continue
                actionRewards.getOrPut(log.actionIndex) { mutableListOf() }
                    .add(log.rewardScore)
            }

            // 3. プロファイルをバッチ更新
            val profileBefore = repository.getUserProfile()
            val updatedProfile = bandit.consolidate(profileBefore, actionRewards)
            repository.updateProfile(updatedProfile)

            // 4. AI日記を生成・保存
            val diaryEntry = DiaryWriter.compose(pendingLogs, profileBefore, updatedProfile)
            repository.saveDiaryEntry(diaryEntry)
            Log.d(TAG, "Diary written: ${diaryEntry.date}, " +
                "${diaryEntry.totalInteractions} interactions")

            // 5. 統合済みログを削除
            val logIds = pendingLogs.map { it.id }
            repository.markLogsConsolidated(logIds)
            repository.deleteConsolidatedLogs()

            // 6. 90日以上前の古い日記を削除（ストレージ節約）
            val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            db.aiDiaryDao().deleteOlderThan(cutoff)

            Log.d(TAG, "Consolidated ${pendingLogs.size} logs, " +
                "profile v${updatedProfile.version}, " +
                "total consolidations: ${updatedProfile.totalConsolidations}")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Consolidation failed: ${e.message}")
            Result.retry()
        }
    }
}
