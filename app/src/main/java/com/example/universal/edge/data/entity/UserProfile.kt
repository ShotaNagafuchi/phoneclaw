package com.example.universal.edge.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ユーザープロファイル: AIの「人格」を表す長期記憶。
 *
 * Thompson Samplingの各EmotionTypeに対するBeta分布パラメータ(α/β)と、
 * 文脈別のバイアス補正値を保持する。
 *
 * personalityAlpha/Beta: 8種のEmotionTypeそれぞれのBeta(α,β)分布パラメータ
 *   → αが大きい = そのアクションが過去に報酬を得た回数が多い
 *   → サンプリング時にそのアクションが選ばれやすくなる
 *
 * contextBias: 文脈カテゴリ(8) × 2(強度/方向) = 16次元
 *   → 特定の文脈で特定アクションの選好を補正
 */
@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: String = "default",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Beta分布パラメータ (各EmotionTypeに1つ)
    val personalityAlpha: FloatArray = FloatArray(8) { 1.0f },
    val personalityBeta: FloatArray = FloatArray(8) { 1.0f },

    // 文脈別バイアス補正
    val contextBias: FloatArray = FloatArray(16) { 0.0f },

    // 統計
    val totalInteractions: Long = 0,
    val totalConsolidations: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserProfile) return false
        return userId == other.userId
    }

    override fun hashCode(): Int = userId.hashCode()
}
