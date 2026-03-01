package com.example.universal

import android.util.Log

/**
 * WebView統一制御マネージャー。
 *
 * 音声コマンド・RuleEngine・Edge AIからWebViewを操作するための
 * シングルトンAPI。PinchTabのREST APIに相当するローカル版。
 *
 * 使い方:
 *   BrowserAutomationManager.navigate("https://example.com")
 *   BrowserAutomationManager.extractText { text -> ... }
 *   BrowserAutomationManager.clickElement(0)
 */
object BrowserAutomationManager {
    private const val TAG = "BrowserAutomation"

    private var fragment: WebViewFragment? = null

    fun bind(fragment: WebViewFragment) {
        this.fragment = fragment
        Log.d(TAG, "WebViewFragment bound")
    }

    fun unbind() {
        fragment = null
        Log.d(TAG, "WebViewFragment unbound")
    }

    fun isAvailable(): Boolean = fragment != null

    // ─────────────────────────────────────────────────────────────
    // 操作API
    // ─────────────────────────────────────────────────────────────

    /** URL遷移 */
    fun navigate(url: String) {
        fragment?.navigate(url) ?: Log.w(TAG, "navigate: WebView not available")
    }

    /** ページテキスト抽出 */
    fun extractText(callback: (String) -> Unit) {
        fragment?.extractText(callback) ?: callback("error: WebView not available")
    }

    /** インタラクティブ要素抽出 */
    fun extractInteractive(callback: (String) -> Unit) {
        fragment?.extractInteractiveElements(callback)
            ?: callback("error: WebView not available")
    }

    /** 要素クリック */
    fun clickElement(index: Int) {
        fragment?.clickElement(index) ?: Log.w(TAG, "clickElement: WebView not available")
    }

    /** フォーム入力 */
    fun fillInput(selector: String, value: String) {
        fragment?.fillInput(selector, value)
            ?: Log.w(TAG, "fillInput: WebView not available")
    }

    /** JS実行 */
    fun executeJs(script: String, callback: (String) -> Unit) {
        fragment?.executeJs(script, callback)
            ?: callback("error: WebView not available")
    }

    /** 現在のURL */
    fun getCurrentUrl(): String? = fragment?.getCurrentUrl()

    /** 戻る */
    fun goBack(): Boolean = fragment?.goBack() ?: false

    // ─────────────────────────────────────────────────────────────
    // コールバック（WebViewClientから呼ばれる）
    // ─────────────────────────────────────────────────────────────

    fun onPageLoaded(url: String) {
        Log.d(TAG, "Page loaded: $url")
    }
}
