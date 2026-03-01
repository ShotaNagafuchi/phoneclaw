package com.example.universal.edge.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 文脈スナップショット: 推論時の「今この瞬間」を80次元ベクトルで表現。
 *
 * contentVector (32): コンテンツ文脈 [カテゴリ埋め込み(16) + 感情極性(8) + トピック(8)]
 * userStateVector (16): ユーザー状態 [時刻sin/cos(2) + 曜日(7) + デバイス状態(3) + 直近ムード(4)]
 * externalVector (32): 外部文脈 [天気(4) + ニュースセンチメント(8) + アプリ使用(16) + 予備(4)]
 */
@Entity(tableName = "context_snapshots")
data class ContextSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val contentVector: FloatArray = FloatArray(32),
    val userStateVector: FloatArray = FloatArray(16),
    val externalVector: FloatArray = FloatArray(32)
) {
    fun toFullVector(): FloatArray =
        contentVector + userStateVector + externalVector

    companion object {
        const val CONTENT_DIM = 32
        const val USER_STATE_DIM = 16
        const val EXTERNAL_DIM = 32
        const val TOTAL_DIM = CONTENT_DIM + USER_STATE_DIM + EXTERNAL_DIM

        fun fromTimeOfDay(): ContextSnapshot {
            val cal = java.util.Calendar.getInstance()
            val hourAngle = (cal.get(java.util.Calendar.HOUR_OF_DAY) / 24.0 * 2 * Math.PI)
            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)

            val userState = FloatArray(USER_STATE_DIM)
            userState[0] = Math.sin(hourAngle).toFloat()
            userState[1] = Math.cos(hourAngle).toFloat()
            if (dayOfWeek in 1..7) userState[1 + dayOfWeek] = 1.0f

            return ContextSnapshot(userStateVector = userState)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContextSnapshot) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
