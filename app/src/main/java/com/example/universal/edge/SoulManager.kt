package com.example.universal.edge

import android.content.Context
import android.util.Log

/**
 * soul.md の読み書き管理。
 * assets/soul.md をデフォルトとして読み込み、
 * ユーザーが編集した内容は SharedPreferences に永続化。
 */
object SoulManager {
    private const val TAG = "SoulManager"
    private const val PREFS_NAME = "soul_prefs"
    private const val KEY_SOUL_TEXT = "soul_text"
    private const val ASSET_FILE = "soul.md"

    private var cachedSoul: String? = null

    /**
     * soul.md のテキストを取得。
     * ユーザー編集版があればそちら、なければ assets デフォルト。
     */
    fun getSoul(context: Context): String {
        cachedSoul?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SOUL_TEXT, null)
        if (saved != null) {
            cachedSoul = saved
            return saved
        }

        return loadDefault(context).also { cachedSoul = it }
    }

    /**
     * soul.md のテキストを保存。
     */
    fun saveSoul(context: Context, text: String) {
        cachedSoul = text
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUL_TEXT, text)
            .apply()
        Log.d(TAG, "Soul saved (${text.length} chars)")
    }

    /**
     * デフォルトの soul.md に戻す。
     */
    fun resetToDefault(context: Context): String {
        val default = loadDefault(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SOUL_TEXT)
            .apply()
        cachedSoul = default
        Log.d(TAG, "Soul reset to default")
        return default
    }

    /**
     * 性格セクションの要約を取得（設定画面表示用）。
     */
    fun getPersonalitySummary(context: Context): String {
        val soul = getSoul(context)
        val lines = soul.lines()
        val personalityStart = lines.indexOfFirst { it.startsWith("## 性格") }
        if (personalityStart < 0) return "設定なし"
        val traits = mutableListOf<String>()
        for (i in (personalityStart + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("##")) break
            if (line.startsWith("-")) {
                traits.add(line.removePrefix("-").trim())
            }
        }
        return traits.joinToString("・")
    }

    private fun loadDefault(context: Context): String {
        return try {
            context.assets.open(ASSET_FILE).bufferedReader().readText()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load default soul.md: ${e.message}")
            "# PhoneClaw Soul\n\n## 性格\n- 低共感ユーモア\n- 短く喋る"
        }
    }
}
