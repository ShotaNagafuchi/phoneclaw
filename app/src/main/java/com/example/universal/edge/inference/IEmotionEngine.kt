package com.example.universal.edge.inference

import com.example.universal.edge.data.entity.ContextSnapshot
import com.example.universal.edge.data.entity.UserProfile

/**
 * 推定層（頭脳）の契約。
 *
 * このインターフェースを切ることで、裏側の実装を自由に差し替え可能にする。
 * - RuleBasedEmotionEngine: LLMなしのフォールバック（v1）
 * - AICoreEmotionEngine: Gemini Nano (将来)
 * - MediaPipeEmotionEngine: Gemma 2B via MediaPipe LLM API (将来)
 */
interface IEmotionEngine {
    /**
     * 文脈とプロファイルからAIの感情/リアクションを推論する。
     *
     * @param context 現在の文脈ベクトル（80次元）
     * @param profile ユーザーの人格パラメータ（Thompson Samplingのα/β）
     * @return 推論結果
     */
    suspend fun infer(
        context: ContextSnapshot,
        profile: UserProfile
    ): EmotionOutput

    /** エンジンが推論可能な状態か */
    fun isReady(): Boolean

    /** エンジン名（デバッグ/ログ用） */
    val engineName: String
}
