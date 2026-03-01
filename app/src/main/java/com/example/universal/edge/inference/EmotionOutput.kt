package com.example.universal.edge.inference

/**
 * AIが出力可能な感情/リアクションの種類。
 * Thompson Samplingのアーム（腕）に対応する。
 */
enum class EmotionType(val label: String) {
    EMPATHY("共感"),
    HUMOR("ユーモア"),
    SURPRISE("驚き"),
    CALM("落ち着き"),
    EXCITEMENT("興奮"),
    CONCERN("心配"),
    ENCOURAGEMENT("励まし"),
    CURIOSITY("好奇心");

    companion object {
        fun fromIndex(index: Int): EmotionType =
            entries.getOrElse(index) { CALM }

        const val COUNT = 8
    }
}

/**
 * アクション候補: 感情タイプ + 強度
 */
data class EmotionAction(
    val type: EmotionType,
    val intensity: Float = 0.5f
) {
    init {
        require(intensity in 0.0f..1.0f) { "intensity must be 0.0-1.0" }
    }
}

/**
 * 推論結果: 選択されたアクションとメタデータ
 */
data class EmotionOutput(
    val selectedAction: EmotionAction,
    val confidence: Float,
    val reasoning: String? = null,
    val fallbackUsed: Boolean = false
)
