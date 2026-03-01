package com.example.universal

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

/**
 * WebView管理Fragment。
 * PinchTabのREST APIに相当する機能をevaluateJavascript()で実現。
 * BrowserAutomationManagerから統一的に制御される。
 */
class WebViewFragment : Fragment() {

    private var webView: WebView? = null
    private var urlInput: TextInputEditText? = null

    companion object {
        private const val TAG = "WebViewFragment"
        private const val HOME_URL = "https://www.google.com"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_webview, container, false)

        urlInput = view.findViewById(R.id.urlInput)
        webView = view.findViewById<WebView>(R.id.webView).apply {
            setBackgroundColor(Color.parseColor("#11111b"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                // WebViewフラグ除去（ステルス）
                userAgentString = userAgentString.replace("; wv", "")
            }
            webViewClient = PhoneClawWebViewClient()
            webChromeClient = PhoneClawWebChromeClient()
            addJavascriptInterface(BrowserBridge(), "PhoneClaw")
        }

        setupUrlBar()

        // BrowserAutomationManagerに登録
        BrowserAutomationManager.bind(this)

        return view
    }

    private fun setupUrlBar() {
        urlInput?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val input = v.text.toString().trim()
                if (input.isNotEmpty()) {
                    val url = if (input.startsWith("http://") || input.startsWith("https://")) {
                        input
                    } else if (input.contains(".") && !input.contains(" ")) {
                        "https://$input"
                    } else {
                        "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
                    }
                    navigate(url)
                }
                true
            } else false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 操作API（BrowserAutomationManagerから呼ばれる）
    // ─────────────────────────────────────────────────────────────

    /** URL遷移 — PinchTab /navigate 相当 */
    fun navigate(url: String) {
        activity?.runOnUiThread {
            webView?.loadUrl(url)
            urlInput?.setText(url)
            Log.d(TAG, "Navigate: $url")
        }
    }

    /** JS実行 → 結果コールバック — PinchTab /evaluate 相当 */
    fun executeJs(script: String, callback: (String) -> Unit) {
        activity?.runOnUiThread {
            webView?.evaluateJavascript(script) { result ->
                callback(result ?: "null")
            }
        }
    }

    /** ページテキスト抽出 — PinchTab /text 相当 */
    fun extractText(callback: (String) -> Unit) {
        executeJs("document.body.innerText", callback)
    }

    /**
     * インタラクティブ要素抽出 — PinchTab /snapshot?filter=interactive 相当
     * JSON配列を返す: [{idx, tag, text, href, type}, ...]
     */
    fun extractInteractiveElements(callback: (String) -> Unit) {
        val js = """
            (function() {
                var els = document.querySelectorAll(
                    'a,button,input,select,textarea,[role="button"],[onclick]'
                );
                return JSON.stringify(Array.from(els).map(function(el, i) {
                    return {
                        idx: i,
                        tag: el.tagName,
                        text: (el.innerText || el.value || el.placeholder || '').substring(0, 50),
                        href: el.href || '',
                        type: el.type || ''
                    };
                }));
            })()
        """.trimIndent()
        executeJs(js, callback)
    }

    /** 要素クリック（インデックス指定）— PinchTab /action click 相当 */
    fun clickElement(index: Int) {
        executeJs("""
            (function() {
                var els = document.querySelectorAll(
                    'a,button,input,select,textarea,[role="button"],[onclick]'
                );
                if (els[$index]) { els[$index].click(); return 'clicked'; }
                return 'not_found';
            })()
        """.trimIndent()) { Log.d(TAG, "clickElement($index): $it") }
    }

    /** フォーム入力 — PinchTab /action fill 相当 */
    fun fillInput(selector: String, value: String) {
        val escaped = value.replace("\\", "\\\\").replace("'", "\\'")
        executeJs("""
            (function() {
                var el = document.querySelector('$selector');
                if (el) {
                    el.value = '$escaped';
                    el.dispatchEvent(new Event('input', {bubbles: true}));
                    el.dispatchEvent(new Event('change', {bubbles: true}));
                    return 'filled';
                }
                return 'not_found';
            })()
        """.trimIndent()) { Log.d(TAG, "fillInput($selector): $it") }
    }

    /** 現在のURL取得 */
    fun getCurrentUrl(): String? = webView?.url

    /** 戻る */
    fun goBack(): Boolean {
        return if (webView?.canGoBack() == true) {
            webView?.goBack()
            true
        } else false
    }

    // ─────────────────────────────────────────────────────────────
    // WebViewClient / WebChromeClient
    // ─────────────────────────────────────────────────────────────

    private inner class PhoneClawWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            urlInput?.setText(url)
            BrowserAutomationManager.onPageLoaded(url)
            Log.d(TAG, "Page loaded: $url")
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            return false // WebView内で処理
        }
    }

    private inner class PhoneClawWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // 将来: プログレスバー表示
        }
    }

    // ─────────────────────────────────────────────────────────────
    // JavaScriptBridge（JS → Android）
    // ─────────────────────────────────────────────────────────────

    inner class BrowserBridge {
        @JavascriptInterface
        fun onPageReady(title: String, url: String) {
            Log.d(TAG, "JS Bridge - Page ready: $title ($url)")
        }

        @JavascriptInterface
        fun sendToAndroid(data: String) {
            Log.d(TAG, "JS Bridge - Data: $data")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        BrowserAutomationManager.unbind()
        webView?.destroy()
        webView = null
        urlInput = null
        super.onDestroyView()
    }
}
