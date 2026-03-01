package com.example.universal.edge.learning

import android.util.Log
import com.example.universal.edge.data.IContextRepository
import com.example.universal.edge.data.entity.InteractionLog
import com.example.universal.edge.inference.EmotionOutput

/**
 * 学習層のオーケストレーター。
 *
 * AIがリアクション → ユーザーの反応を評価 → ログ記録 → パラメータ即時更新
 * の一連のフローを管理する。
 *
 * 学習は2段階で行われる:
 * 1. リアルタイム更新: 各インタラクション後に即座にα/βを微調整
 * 2. 夜間統合 (MemoryConsolidationWorker): ログを集約してバッチ更新
 */
class LearningOrchestrator(
    private val repository: IContextRepository,
    private val rewardEvaluator: IRewardEvaluator,
    private val bandit: ThompsonSamplingBandit
) {
    companion object {
        private const val TAG = "LearningOrchestrator"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.3f
    }

    /**
     * AIリアクション後の学習サイクル。
     *
     * 1. rewardEvaluatorでユーザーの反応を評価
     * 2. 確信度がしきい値以上ならログ記録 & パラメータ更新
     * 3. そうでなければログのみ記録（統合時に再評価可能）
     */
    suspend fun learnFromReaction(
        emotionOutput: EmotionOutput,
        evaluationDurationMs: Long = 3000L
    ) {
        try {
            val reward = rewardEvaluator.evaluate(evaluationDurationMs)

            val context = repository.getCurrentContext()

            // ログ記録（報酬の確信度に関わらず）
            val log = InteractionLog(
                contextVector = context.toFullVector(),
                actionIndex = emotionOutput.selectedAction.type.ordinal,
                actionIntensity = emotionOutput.selectedAction.intensity,
                rewardScore = reward.score,
                rewardConfidence = reward.confidence
            )
            repository.logInteraction(log)

            // 確信度が高い場合のみリアルタイム更新
            if (reward.isReliable(MIN_CONFIDENCE_THRESHOLD)) {
                val currentProfile = repository.getUserProfile()
                val updatedProfile = bandit.updateFromReward(
                    currentProfile,
                    emotionOutput.selectedAction.type.ordinal,
                    reward.score
                )
                repository.updateProfile(updatedProfile)

                Log.d(TAG, "Learned: ${emotionOutput.selectedAction.type.name} " +
                    "reward=${reward.score} confidence=${reward.confidence}")
            } else {
                Log.d(TAG, "Low confidence (${reward.confidence}), logged but not updated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Learning failed: ${e.message}")
        }
    }
}
