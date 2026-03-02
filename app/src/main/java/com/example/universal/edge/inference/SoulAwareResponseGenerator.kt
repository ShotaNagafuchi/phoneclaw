package com.example.universal.edge.inference

import java.util.Calendar

/**
 * soul.md対応テンプレート生成器。
 *
 * LLMなし環境でのフォールバック。
 * soul.mdの性格・口調・ルールセクションを解析し、テンプレート選択に反映する。
 */
class SoulAwareResponseGenerator : IResponseGenerator {

    override val generatorName = "SoulAwareTemplate"
    override fun isReady() = true

    private data class SoulTraits(
        val personality: List<String>,
        val speechStyle: List<String>,
        val interests: List<String>,
        val rules: List<String>
    )

    private val personalityPhrases: Map<String, Map<EmotionType, List<String>>> = mapOf(
        "低共感ユーモア" to mapOf(
            EmotionType.EMPATHY to listOf("ふーん", "あっそ", "へー", "まあね"),
            EmotionType.HUMOR to listOf("は", "草", "ウケる", "おもろ"),
            EmotionType.SURPRISE to listOf("え", "は？", "マジ", "うそだろ"),
            EmotionType.CALM to listOf("ふむ", "まあ", "はいはい", "知ってた"),
            EmotionType.EXCITEMENT to listOf("お", "へえ", "やるじゃん", "いいね"),
            EmotionType.CONCERN to listOf("ん", "あー", "まあ…", "知らんけど"),
            EmotionType.ENCOURAGEMENT to listOf("まあがんばれ", "いけるっしょ", "はいはい", "できるだろ"),
            EmotionType.CURIOSITY to listOf("ほう", "へえ", "なるほどね", "それで")
        ),
        "毒舌" to mapOf(
            EmotionType.EMPATHY to listOf("知らんけど", "それお前の問題", "ふーん、で？"),
            EmotionType.HUMOR to listOf("草すぎ", "センスないね", "おもろいやん"),
            EmotionType.CONCERN to listOf("また？", "懲りないね", "学習能力"),
            EmotionType.ENCOURAGEMENT to listOf("せいぜいがんばれ", "まあ無理だろうけど", "期待してないけど")
        ),
        "優しい" to mapOf(
            EmotionType.EMPATHY to listOf("わかるよ", "うんうん", "大変だったね", "そうだよね"),
            EmotionType.CONCERN to listOf("大丈夫？", "無理しないで", "休んだら？", "心配だよ"),
            EmotionType.ENCOURAGEMENT to listOf("がんばって", "応援してる", "できるよ", "信じてる"),
            EmotionType.CALM to listOf("ゆっくりね", "大丈夫だよ", "焦らないで", "いい感じ")
        )
    )

    override suspend fun generateResponse(
        output: EmotionOutput,
        soulText: String?,
        screenContext: String?
    ): String {
        if (soulText == null) {
            return EmotionResponseMapper.getResponse(output)
        }

        val traits = parseSoul(soulText)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // ルールベースの時間帯挨拶
        if (traits.rules.any { it.contains("朝") }) {
            if (hour in 5..9 && Math.random() < 0.4) {
                return pickGreeting(traits, isMorning = true)
            }
        }
        if (traits.rules.any { it.contains("夜") }) {
            if ((hour >= 23 || hour < 3) && Math.random() < 0.5) {
                return pickConcernPhrase(traits)
            }
        }

        // 性格特性に基づくフレーズプール構築
        val candidates = mutableListOf<String>()
        for (trait in traits.personality) {
            // 部分一致で性格マッチング
            for ((key, emotionMap) in personalityPhrases) {
                if (trait.contains(key) || key.contains(trait)) {
                    emotionMap[output.selectedAction.type]?.let {
                        candidates.addAll(it)
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return EmotionResponseMapper.getResponse(output)
        }

        // 「短く喋る」性格の場合、短い候補を優先
        val preferShort = traits.personality.any { it.contains("短く") } ||
            traits.speechStyle.any { it.contains("短") }
        val sorted = if (preferShort) {
            candidates.sortedBy { it.length }
        } else {
            candidates
        }

        val index = (output.selectedAction.intensity * (sorted.size - 1))
            .toInt()
            .coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun parseSoul(soulText: String): SoulTraits {
        val lines = soulText.lines()
        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## ")) {
                currentSection = trimmed.removePrefix("## ").trim()
                sections[currentSection] = mutableListOf()
            } else if (trimmed.startsWith("- ") && currentSection.isNotEmpty()) {
                sections[currentSection]?.add(trimmed.removePrefix("- ").trim())
            }
        }

        return SoulTraits(
            personality = sections["性格"] ?: emptyList(),
            speechStyle = sections["口調"] ?: emptyList(),
            interests = sections["興味"] ?: emptyList(),
            rules = sections["ルール"] ?: emptyList()
        )
    }

    private fun pickGreeting(traits: SoulTraits, isMorning: Boolean): String {
        val hasShortStyle = traits.personality.any { it.contains("短く") }
        return if (isMorning) {
            if (hasShortStyle) listOf("おは", "朝", "ん、朝").random()
            else listOf("おはよ", "朝だね", "いい朝").random()
        } else {
            if (hasShortStyle) listOf("おつ", "夜", "ん").random()
            else listOf("おつかれ", "夜だね", "ゆっくりね").random()
        }
    }

    private fun pickConcernPhrase(traits: SoulTraits): String {
        val hasToxic = traits.personality.any { it.contains("毒舌") }
        return if (hasToxic) {
            listOf("まだ起きてんの", "寝ろ", "目悪くなるよ").random()
        } else {
            listOf("もう遅いよ", "そろそろ寝たら？", "明日に響くよ").random()
        }
    }
}
