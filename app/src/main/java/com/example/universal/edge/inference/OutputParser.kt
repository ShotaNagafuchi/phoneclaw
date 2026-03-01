package com.example.universal.edge.inference

import android.util.Log

/**
 * LLM出力の安全弁パーサー。
 *
 * 軽量LLMはJSONフォーマットを頻繁に崩すため、以下の段階的フォールバックで
 * 必ず有効なEmotionOutputを返すことを保証する。
 *
 * 1. JSON抽出 → Gsonパース
 * 2. テキストマッチ（EmotionType名を探す）
 * 3. 前回の出力を維持
 * 4. デフォルト（CALM, 0.5）
 */
object OutputParser {
    private const val TAG = "OutputParser"

    private val JSON_PATTERN = Regex("""\{[^{}]*\}""")
    private val EMOTION_PATTERN = Regex(
        EmotionType.entries.joinToString("|") { it.name },
        RegexOption.IGNORE_CASE
    )
    private val INTENSITY_PATTERN = Regex("""(?:intensity|強度)["\s:]*([0-9]*\.?[0-9]+)""")

    private var lastOutput: EmotionOutput? = null

    fun parse(rawOutput: String): EmotionOutput {
        // Stage 1: JSONからパース
        val jsonResult = tryParseJson(rawOutput)
        if (jsonResult != null) {
            lastOutput = jsonResult
            return jsonResult
        }

        // Stage 2: テキストマッチ
        val textResult = tryParseText(rawOutput)
        if (textResult != null) {
            lastOutput = textResult
            return textResult
        }

        // Stage 3: 前回の出力を維持
        lastOutput?.let {
            Log.w(TAG, "Parse failed, using last output: ${it.selectedAction.type}")
            return it.copy(fallbackUsed = true)
        }

        // Stage 4: デフォルト
        Log.w(TAG, "Parse failed completely, using default CALM")
        return EmotionOutput(
            selectedAction = EmotionAction(EmotionType.CALM, 0.5f),
            confidence = 0.0f,
            reasoning = "fallback: parse failed",
            fallbackUsed = true
        )
    }

    private fun tryParseJson(raw: String): EmotionOutput? {
        return try {
            val jsonMatch = JSON_PATTERN.find(raw)?.value ?: return null
            val emotionMatch = EMOTION_PATTERN.find(jsonMatch) ?: return null
            val type = EmotionType.valueOf(emotionMatch.value.uppercase())
            val intensity = INTENSITY_PATTERN.find(jsonMatch)
                ?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 1f)
                ?: 0.5f

            EmotionOutput(
                selectedAction = EmotionAction(type, intensity),
                confidence = 0.8f,
                reasoning = jsonMatch
            )
        } catch (e: Exception) {
            Log.d(TAG, "JSON parse failed: ${e.message}")
            null
        }
    }

    private fun tryParseText(raw: String): EmotionOutput? {
        val emotionMatch = EMOTION_PATTERN.find(raw) ?: return null
        return try {
            val type = EmotionType.valueOf(emotionMatch.value.uppercase())
            val intensity = INTENSITY_PATTERN.find(raw)
                ?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 1f)
                ?: 0.5f

            EmotionOutput(
                selectedAction = EmotionAction(type, intensity),
                confidence = 0.5f,
                reasoning = "text-match: ${emotionMatch.value}",
                fallbackUsed = true
            )
        } catch (e: Exception) {
            null
        }
    }
}
