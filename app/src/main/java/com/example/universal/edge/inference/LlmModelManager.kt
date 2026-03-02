package com.example.universal.edge.inference

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Gemma 3 1B IT INT4 (QAT) モデルのダウンロード・ライフサイクル管理。
 *
 * - Wi-Fi接続時のみ自動DL
 * - context.filesDir/llm_models/ に保存
 * - 進捗コールバック付き
 *
 * Gemma 2B INT4 (~1.3GB) → Gemma 3 1B QAT INT4 (~529MB) に移行。
 * サイズ60%減、prefill速度85x向上、RAM半減。
 */
object LlmModelManager {

    private const val TAG = "LlmModelManager"

    private const val MODEL_DIR = "llm_models"
    private const val MODEL_FILENAME = "gemma3-1b-it-int4.task"

    // HuggingFace litert-community: Gemma 3 1B IT INT4 QAT (.task形式)
    // ※ Gemmaライセンス同意が必要。403エラー時は手動DL:
    //    adb push gemma3-1b-it-int4.task /data/data/com.example.universal/files/llm_models/
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

    private const val EXPECTED_MIN_SIZE_BYTES = 200_000_000L // ~200MB 最低サイズ (実際は~529MB)

    enum class ModelState {
        NOT_DOWNLOADED,
        DOWNLOADING,
        READY,
        ERROR
    }

    @Volatile
    var state: ModelState = ModelState.NOT_DOWNLOADED
        private set

    @Volatile
    var downloadProgress: Float = 0f
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    fun getModelPath(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR)
        return File(dir, MODEL_FILENAME)
    }

    fun isModelReady(context: Context): Boolean {
        val modelFile = getModelPath(context)
        val ready = modelFile.exists() && modelFile.length() > EXPECTED_MIN_SIZE_BYTES
        if (ready) state = ModelState.READY
        return ready
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * モデルをダウンロードする。Wi-Fi接続チェックは呼び出し側で行うこと。
     */
    suspend fun downloadModel(
        context: Context,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (state == ModelState.DOWNLOADING) return@withContext false
        if (isModelReady(context)) return@withContext true

        state = ModelState.DOWNLOADING
        downloadProgress = 0f
        errorMessage = null

        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) dir.mkdirs()

        val targetFile = getModelPath(context)
        val tempFile = File(dir, "${MODEL_FILENAME}.tmp")

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(MODEL_URL).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (totalBytes > 0) {
                            downloadProgress = bytesRead.toFloat() / totalBytes
                            onProgress?.invoke(downloadProgress)
                        }
                    }
                }
            }

            // 成功: tmpをリネーム
            if (tempFile.length() > EXPECTED_MIN_SIZE_BYTES) {
                tempFile.renameTo(targetFile)
                state = ModelState.READY
                downloadProgress = 1f
                Log.i(TAG, "Model downloaded: ${targetFile.length()} bytes")
                true
            } else {
                tempFile.delete()
                throw Exception("Downloaded file too small: ${tempFile.length()} bytes")
            }
        } catch (e: Exception) {
            state = ModelState.ERROR
            errorMessage = e.message
            Log.e(TAG, "Model download failed", e)
            tempFile.delete()
            false
        }
    }
}
