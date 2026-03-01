package com.example.universal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.universal.edge.EdgeAIManager
import com.example.universal.edge.inference.EmotionOutput
import java.util.Calendar

/**
 * ルールエンジン: MVPではKotlinのwhen式でハードコード
 * 後でYAML/JSON読み込みに拡張可能な構造
 */
class RuleEngine(private val context: Context) {
    companion object {
        private const val TAG = "RuleEngine"
    }

    /**
     * ルールを評価し、実行すべきアクションを返す
     */
    fun evaluateRules(): RuleAction? {
        // ルール1: 当日最初のロック解除
        if (shouldTriggerFirstUnlockRule()) {
            return RuleAction.TTS(context.getString(R.string.rule_first_unlock_tts))
        }

        // ルール2: Wi-Fi未接続
        if (shouldTriggerWifiRule()) {
            return RuleAction.Notification(
                title = context.getString(R.string.rule_wifi_off_title),
                message = context.getString(R.string.rule_wifi_off_message)
            )
        }

        // ルール3: 特定アプリ連続使用が閾値超え（後で実装）
        // UsageTrackerと連携して実装

        return null
    }

    /**
     * ルール1: 当日最初のロック解除かどうか
     */
    private fun shouldTriggerFirstUnlockRule(): Boolean {
        val buddyService = BuddyService.instance
        return buddyService?.isFirstUnlockToday() == true
    }

    /**
     * ルール2: Wi-Fi未接続かどうか
     */
    private fun shouldTriggerWifiRule(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return true // ネットワークがない
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true

        // Wi-Fiに接続されていない場合
        return !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Edge AIリアクションを要求する。
     * EdgeAIManagerが初期化済みかつ準備完了の場合のみ実行可能。
     */
    suspend fun evaluateEdgeAIReaction(): RuleAction? {
        val edgeAI = EdgeAIManager.instance ?: return null
        if (!edgeAI.isEngineReady()) return null

        return try {
            val output = edgeAI.reactAndLearn()
            RuleAction.EdgeAIReaction(output)
        } catch (e: Exception) {
            Log.e(TAG, "EdgeAI reaction failed: ${e.message}")
            null
        }
    }

    /**
     * ルール3: 特定アプリの連続使用時間が閾値を超えているか
     * UsageTrackerと連携して実装
     */
    fun shouldTriggerAppUsageRule(packageName: String, thresholdMinutes: Int): Boolean {
        val usageTracker = UsageTracker.instance
        val usageTime = usageTracker?.getAppUsageTime(packageName) ?: 0L
        return usageTime > thresholdMinutes * 60 * 1000L // ミリ秒に変換
    }
}

/**
 * ルール実行アクション
 */
sealed class RuleAction {
    data class TTS(val message: String) : RuleAction()
    data class Notification(val title: String, val message: String) : RuleAction()
    data class Action(val actionType: String, val params: Map<String, Any> = emptyMap()) : RuleAction()
    data class EdgeAIReaction(val output: EmotionOutput) : RuleAction()
    data class BrowserAction(val url: String, val action: String = "navigate") : RuleAction()
}
