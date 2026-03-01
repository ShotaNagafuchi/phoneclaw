package com.example.universal.edge.learning

import com.example.universal.edge.data.entity.ContextSnapshot
import com.example.universal.edge.data.entity.UserProfile
import com.example.universal.edge.inference.EmotionType
import java.util.Random

/**
 * Thompson Sampling Contextual Bandit.
 *
 * 各EmotionType（アーム）に対してBeta分布を維持し、
 * サンプリングによって探索と活用を自然にバランスさせる。
 *
 * 仕組み:
 * - 各EmotionType i に対し Beta(α_i, β_i) を維持
 * - 推論時: 各分布からサンプリング → 最大値のアクションを選択
 * - 報酬後: reward > 0 なら α_i += reward, そうでなければ β_i += |reward|
 *
 * 文脈依存: contextBiasを使って文脈カテゴリに応じた補正を行う。
 *
 * 参考:
 * - Chapelle & Li (2011): "An Empirical Evaluation of Thompson Sampling"
 * - HeartSteps (Harvard): mHealthでのmicro-randomization成功事例
 */
class ThompsonSamplingBandit(
    private val random: Random = Random()
) {
    /**
     * Beta分布からサンプリングしてアクションを選択する。
     *
     * @return 選択されたアクションのインデックス (0..7)
     */
    fun selectAction(profile: UserProfile, context: ContextSnapshot): Int {
        val samples = FloatArray(EmotionType.COUNT)

        for (i in 0 until EmotionType.COUNT) {
            val alpha = profile.personalityAlpha.getOrElse(i) { 1f }.toDouble()
                .coerceAtLeast(0.01)
            val beta = profile.personalityBeta.getOrElse(i) { 1f }.toDouble()
                .coerceAtLeast(0.01)

            // Beta分布からサンプリング (Gamma分布の比として実装)
            val sample = sampleBeta(alpha, beta)

            // 文脈バイアス補正
            val biasIndex = (i * 2).coerceAtMost(profile.contextBias.size - 1)
            val bias = if (biasIndex < profile.contextBias.size) {
                profile.contextBias[biasIndex]
            } else 0f

            samples[i] = (sample + bias).toFloat()
        }

        return samples.indices.maxByOrNull { samples[it] } ?: 0
    }

    /**
     * 報酬を受けてプロファイルのα/βを更新する。
     *
     * @param profile 現在のプロファイル
     * @param actionIndex 実行したアクションのインデックス
     * @param reward 報酬スコア (-1.0 ~ +1.0)
     * @return 更新されたプロファイル
     */
    fun updateFromReward(
        profile: UserProfile,
        actionIndex: Int,
        reward: Float
    ): UserProfile {
        val newAlpha = profile.personalityAlpha.copyOf()
        val newBeta = profile.personalityBeta.copyOf()

        if (actionIndex in 0 until EmotionType.COUNT) {
            if (reward > 0) {
                // 正の報酬 → α増加（成功回数）
                newAlpha[actionIndex] += reward
            } else {
                // 負の報酬 → β増加（失敗回数）
                newBeta[actionIndex] += (-reward)
            }

            // α,βが大きくなりすぎないよう定期的にスケールダウン
            // （最近の経験を重視する効果もある）
            val sum = newAlpha[actionIndex] + newBeta[actionIndex]
            if (sum > MAX_PARAMETER_SUM) {
                val scale = MAX_PARAMETER_SUM / sum
                newAlpha[actionIndex] *= scale
                newBeta[actionIndex] *= scale
            }
        }

        return profile.copy(
            personalityAlpha = newAlpha,
            personalityBeta = newBeta,
            totalInteractions = profile.totalInteractions + 1,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 統合時のバッチ更新: 複数のログをまとめてα/βに反映する。
     */
    fun consolidate(
        profile: UserProfile,
        actionRewards: Map<Int, List<Float>>
    ): UserProfile {
        val newAlpha = profile.personalityAlpha.copyOf()
        val newBeta = profile.personalityBeta.copyOf()

        for ((actionIndex, rewards) in actionRewards) {
            if (actionIndex !in 0 until EmotionType.COUNT) continue

            val positiveSum = rewards.filter { it > 0 }.sum()
            val negativeSum = rewards.filter { it < 0 }.map { -it }.sum()

            newAlpha[actionIndex] += positiveSum
            newBeta[actionIndex] += negativeSum

            // スケールダウン
            val sum = newAlpha[actionIndex] + newBeta[actionIndex]
            if (sum > MAX_PARAMETER_SUM) {
                val scale = MAX_PARAMETER_SUM / sum
                newAlpha[actionIndex] *= scale
                newBeta[actionIndex] *= scale
            }
        }

        return profile.copy(
            personalityAlpha = newAlpha,
            personalityBeta = newBeta,
            totalConsolidations = profile.totalConsolidations + 1,
            version = profile.version + 1,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Beta(α, β) 分布からのサンプリング。
     * Gamma分布の比 X/(X+Y) として実装（X ~ Gamma(α), Y ~ Gamma(β)）。
     */
    private fun sampleBeta(alpha: Double, beta: Double): Double {
        val x = sampleGamma(alpha)
        val y = sampleGamma(beta)
        return if (x + y > 0) x / (x + y) else 0.5
    }

    /**
     * Gamma(shape, 1) 分布からのサンプリング。
     * Marsaglia & Tsang (2000) の方法。
     */
    private fun sampleGamma(shape: Double): Double {
        if (shape < 1.0) {
            // shape < 1 の場合、shape+1のGammaを生成して補正
            val u = random.nextDouble()
            return sampleGamma(shape + 1.0) * Math.pow(u, 1.0 / shape)
        }

        val d = shape - 1.0 / 3.0
        val c = 1.0 / Math.sqrt(9.0 * d)

        while (true) {
            var x: Double
            var v: Double
            do {
                x = random.nextGaussian()
                v = 1.0 + c * x
            } while (v <= 0)

            v = v * v * v
            val u = random.nextDouble()

            if (u < 1 - 0.0331 * (x * x) * (x * x)) return d * v
            if (Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) return d * v
        }
    }

    companion object {
        // α+βの上限（これを超えるとスケールダウン → 最近の学習を重視）
        private const val MAX_PARAMETER_SUM = 100f
    }
}
