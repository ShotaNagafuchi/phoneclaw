package com.example.universal.edge.autonomous

import android.content.Context
import android.util.Log
import com.example.universal.edge.EdgeAIManager
import com.example.universal.edge.SoulManager
import com.example.universal.edge.inference.LlmModelManager

/**
 * LLMベース行動計画。
 *
 * LLM利用可能時: 画面テキスト + soul.md から構造化アクションを生成
 * LLM不可時: ルールベースのシンプルな行動（スクロール、ホーム遷移等）
 */
class LlmActionPlanner(private val context: Context) {

    companion object {
        private const val TAG = "LlmActionPlanner"
    }

    suspend fun plan(observation: AutonomousAgent.ScreenObservation): AgentAction {
        // LLM利用可能時はLLMで判断
        if (LlmModelManager.isModelReady(context) && EdgeAIManager.instance?.isGeneratorReady() == true) {
            return planWithLlm(observation)
        }
        // フォールバック: ルールベース
        return planWithRules(observation)
    }

    private suspend fun planWithLlm(observation: AutonomousAgent.ScreenObservation): AgentAction {
        return try {
            val soul = try { SoulManager.getSoul(context) } catch (_: Exception) { null }
            val prompt = buildPrompt(observation, soul)
            val response = EdgeAIManager.instance?.generateTextResponse(
                observation.emotionOutput ?: return planWithRules(observation),
                context
            ) ?: return planWithRules(observation)

            // LLMテキスト応答をアクションに変換
            parseAction(response)
        } catch (e: Exception) {
            Log.w(TAG, "LLM planning failed", e)
            planWithRules(observation)
        }
    }

    private fun buildPrompt(
        observation: AutonomousAgent.ScreenObservation,
        soul: String?
    ): String {
        val sb = StringBuilder()
        if (soul != null) {
            sb.appendLine("性格: ${soul.take(200)}")
        }
        sb.appendLine("現在のアプリ: ${observation.packageName ?: "不明"}")
        sb.appendLine("画面テキスト: ${observation.screenText?.take(500) ?: "取得不可"}")
        sb.appendLine()
        sb.appendLine("以下から一つ選択:")
        sb.appendLine("- TAP:<テキスト>")
        sb.appendLine("- SWIPE:<UP/DOWN/LEFT/RIGHT>")
        sb.appendLine("- OPEN:<パッケージ名>")
        sb.appendLine("- TYPE:<テキスト>")
        sb.appendLine("- SPEAK:<一言>")
        sb.appendLine("- BACK")
        sb.appendLine("- HOME")
        sb.appendLine("- OBSERVE")
        sb.appendLine()
        sb.appendLine("アクション:")
        return sb.toString()
    }

    private fun parseAction(response: String): AgentAction {
        val line = response.trim().lines().firstOrNull()?.trim() ?: return AgentAction.Observe

        return when {
            line.startsWith("TAP:", ignoreCase = true) ->
                AgentAction.TapByText(line.substringAfter(":").trim())
            line.startsWith("SWIPE:", ignoreCase = true) -> {
                val dir = line.substringAfter(":").trim().uppercase()
                val swipeDir = when (dir) {
                    "UP" -> SwipeDirection.UP
                    "DOWN" -> SwipeDirection.DOWN
                    "LEFT" -> SwipeDirection.LEFT
                    "RIGHT" -> SwipeDirection.RIGHT
                    else -> SwipeDirection.DOWN
                }
                AgentAction.Swipe(swipeDir)
            }
            line.startsWith("OPEN:", ignoreCase = true) ->
                AgentAction.OpenApp(line.substringAfter(":").trim())
            line.startsWith("TYPE:", ignoreCase = true) ->
                AgentAction.TypeText(line.substringAfter(":").trim())
            line.startsWith("SPEAK:", ignoreCase = true) ->
                AgentAction.Speak(line.substringAfter(":").trim())
            line.equals("BACK", ignoreCase = true) -> AgentAction.GoBack
            line.equals("HOME", ignoreCase = true) -> AgentAction.GoHome
            else -> AgentAction.Observe
        }
    }

    /**
     * ルールベースフォールバック。
     * LLM不可時のシンプルな行動パターン。
     */
    private fun planWithRules(observation: AutonomousAgent.ScreenObservation): AgentAction {
        val screenText = observation.screenText

        // 画面テキストがない → 何もしない
        if (screenText.isNullOrBlank()) {
            return AgentAction.Observe
        }

        // 興味に基づく簡易ルール
        val emotion = observation.emotionOutput?.selectedAction?.type

        return when {
            // CURIOSITY状態 → スクロールして情報を探す
            emotion == com.example.universal.edge.inference.EmotionType.CURIOSITY ->
                AgentAction.Swipe(SwipeDirection.DOWN)
            // EXCITEMENT状態 → ランダムなタップを試行（ボタンらしいテキスト）
            emotion == com.example.universal.edge.inference.EmotionType.EXCITEMENT -> {
                val buttons = listOf("開く", "次へ", "OK", "続ける", "見る", "再生")
                val found = buttons.firstOrNull { screenText.contains(it) }
                if (found != null) AgentAction.TapByText(found) else AgentAction.Swipe(SwipeDirection.DOWN)
            }
            // デフォルト: 観察
            else -> AgentAction.Observe
        }
    }
}
