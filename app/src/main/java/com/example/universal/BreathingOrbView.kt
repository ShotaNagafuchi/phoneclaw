package com.example.universal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * Breathing Orb View: Aurora UIベースの2色（紫/ピンク）で有機ノイズ変形するオーブ
 * transparentを多用し、呼吸アニメーションを実装
 */
class BreathingOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 100f
    private var currentRadius = 100f

    // Animation properties
    private var breathingPhase = 0f
    private var noisePhase = 0f
    private var colorBlendPhase = 0f

    // Animators
    private var breathingAnimator: ValueAnimator? = null
    private var noiseAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null

    // Aurora UI colors: 紫 #cba6f7, ピンク #f38ba8
    private val colorPurple = Color.parseColor("#cba6f7")
    private val colorPink = Color.parseColor("#f38ba8")

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setupAnimations()
    }

    private fun setupAnimations() {
        // Breathing animation (呼吸)
        breathingAnimator = ValueAnimator.ofFloat(0f, 2 * PI.toFloat()).apply {
            duration = 3000 // 3秒で1呼吸
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                breathingPhase = animation.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Organic noise animation (有機ノイズ)
        noiseAnimator = ValueAnimator.ofFloat(0f, 2 * PI.toFloat()).apply {
            duration = 8000 // 8秒で1周期
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                noisePhase = animation.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Color blend animation (色のブレンド)
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000 // 5秒で色が変化
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                colorBlendPhase = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = minOf(w, h) / 4f
        currentRadius = baseRadius
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 呼吸による半径の変化（sin波で滑らかに）
        val breathingOffset = sin(breathingPhase) * (baseRadius * 0.15f)
        currentRadius = baseRadius + breathingOffset

        // 有機ノイズ関数（簡易版Perlin noise風）
        val noiseScale = 0.02f
        val noiseStrength = baseRadius * 0.2f

        // 現在の色をブレンド（紫とピンクの間で滑らかに変化）
        val blendValue = sin(colorBlendPhase * 2 * PI.toFloat()).let { (it + 1f) / 2f }
        val currentColor = interpolateColor(colorPurple, colorPink, blendValue)

        // オーブの形状を有機的に変形させるためのポイント生成
        val numPoints = 32 // より滑らかな形状のためポイント数を増やす
        val morphedPoints = mutableListOf<PointF>()

        for (i in 0 until numPoints) {
            val angle = (i * 2 * PI / numPoints).toFloat()
            
            // 有機ノイズによる変形（複数のsin/cos波を組み合わせ）
            val noise1 = sin(noisePhase + angle * 2f) * 0.5f
            val noise2 = cos(noisePhase * 1.3f + angle * 3f) * 0.3f
            val noise3 = sin(noisePhase * 0.7f + angle * 5f) * 0.2f
            val combinedNoise = (noise1 + noise2 + noise3) / 3f
            
            val radiusVariation = combinedNoise * noiseStrength
            val radius = currentRadius + radiusVariation
            
            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius
            
            morphedPoints.add(PointF(x, y))
        }

        // グロー効果（外側の光）
        val glowRadius = currentRadius * 1.8f
        val glowGradient = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(
                Color.argb(80, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.argb(40, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = glowGradient
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // メインオーブのグラデーション
        val mainGradient = RadialGradient(
            centerX - currentRadius * 0.2f,
            centerY - currentRadius * 0.2f,
            currentRadius * 1.5f,
            intArrayOf(
                Color.argb(200, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.argb(150, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.argb(100, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        paint.shader = mainGradient

        // 有機的に変形したオーブを描画
        if (morphedPoints.isNotEmpty()) {
            val path = Path()
            path.moveTo(morphedPoints[0].x, morphedPoints[0].y)

            // 滑らかな曲線で接続
            for (i in 1 until morphedPoints.size) {
                val current = morphedPoints[i]
                val previous = morphedPoints[i - 1]
                val next = morphedPoints[(i + 1) % morphedPoints.size]

                // ベジェ曲線の制御点
                val cp1x = previous.x + (current.x - previous.x) * 0.5f
                val cp1y = previous.y + (current.y - previous.y) * 0.5f
                val cp2x = current.x + (next.x - current.x) * 0.3f
                val cp2y = current.y + (next.y - current.y) * 0.3f

                path.cubicTo(cp1x, cp1y, cp2x, cp2y, current.x, current.y)
            }

            // パスを閉じる
            val first = morphedPoints[0]
            val last = morphedPoints[morphedPoints.size - 1]
            val second = morphedPoints[1]
            val secondLast = morphedPoints[morphedPoints.size - 2]

            val cp1x = last.x + (first.x - secondLast.x) * 0.3f
            val cp1y = last.y + (first.y - secondLast.y) * 0.3f
            val cp2x = first.x + (second.x - last.x) * 0.3f
            val cp2y = first.y + (second.y - last.y) * 0.3f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, first.x, first.y)
            path.close()

            canvas.drawPath(path, paint)
        }

        // 内側のハイライト（透明から白へのグラデーション）
        val highlightRadius = currentRadius * 0.6f
        val highlightGradient = RadialGradient(
            centerX - currentRadius * 0.3f,
            centerY - currentRadius * 0.3f,
            highlightRadius,
            intArrayOf(
                Color.argb(100, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        highlightPaint.shader = highlightGradient
        canvas.drawCircle(
            centerX - currentRadius * 0.2f,
            centerY - currentRadius * 0.2f,
            highlightRadius,
            highlightPaint
        )
    }

    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val r = (r1 + (r2 - r1) * fraction).toInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * fraction).toInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * fraction).toInt().coerceIn(0, 255)

        return Color.rgb(r, g, b)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
    }

    private fun stopAllAnimations() {
        breathingAnimator?.cancel()
        noiseAnimator?.cancel()
        colorAnimator?.cancel()
    }
}
