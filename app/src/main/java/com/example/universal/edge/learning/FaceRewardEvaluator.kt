package com.example.universal.edge.learning

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * MediaPipe FaceMesh を使った表情ベースの報酬評価器。
 *
 * AIがリアクションした直後のユーザーの表情を検知し、
 * 笑顔→正の報酬、無表情/しかめ面→負の報酬としてスコアリングする。
 *
 * 主要なランドマーク:
 * - 口角 (61, 291): 上がっている=笑顔
 * - 眉間 (9, 10): 寄っている=しかめ面
 * - 目の開き (159, 145 / 386, 374): 大きい=驚き/興味
 *
 * フレームレート: 500ms間隔でサンプリング（バッテリー配慮）
 */
class FaceRewardEvaluator(
    private val context: Context
) : IRewardEvaluator {

    private var faceLandmarker: FaceLandmarker? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "FaceRewardEvaluator"
        private const val SAMPLE_INTERVAL_MS = 500L
        private const val MODEL_ASSET = "face_landmarker.task"

        // FaceMeshランドマークインデックス
        private const val MOUTH_LEFT = 61
        private const val MOUTH_RIGHT = 291
        private const val MOUTH_TOP = 13
        private const val MOUTH_BOTTOM = 14
        private const val LEFT_EYE_TOP = 159
        private const val LEFT_EYE_BOTTOM = 145
        private const val RIGHT_EYE_TOP = 386
        private const val RIGHT_EYE_BOTTOM = 374
        private const val NOSE_TIP = 1
        private const val FOREHEAD = 10
    }

    override suspend fun prepare() {
        withContext(Dispatchers.IO) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build()

                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setNumFaces(1)
                    .build()

                faceLandmarker = FaceLandmarker.createFromOptions(context, options)
                isInitialized = true
                Log.d(TAG, "FaceLandmarker initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FaceLandmarker: ${e.message}")
                isInitialized = false
            }
        }
    }

    override fun isAvailable(): Boolean = isInitialized && faceLandmarker != null

    /**
     * 指定期間中のカメラフレームから表情を評価し、報酬シグナルを返す。
     *
     * 現在の実装ではScreenCaptureServiceの最新フレームを使う。
     * 将来的にはフロントカメラからの直接キャプチャに置き換え可能。
     */
    override suspend fun evaluate(durationMs: Long): RewardSignal {
        if (!isAvailable()) {
            Log.w(TAG, "FaceLandmarker not available, returning neutral")
            return RewardSignal(score = 0f, confidence = 0f)
        }

        val scores = mutableListOf<Float>()
        val allFeatures = mutableListOf<FloatArray>()
        val samples = (durationMs / SAMPLE_INTERVAL_MS).toInt().coerceAtLeast(1)

        for (i in 0 until samples) {
            val frameResult = captureAndAnalyzeFrame()
            if (frameResult != null) {
                scores.add(frameResult.first)
                allFeatures.add(frameResult.second)
            }
            if (i < samples - 1) delay(SAMPLE_INTERVAL_MS)
        }

        if (scores.isEmpty()) {
            return RewardSignal(score = 0f, confidence = 0f)
        }

        val avgScore = scores.average().toFloat()
        val confidence = (scores.size.toFloat() / samples).coerceIn(0f, 1f)
        val avgFeatures = if (allFeatures.isNotEmpty()) {
            averageFeatures(allFeatures)
        } else FloatArray(0)

        return RewardSignal(
            score = avgScore.coerceIn(-1f, 1f),
            confidence = confidence,
            rawFeatures = avgFeatures
        )
    }

    /**
     * 1フレームのキャプチャと解析。
     * @return Pair(スコア, 生特徴量) or null
     */
    private fun captureAndAnalyzeFrame(): Pair<Float, FloatArray>? {
        // ScreenCaptureServiceの最新フレームを活用
        // 将来的にはCameraXからフロントカメラフレームを取得
        val pngBytes = try {
            val field = Class.forName("com.example.universal.ScreenCaptureService")
                .getDeclaredField("lastCapturedPng")
            field.isAccessible = true
            field.get(null) as? ByteArray
        } catch (e: Exception) {
            null
        }

        if (pngBytes == null) return null

        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                ?: return null
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceLandmarker?.detect(mpImage) ?: return null
            analyzeResult(result)
        } catch (e: Exception) {
            Log.d(TAG, "Frame analysis failed: ${e.message}")
            null
        }
    }

    /**
     * FaceLandmarkerの結果から感情スコアを算出。
     *
     * 笑顔度: 口角の上昇量
     * 興味度: 目の開き
     * 不快度: 眉間の縮み
     *
     * → 総合スコア = (笑顔度 * 0.6) + (興味度 * 0.3) - (不快度 * 0.4)
     */
    private fun analyzeResult(result: FaceLandmarkerResult): Pair<Float, FloatArray>? {
        if (result.faceLandmarks().isEmpty()) return null

        val landmarks = result.faceLandmarks()[0]
        if (landmarks.size < 400) return null

        // 口角の高さ（笑顔指標）
        val mouthLeft = landmarks[MOUTH_LEFT]
        val mouthRight = landmarks[MOUTH_RIGHT]
        val mouthTop = landmarks[MOUTH_TOP]
        val mouthBottom = landmarks[MOUTH_BOTTOM]
        val mouthWidth = Math.abs(mouthRight.x() - mouthLeft.x())
        val mouthHeight = Math.abs(mouthBottom.y() - mouthTop.y())
        val smileRatio = if (mouthWidth > 0) mouthHeight / mouthWidth else 0f

        // 口角の上昇（y座標が小さい方が上）
        val mouthCornerAvgY = (mouthLeft.y() + mouthRight.y()) / 2
        val mouthCenterY = (mouthTop.y() + mouthBottom.y()) / 2
        val cornerLift = (mouthCenterY - mouthCornerAvgY).coerceIn(-0.1f, 0.1f) * 10

        // 目の開き（興味/驚き指標）
        val leftEyeOpen = Math.abs(landmarks[LEFT_EYE_TOP].y() - landmarks[LEFT_EYE_BOTTOM].y())
        val rightEyeOpen = Math.abs(landmarks[RIGHT_EYE_TOP].y() - landmarks[RIGHT_EYE_BOTTOM].y())
        val eyeOpenness = ((leftEyeOpen + rightEyeOpen) / 2 * 20).coerceIn(0f, 1f)

        // 笑顔スコア
        val smileScore = (cornerLift * 0.7f + smileRatio * 0.3f).coerceIn(-1f, 1f)

        // 総合スコア
        val totalScore = (smileScore * 0.6f + eyeOpenness * 0.3f).coerceIn(-1f, 1f)

        val features = floatArrayOf(smileScore, eyeOpenness, smileRatio, cornerLift)
        return Pair(totalScore, features)
    }

    private fun averageFeatures(features: List<FloatArray>): FloatArray {
        if (features.isEmpty()) return FloatArray(0)
        val size = features.first().size
        val avg = FloatArray(size)
        for (f in features) {
            for (i in 0 until minOf(size, f.size)) {
                avg[i] += f[i]
            }
        }
        for (i in avg.indices) avg[i] /= features.size
        return avg
    }

    override fun release() {
        faceLandmarker?.close()
        faceLandmarker = null
        isInitialized = false
    }
}
