package com.example.universal.edge.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 短期記憶ログ: ユーザーが強い反応を示した瞬間のみ記録される。
 *
 * 1レコード ≈ 500バイト。上限1000件でconsolidation対象。
 * consolidation後は削除され、集約結果がUserProfileに反映される。
 *
 * 例: ユーザーが3秒以上笑った時:
 *   contextVector = [悲しいニュースの文脈ベクトル]
 *   actionIndex = 0 (EMPATHY)
 *   actionIntensity = 0.8
 *   rewardScore = +0.7 (笑顔検出)
 */
@Entity(tableName = "interaction_logs")
data class InteractionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val contextVector: FloatArray,
    val actionIndex: Int,
    val actionIntensity: Float,
    val rewardScore: Float,
    val rewardConfidence: Float,
    val consolidated: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InteractionLog) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
