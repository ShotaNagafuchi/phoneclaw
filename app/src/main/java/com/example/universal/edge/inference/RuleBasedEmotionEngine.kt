package com.example.universal.edge.inference

import com.example.universal.edge.data.entity.ContextSnapshot
import com.example.universal.edge.data.entity.UserProfile
import com.example.universal.edge.learning.ThompsonSamplingBandit

/**
 * LLMを使わないルールベース推定エンジン（v1フォールバック実装）。
 *
 * Thompson Samplingで最適なEmotionTypeを選択し、
 * 文脈ベクトルの特徴量からintensityをルールで決定する。
 *
 * 将来AICoreEmotionEngineやMediaPipeEmotionEngineに差し替え可能。
 */
class RuleBasedEmotionEngine(
    private val bandit: ThompsonSamplingBandit
) : IEmotionEngine {

    override val engineName: String = "RuleBasedEngine"

    override fun isReady(): Boolean = true

    override suspend fun infer(
        context: ContextSnapshot,
        profile: UserProfile
    ): EmotionOutput {
        // Thompson Samplingでアクション選択
        val selectedIndex = bandit.selectAction(profile, context)
        val emotionType = EmotionType.fromIndex(selectedIndex)

        // 文脈からintensityをルールで決定
        val intensity = computeIntensity(context, emotionType)

        return EmotionOutput(
            selectedAction = EmotionAction(emotionType, intensity),
            confidence = computeConfidence(profile, selectedIndex),
            reasoning = "rule-based: bandit selected ${emotionType.name}"
        )
    }

    /**
     * 文脈ベクトルの特徴量からintensityを計算。
     * userStateVector[0..1]は時刻のsin/cos → 深夜帯は控えめ。
     */
    private fun computeIntensity(context: ContextSnapshot, type: EmotionType): Float {
        val hourSin = context.userStateVector.getOrElse(0) { 0f }
        val hourCos = context.userStateVector.getOrElse(1) { 0f }

        // 深夜(sin≈0, cos≈1)はintensityを下げる
        val timeModifier = (0.5f + hourSin * 0.3f).coerceIn(0.2f, 0.9f)

        // EmotionType別のベース強度
        val baseIntensity = when (type) {
            EmotionType.CALM -> 0.4f
            EmotionType.EXCITEMENT -> 0.7f
            EmotionType.HUMOR -> 0.6f
            EmotionType.EMPATHY -> 0.6f
            EmotionType.SURPRISE -> 0.5f
            EmotionType.CONCERN -> 0.5f
            EmotionType.ENCOURAGEMENT -> 0.6f
            EmotionType.CURIOSITY -> 0.5f
        }

        return (baseIntensity * timeModifier).coerceIn(0.1f, 1.0f)
    }

    /**
     * プロファイルのα/β値から推論の確信度を計算。
     * α/(α+β) のBeta分布の期待値をそのまま使う。
     */
    private fun computeConfidence(profile: UserProfile, actionIndex: Int): Float {
        val alpha = profile.personalityAlpha.getOrElse(actionIndex) { 1f }
        val beta = profile.personalityBeta.getOrElse(actionIndex) { 1f }
        return alpha / (alpha + beta)
    }
}
