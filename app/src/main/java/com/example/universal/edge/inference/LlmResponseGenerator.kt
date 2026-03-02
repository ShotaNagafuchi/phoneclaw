package com.example.universal.edge.inference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * MediaPipe Gemma 2B によるオンデバイスLLMテキスト生成。
 *
 * soul.md性格 + 感情 + 画面コンテキストからプロンプトを組み立て、
 * 日本語で一言を生成する。失敗時はEmotionResponseMapperにフォールバック。
 */
class LlmResponseGenerator(private val context: Context) : IResponseGenerator {

    override val generatorName = "GemmaLLM"

    private var llmInference: LlmInference? = null

    @Volatile
    private var initialized = false

    override fun isReady(): Boolean = initialized && llmInference != null

    /**
     * モデルを初期化する。成功時にtrueを返す。
     * IOスレッドで呼ぶこと。
     */
    fun initialize(): Boolean {
        if (initialized) return true
        if (!LlmModelManager.isModelReady(context)) {
            Log.w(TAG, "Model not ready")
            return false
        }

        return try {
            val modelPath = LlmModelManager.getModelPath(context).absolutePath
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(64)
                .setTopK(40)
                .setTemperature(0.8f)
                .setRandomSeed(System.currentTimeMillis().toInt())
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            initialized = true
            Log.i(TAG, "LLM initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "LLM initialization failed", e)
            false
        }
    }

    override suspend fun generateResponse(
        output: EmotionOutput,
        soulText: String?,
        screenContext: String?
    ): String {
        val inference = llmInference
        if (inference == null) {
            return EmotionResponseMapper.getResponse(output)
        }

        return try {
            val prompt = buildPrompt(output, soulText, screenContext)
            val raw = inference.generateResponse(prompt)
            sanitize(raw)
        } catch (e: Exception) {
            Log.w(TAG, "LLM generation failed, falling back", e)
            EmotionResponseMapper.getResponse(output)
        }
    }

    private fun buildPrompt(
        output: EmotionOutput,
        soulText: String?,
        screenContext: String?
    ): String {
        val sb = StringBuilder()

        if (soulText != null) {
            sb.appendLine("あなたの性格:")
            sb.appendLine(soulText.take(300))
            sb.appendLine()
        }

        if (screenContext != null && screenContext.isNotBlank()) {
            sb.appendLine("現在の画面: ${screenContext.take(200)}")
            sb.appendLine()
        }

        sb.appendLine("感情: ${output.selectedAction.type.label}")
        sb.appendLine("強度: ${(output.selectedAction.intensity * 100).toInt()}%")
        sb.appendLine()
        sb.appendLine("指示: 上記の性格と感情に基づいて日本語で一言（10文字以内）。絵文字不使用。句読点不要。")
        sb.appendLine("応答:")

        return sb.toString()
    }

    private fun sanitize(raw: String): String {
        val line = raw.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("応答") }
            ?: raw.trim()

        // 30文字制限、余分な記号削除
        return line
            .replace(Regex("[「」。、！？!?\\s]"), "")
            .take(30)
            .ifBlank { "ふーん" }
    }

    fun release() {
        try {
            llmInference?.close()
        } catch (_: Exception) {}
        llmInference = null
        initialized = false
        Log.d(TAG, "LLM released")
    }

    companion object {
        private const val TAG = "LlmResponseGenerator"
    }
}
