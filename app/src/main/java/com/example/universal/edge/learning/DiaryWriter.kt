package com.example.universal.edge.learning

import com.example.universal.edge.data.entity.AIDiaryEntry
import com.example.universal.edge.data.entity.InteractionLog
import com.example.universal.edge.data.entity.UserProfile
import com.example.universal.edge.inference.EmotionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI日記ライター: インタラクションログとプロファイルの変化から、
 * 人間が読める「AI視点の日記」を生成する。
 *
 * ConsolidationWorker が夜間に呼び出し、その日の学習内容を
 * ユーザーに分かりやすく伝える日記エントリを作成する。
 */
object DiaryWriter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * インタラクションログとプロファイルの変化から日記エントリを生成する。
     *
     * @param logs 統合対象のインタラクションログ
     * @param profileBefore 統合前のプロファイル
     * @param profileAfter 統合後のプロファイル
     * @return 日記エントリ
     */
    fun compose(
        logs: List<InteractionLog>,
        profileBefore: UserProfile,
        profileAfter: UserProfile
    ): AIDiaryEntry {
        val today = dateFormat.format(Date())
        val stats = computeStats(logs)
        val changes = computePersonalityChanges(profileBefore, profileAfter)
        val diaryText = buildDiaryText(stats, changes)

        return AIDiaryEntry(
            date = today,
            totalInteractions = logs.size,
            topEmotionType = stats.topEmotion?.name ?: "CALM",
            topEmotionSuccessRate = stats.topSuccessRate,
            personalityChanges = serializeChanges(changes),
            diaryText = diaryText,
            profileVersionBefore = profileBefore.version,
            profileVersionAfter = profileAfter.version
        )
    }

    private data class DailyStats(
        val totalCount: Int,
        val emotionCounts: Map<EmotionType, Int>,
        val emotionRewards: Map<EmotionType, Float>,    // 平均報酬
        val emotionSuccessRates: Map<EmotionType, Float>, // 正の報酬の割合
        val topEmotion: EmotionType?,
        val topSuccessRate: Float,
        val worstEmotion: EmotionType?,
        val avgReward: Float
    )

    private data class PersonalityChange(
        val type: EmotionType,
        val before: Float,  // α/(α+β) の期待値
        val after: Float,
        val delta: Float
    )

    private fun computeStats(logs: List<InteractionLog>): DailyStats {
        if (logs.isEmpty()) {
            return DailyStats(0, emptyMap(), emptyMap(), emptyMap(), null, 0f, null, 0f)
        }

        val emotionCounts = mutableMapOf<EmotionType, Int>()
        val emotionRewardSums = mutableMapOf<EmotionType, Float>()
        val emotionPositiveCounts = mutableMapOf<EmotionType, Int>()

        for (log in logs) {
            val type = EmotionType.fromIndex(log.actionIndex)
            emotionCounts[type] = (emotionCounts[type] ?: 0) + 1
            emotionRewardSums[type] = (emotionRewardSums[type] ?: 0f) + log.rewardScore
            if (log.rewardScore > 0) {
                emotionPositiveCounts[type] = (emotionPositiveCounts[type] ?: 0) + 1
            }
        }

        val emotionRewards = emotionRewardSums.mapValues { (type, sum) ->
            sum / (emotionCounts[type] ?: 1)
        }

        val emotionSuccessRates = emotionCounts.mapValues { (type, count) ->
            (emotionPositiveCounts[type] ?: 0).toFloat() / count
        }

        val topEntry = emotionSuccessRates
            .filter { (emotionCounts[it.key] ?: 0) >= 2 }
            .maxByOrNull { it.value }

        val worstEntry = emotionSuccessRates
            .filter { (emotionCounts[it.key] ?: 0) >= 2 }
            .minByOrNull { it.value }

        val avgReward = logs.map { it.rewardScore }.average().toFloat()

        return DailyStats(
            totalCount = logs.size,
            emotionCounts = emotionCounts,
            emotionRewards = emotionRewards,
            emotionSuccessRates = emotionSuccessRates,
            topEmotion = topEntry?.key,
            topSuccessRate = topEntry?.value ?: 0f,
            worstEmotion = worstEntry?.key,
            avgReward = avgReward
        )
    }

    private fun computePersonalityChanges(
        before: UserProfile,
        after: UserProfile
    ): List<PersonalityChange> {
        return EmotionType.entries.mapIndexed { i, type ->
            val alphaBefore = before.personalityAlpha.getOrElse(i) { 1f }
            val betaBefore = before.personalityBeta.getOrElse(i) { 1f }
            val alphaAfter = after.personalityAlpha.getOrElse(i) { 1f }
            val betaAfter = after.personalityBeta.getOrElse(i) { 1f }

            val valueBefore = alphaBefore / (alphaBefore + betaBefore)
            val valueAfter = alphaAfter / (alphaAfter + betaAfter)

            PersonalityChange(type, valueBefore, valueAfter, valueAfter - valueBefore)
        }
    }

    private fun buildDiaryText(stats: DailyStats, changes: List<PersonalityChange>): String {
        val sb = StringBuilder()

        // ヘッダー
        if (stats.totalCount == 0) {
            sb.appendLine("今日はやりとりがありませんでした。")
            sb.appendLine("明日はたくさんお話しできるといいな。")
            return sb.toString()
        }

        // 概要
        sb.appendLine("今日は${stats.totalCount}回やりとりしました。")

        // ベストな感情タイプ
        if (stats.topEmotion != null) {
            val count = stats.emotionCounts[stats.topEmotion] ?: 0
            val successPct = (stats.topSuccessRate * 100).toInt()
            sb.appendLine("あなたが一番喜んでくれたのは「${stats.topEmotion.label}」" +
                "(${count}回中${successPct}%で好反応)でした。")
        }

        // ワーストな感情タイプ
        if (stats.worstEmotion != null && stats.worstEmotion != stats.topEmotion) {
            val worstRate = (stats.emotionSuccessRates[stats.worstEmotion] ?: 0f) * 100
            if (worstRate < 40) {
                sb.appendLine("「${stats.worstEmotion.label}」は空振りが多かったので、少し控えめにしようと思います。")
            }
        }

        // 性格の変化グラフ
        sb.appendLine()
        sb.appendLine("--- 性格の変化 ---")

        val significantChanges = changes.filter { Math.abs(it.delta) > 0.01f }
            .sortedByDescending { Math.abs(it.delta) }

        if (significantChanges.isEmpty()) {
            sb.appendLine("今日は大きな性格の変化はありませんでした。")
        } else {
            for (change in significantChanges) {
                val bar = buildBar(change.after)
                val arrow = when {
                    change.delta > 0.05f -> "↑"
                    change.delta < -0.05f -> "↓"
                    change.delta > 0 -> "↗"
                    change.delta < 0 -> "↘"
                    else -> "→"
                }
                val label = change.type.label.padEnd(6, '　')
                sb.appendLine("$label $bar %.2f → %.2f ($arrow)".format(change.before, change.after))
            }
        }

        // 締めの言葉
        sb.appendLine()
        if (stats.avgReward > 0.3f) {
            sb.appendLine("今日はたくさん笑ってくれて嬉しかったです。明日もよろしくね。")
        } else if (stats.avgReward > 0f) {
            sb.appendLine("明日はもっとあなたのことを理解できるように頑張ります。")
        } else {
            sb.appendLine("今日はうまくいかないことが多かったけど、少しずつ学んでいます。明日も一緒にいてね。")
        }

        return sb.toString()
    }

    /** 0.0-1.0の値を10段階バーで表現 */
    private fun buildBar(value: Float): String {
        val filled = (value * 10).toInt().coerceIn(0, 10)
        return "■".repeat(filled) + "□".repeat(10 - filled)
    }

    /** 性格変化をJSON風文字列にシリアライズ */
    private fun serializeChanges(changes: List<PersonalityChange>): String {
        return changes.joinToString(";") { c ->
            "${c.type.name}:%.3f,%.3f".format(c.before, c.after)
        }
    }
}
