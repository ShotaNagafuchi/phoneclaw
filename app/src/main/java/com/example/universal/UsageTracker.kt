package com.example.universal

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

/**
 * UsageTracker: UsageStatsManagerでアプリ使用履歴を記録
 * MVPでは可視化は控えめにし、提案精度改善に使用
 */
class UsageTracker(private val context: Context) {
    companion object {
        private const val TAG = "UsageTracker"
        private const val USAGE_STATS_INTERVAL = 24 * 60 * 60 * 1000L // 24時間
        
        var instance: UsageTracker? = null
            private set
    }

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val appUsageMap = mutableMapOf<String, Long>() // packageName -> usageTime (ms)

    init {
        instance = this
        updateUsageStats()
    }

    /**
     * 使用統計を更新
     */
    fun updateUsageStats() {
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return
        }

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        val startTime = endTime - USAGE_STATS_INTERVAL

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        appUsageMap.clear()
        stats?.forEach { stat ->
            val packageName = stat.packageName
            val totalTime = stat.totalTimeInForeground
            appUsageMap[packageName] = (appUsageMap[packageName] ?: 0L) + totalTime
        }

        Log.d(TAG, "Updated usage stats for ${appUsageMap.size} apps")
    }

    /**
     * 特定アプリの使用時間を取得（ミリ秒）
     */
    fun getAppUsageTime(packageName: String): Long {
        return appUsageMap[packageName] ?: 0L
    }

    /**
     * 最も使用時間の長いアプリを取得
     */
    fun getMostUsedApp(): Pair<String, Long>? {
        return appUsageMap.maxByOrNull { it.value }?.let { it.key to it.value }
    }

    /**
     * すべてのアプリ使用時間を取得
     */
    fun getAllUsageStats(): Map<String, Long> {
        return appUsageMap.toMap()
    }

    /**
     * 特定アプリの連続使用時間をチェック（簡易版）
     * 実際の連続使用時間の追跡には、より複雑な実装が必要
     */
    fun checkContinuousUsage(packageName: String, thresholdMinutes: Int): Boolean {
        val usageTime = getAppUsageTime(packageName)
        return usageTime > thresholdMinutes * 60 * 1000L
    }
}
