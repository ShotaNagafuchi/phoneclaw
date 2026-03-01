package com.example.universal.edge.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.universal.edge.inference.EmotionType

/**
 * AI日記エントリ: AIが「今日何を見て、何を学んだか」を人間が読める形で記録する。
 *
 * MemoryConsolidationWorker の実行時（充電中の夜間）に自動生成され、
 * ユーザーがアプリのメモから閲覧できる。
 *
 * 日記はAIの内部状態を「見える化」し、ユーザーとの信頼関係を構築する。
 *
 * 例:
 * ```
 * 📅 2026-03-01 の日記
 *
 * 今日は12回やりとりしました。
 * あなたが一番喜んでくれたのは「ユーモア」(7回中5回で笑顔😊)でした。
 * 逆に「心配」は空振りが多かったので、少し控えめにしようと思います。
 *
 * 【性格の変化】
 * ユーモア: ■■■■■■■□□□ 0.42 → 0.58 (↑大きく成長)
 * 共感:     ■■■■■□□□□□ 0.50 → 0.52
 * 心配:     ■■■□□□□□□□ 0.45 → 0.35 (↓少し控えめに)
 *
 * 明日も一緒に過ごせるのを楽しみにしています。
 * ```
 */
@Entity(tableName = "ai_diary_entries")
data class AIDiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,              // "2026-03-01"
    val createdAt: Long = System.currentTimeMillis(),

    // 統計サマリー
    val totalInteractions: Int,
    val topEmotionType: String,    // EmotionType.name
    val topEmotionSuccessRate: Float,

    // 人格パラメータの変化 (JSON形式: {"EMPATHY": [0.5, 0.52], "HUMOR": [0.42, 0.58], ...})
    val personalityChanges: String,

    // 生成された日記テキスト
    val diaryText: String,

    // メタデータ
    val profileVersionBefore: Int,
    val profileVersionAfter: Int
)
