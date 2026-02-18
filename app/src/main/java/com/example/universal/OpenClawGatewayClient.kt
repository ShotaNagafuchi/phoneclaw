package com.example.universal

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OpenClaw Gateway WebSocket client. Connects PhoneClaw as a Node to the OpenClaw Gateway
 * control plane, enabling AI-driven device automation via node.invoke.
 *
 * Protocol: https://docs.openclaw.ai/gateway/protocol
 */
class OpenClawGatewayClient(
    private val context: Context,
    private val invokeHandler: NodeInvokeHandler,
    private val connectionCallback: ConnectionCallback
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var connectNonce: String? = null
    private var connectToken: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(host: String, port: Int, token: String? = null) {
        connectToken = token
        scope.launch {
            try {
                val scheme = if (port == 443) "wss" else "ws"
                val url = "$scheme://$host:$port"
                Log.d(TAG, "Connecting to Gateway at $url")

                withContext(Dispatchers.Main) {
                    connectionCallback.onConnecting()
                }

                val request = Request.Builder()
                    .url(url)
                    .build()

                webSocket = client.newWebSocket(request, createListener(token))
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    connectionCallback.onDisconnected("Connect failed: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
            withContext(Dispatchers.Main) {
                connectionCallback.onDisconnected("Disconnected")
            }
        }
    }

    fun isConnected(): Boolean = webSocket != null

    private fun createListener(token: String?): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                connectNonce = null
                // Protocol: first frame from client must be connect
                sendConnect(webSocket, token, null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, webSocket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                this@OpenClawGatewayClient.webSocket = null
                scope.launch {
                    withContext(Dispatchers.Main) {
                        connectionCallback.onDisconnected(reason)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                this@OpenClawGatewayClient.webSocket = null
                scope.launch {
                    withContext(Dispatchers.Main) {
                        connectionCallback.onDisconnected("Connection failed: ${t.message}")
                    }
                }
            }
        }
    }

    private fun handleMessage(text: String, webSocket: WebSocket) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            when (type) {
                "event" -> {
                    val event = json.get("event")?.asString
                    if (event == "connect.challenge") {
                        val payload = json.getAsJsonObject("payload")
                        val nonce = payload?.get("nonce")?.asString
                        connectNonce = nonce
                        sendConnect(webSocket, connectToken, nonce)
                    }
                }
                "res" -> {
                    val ok = json.get("ok")?.asBoolean ?: false
                    val payload = json.getAsJsonObject("payload")
                    if (ok && payload != null) {
                        val helloType = payload.get("type")?.asString
                        if (helloType == "hello-ok") {
                            Log.d(TAG, "Connected to Gateway (hello-ok)")
                            scope.launch {
                                withContext(Dispatchers.Main) {
                                    connectionCallback.onConnected()
                                }
                            }
                        }
                    }
                }
                "req" -> {
                    val id = json.get("id")?.asString
                    val method = json.get("method")?.asString
                    val params = json.getAsJsonObject("params")

                    if ((method == "invoke" || method == "node.invoke") && params != null) {
                        scope.launch {
                            handleInvoke(webSocket, id, params)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun sendConnect(webSocket: WebSocket, token: String?, nonce: String?) {
        val deviceId = getDeviceId()
        val connectParams = JsonObject().apply {
            addProperty("minProtocol", 3)
            addProperty("maxProtocol", 3)
            add("client", JsonObject().apply {
                addProperty("id", "phoneclaw-android")
                addProperty("version", "1.1.0")
                addProperty("platform", "android")
                addProperty("mode", "node")
            })
            addProperty("role", "node")
            add("scopes", gson.toJsonTree(emptyList<String>()))
            add("caps", gson.toJsonTree(listOf(
                "camera", "canvas", "screen", "android-automation"
            )))
            add("commands", gson.toJsonTree(listOf(
                "android.tap", "android.swipe", "android.type",
                "android.magicClick", "android.magicScraper", "android.getScreenText"
            )))
            add("permissions", JsonObject().apply {
                addProperty("accessibility", true)
            })
            add("device", JsonObject().apply {
                addProperty("id", deviceId)
                addProperty("publicKey", "")
                addProperty("signature", "")
                addProperty("signedAt", System.currentTimeMillis())
                addProperty("nonce", nonce ?: "")
            })
            addProperty("locale", "en-US")
            addProperty("userAgent", "phoneclaw-android/1.1.0")
            if (!token.isNullOrEmpty()) {
                add("auth", JsonObject().apply {
                    addProperty("token", token)
                })
            }
        }

        val req = JsonObject().apply {
            addProperty("type", "req")
            addProperty("id", UUID.randomUUID().toString())
            addProperty("method", "connect")
            add("params", connectParams)
        }

        val payload = gson.toJson(req)
        Log.d(TAG, "Sending connect: $payload")
        webSocket.send(payload)
    }

    private suspend fun handleInvoke(webSocket: WebSocket, id: String?, params: JsonObject) {
        val command = params.get("command")?.asString ?: run {
            sendInvokeError(webSocket, id, "Missing command")
            return
        }
        val invokeParams = params.getAsJsonObject("params") ?: params

        val result = invokeHandler.handleInvoke(command, invokeParams)

        val res = JsonObject().apply {
            addProperty("type", "res")
            addProperty("id", id)
            addProperty("ok", result.success)
            if (result.success) {
                add("payload", result.payload ?: JsonObject())
            } else {
                addProperty("error", result.error ?: "Unknown error")
            }
        }
        webSocket.send(gson.toJson(res))
    }

    private fun sendInvokeError(webSocket: WebSocket, id: String?, message: String) {
        val res = JsonObject().apply {
            addProperty("type", "res")
            addProperty("id", id)
            addProperty("ok", false)
            addProperty("error", message)
        }
        webSocket.send(gson.toJson(res))
    }

    private fun getDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            "phoneclaw-${androidId ?: "unknown"}"
        } catch (e: Exception) {
            "phoneclaw-${UUID.randomUUID().toString().take(12)}"
        }
    }

    interface ConnectionCallback {
        fun onConnecting()
        fun onConnected()
        fun onDisconnected(reason: String)
    }

    companion object {
        private const val TAG = "OpenClawGateway"
    }
}
