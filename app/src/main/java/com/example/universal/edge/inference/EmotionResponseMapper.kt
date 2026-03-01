package com.example.universal.edge.inference

import java.util.Calendar

/**
 * EmotionType → 短い日本語フレーズ変換。
 * 外部LLMなし。テンプレートベースでintensityに応じた候補選択。
 */
object EmotionResponseMapper {

    private val phrases: Map<EmotionType, List<String>> = mapOf(
        EmotionType.EMPATHY to listOf(
            "うんうん",
            "わかる",
            "だよね",
            "それな"
        ),
        EmotionType.HUMOR to listOf(
            "ふふ",
            "なんかウケる",
            "草",
            "今日も元気だね"
        ),
        EmotionType.SURPRISE to listOf(
            "お",
            "おお",
            "え、マジ？",
            "うそ"
        ),
        EmotionType.CALM to listOf(
            "ふむ",
            "まあまあ",
            "ゆっくりね",
            "大丈夫"
        ),
        EmotionType.EXCITEMENT to listOf(
            "お",
            "いいじゃん",
            "やるね！",
            "今日やるぞ"
        ),
        EmotionType.CONCERN to listOf(
            "ん？",
            "大丈夫？",
            "無理しないで",
            "ちょっと心配"
        ),
        EmotionType.ENCOURAGEMENT to listOf(
            "いける",
            "がんばれ",
            "できるよ",
            "その調子"
        ),
        EmotionType.CURIOSITY to listOf(
            "へえ",
            "なるほど",
            "面白い",
            "それで？"
        )
    )

    /** 朝の挨拶（初回ルール以外で朝に呼ばれた場合用） */
    private val morningGreetings = listOf("おはよ", "朝だね", "いい朝")
    private val eveningGreetings = listOf("おつかれ", "夜だね", "ゆっくりね")

    /**
     * EmotionOutputから日本語テキストを生成。
     * intensityが高いほど後半（より強い表現）の候補を選ぶ。
     */
    fun getResponse(output: EmotionOutput): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 朝5-9時 or 夜22-4時は時間帯挨拶を混ぜる（30%の確率）
        if (Math.random() < 0.3) {
            when {
                hour in 5..9 -> return morningGreetings.random()
                hour >= 22 || hour < 4 -> return eveningGreetings.random()
            }
        }

        val candidates = phrases[output.selectedAction.type] ?: return "ふむ"
        val index = (output.selectedAction.intensity * (candidates.size - 1))
            .toInt()
            .coerceIn(0, candidates.lastIndex)
        return candidates[index]
    }
}
