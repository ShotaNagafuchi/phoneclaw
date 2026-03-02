package com.example.universal.edge.inference

/**
 * 感情テキスト生成の契約。
 * EmotionOutput(感情+強度) → 日本語テキストに変換する。
 *
 * 実装:
 * - SoulAwareResponseGenerator: soul.md対応テンプレート（フォールバック）
 * - LlmResponseGenerator: MediaPipe Gemma 2B on-device LLM（将来）
 */
interface IResponseGenerator {
    /**
     * 感情出力からテキスト応答を生成。
     *
     * @param output 感情推論結果
     * @param soulText soul.mdの全文（性格設定）
     * @param screenContext 現在の画面テキスト（自律行動時に利用）
     */
    suspend fun generateResponse(
        output: EmotionOutput,
        soulText: String?,
        screenContext: String? = null
    ): String

    fun isReady(): Boolean
    val generatorName: String
}
