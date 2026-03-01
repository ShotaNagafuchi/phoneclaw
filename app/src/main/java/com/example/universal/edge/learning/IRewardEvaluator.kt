package com.example.universal.edge.learning

/**
 * 報酬評価の契約。
 *
 * AIがリアクションした後のユーザーの反応を評価し、
 * 報酬シグナルを返す。実装によってセンサーが異なる:
 *
 * - FaceRewardEvaluator: MediaPipe FaceMeshでカメラから表情を検知
 * - (将来) AudioRewardEvaluator: 声のトーンから評価
 * - (将来) InteractionRewardEvaluator: タップ/スワイプパターンから評価
 */
interface IRewardEvaluator {
    /**
     * ユーザーの反応を評価してスコアを返す。
     * AIがリアクションした直後に呼び出す。
     *
     * @param durationMs 評価期間（ミリ秒）。この間ユーザーの反応を観察する
     * @return 報酬シグナル
     */
    suspend fun evaluate(durationMs: Long = 3000L): RewardSignal

    /** 評価に必要なリソースを準備（カメラ起動等） */
    suspend fun prepare()

    /** リソース解放 */
    fun release()

    /** 評価可能な状態か */
    fun isAvailable(): Boolean
}

/**
 * 報酬シグナル: ユーザーの反応を数値化したもの。
 *
 * @param score 報酬スコア (-1.0 ~ +1.0)。正=好反応、負=悪反応
 * @param confidence 評価の確信度 (0.0 ~ 1.0)。低い場合はログに記録するが学習に使わない
 * @param rawFeatures 生の特徴量（ログ用、デバッグ用）
 */
data class RewardSignal(
    val score: Float,
    val confidence: Float,
    val rawFeatures: FloatArray = FloatArray(0)
) {
    /** 確信度がしきい値以上の場合のみ有効とみなす */
    fun isReliable(threshold: Float = 0.3f): Boolean = confidence >= threshold

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RewardSignal) return false
        return score == other.score && confidence == other.confidence
    }

    override fun hashCode(): Int = 31 * score.hashCode() + confidence.hashCode()
}
