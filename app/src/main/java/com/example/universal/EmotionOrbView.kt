package com.example.universal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.universal.edge.inference.EmotionOutput
import com.example.universal.edge.inference.EmotionType
import kotlin.math.*

/**
 * 感情対応オーバーレイ用オーブView。
 *
 * BreathingOrbViewの有機ノイズ変形描画を踏襲しつつ、
 * EmotionTypeに応じて色・呼吸速度・ノイズ強度を動的変更。
 * TYPE_ACCESSIBILITY_OVERLAYで画面上に浮かせて使う。
 */
class EmotionOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- タッチハンドリング ---
    var onDragListener: ((deltaX: Int, deltaY: Int) -> Unit)? = null
    var onTapListener: (() -> Unit)? = null
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val dragThreshold = 10f * resources.displayMetrics.density

    // --- 感情対応パラメータ ---
    private var primaryColor: Int = Color.parseColor("#cba6f7")
    private var secondaryColor: Int = Color.parseColor("#b4befe")
    private var breathingDuration: Long = 4000L
    private var noiseDuration: Long = 10000L
    private var noiseStrengthFactor: Float = 0.12f

    // --- 描画状態 ---
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 30f
    private var currentRadius = 30f
    private var breathingPhase = 0f
    private var noisePhase = 0f
    private var colorBlendPhase = 0f

    private var breathingAnimator: ValueAnimator? = null
    private var noiseAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    data class EmotionVisual(
        val primary: Int,
        val secondary: Int,
        val breathMs: Long,
        val noiseMs: Long,
        val noiseStrength: Float
    )

    companion object {
        private val EMOTION_VISUALS = mapOf(
            EmotionType.CALM to EmotionVisual(
                Color.parseColor("#cba6f7"), Color.parseColor("#b4befe"),
                4000L, 10000L, 0.12f
            ),
            EmotionType.HUMOR to EmotionVisual(
                Color.parseColor("#f9e2af"), Color.parseColor("#fab387"),
                1500L, 4000L, 0.30f
            ),
            EmotionType.EMPATHY to EmotionVisual(
                Color.parseColor("#89b4fa"), Color.parseColor("#74c7ec"),
                3500L, 9000L, 0.15f
            ),
            EmotionType.SURPRISE to EmotionVisual(
                Color.parseColor("#ffffff"), Color.parseColor("#cdd6f4"),
                800L, 3000L, 0.35f
            ),
            EmotionType.EXCITEMENT to EmotionVisual(
                Color.parseColor("#f38ba8"), Color.parseColor("#eba0ac"),
                1200L, 3500L, 0.28f
            ),
            EmotionType.CONCERN to EmotionVisual(
                Color.parseColor("#fab387"), Color.parseColor("#f9e2af"),
                2500L, 6000L, 0.25f
            ),
            EmotionType.ENCOURAGEMENT to EmotionVisual(
                Color.parseColor("#a6e3a1"), Color.parseColor("#94e2d5"),
                2000L, 5000L, 0.18f
            ),
            EmotionType.CURIOSITY to EmotionVisual(
                Color.parseColor("#cba6f7"), Color.parseColor("#f9e2af"),
                2200L, 4000L, 0.22f
            )
        )
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setupAnimations()
    }

    /**
     * 感情状態を更新。スレッドセーフ。
     */
    fun setEmotion(output: EmotionOutput) {
        val visual = EMOTION_VISUALS[output.selectedAction.type] ?: return
        mainHandler.post {
            primaryColor = visual.primary
            secondaryColor = visual.secondary
            noiseStrengthFactor = visual.noiseStrength

            val needRestart = breathingDuration != visual.breathMs ||
                noiseDuration != visual.noiseMs
            breathingDuration = visual.breathMs
            noiseDuration = visual.noiseMs

            if (needRestart) {
                restartAnimations()
            }
            invalidate()
        }
    }

    private fun setupAnimations() {
        breathingAnimator = ValueAnimator.ofFloat(0f, 2 * PI.toFloat()).apply {
            duration = breathingDuration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { breathingPhase = it.animatedValue as Float; invalidate() }
            start()
        }

        noiseAnimator = ValueAnimator.ofFloat(0f, 2 * PI.toFloat()).apply {
            duration = noiseDuration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { noisePhase = it.animatedValue as Float; invalidate() }
            start()
        }

        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { colorBlendPhase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun restartAnimations() {
        stopAllAnimations()
        setupAnimations()
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

        val breathingOffset = sin(breathingPhase) * (baseRadius * 0.15f)
        currentRadius = baseRadius + breathingOffset

        val noiseStrength = baseRadius * noiseStrengthFactor

        val blendValue = sin(colorBlendPhase * 2 * PI.toFloat()).let { (it + 1f) / 2f }
        val currentColor = interpolateColor(primaryColor, secondaryColor, blendValue)

        // 有機ノイズ変形ポイント
        val numPoints = 32
        val morphedPoints = mutableListOf<PointF>()

        for (i in 0 until numPoints) {
            val angle = (i * 2 * PI / numPoints).toFloat()
            val noise1 = sin(noisePhase + angle * 2f) * 0.5f
            val noise2 = cos(noisePhase * 1.3f + angle * 3f) * 0.3f
            val noise3 = sin(noisePhase * 0.7f + angle * 5f) * 0.2f
            val combinedNoise = (noise1 + noise2 + noise3) / 3f
            val radiusVariation = combinedNoise * noiseStrength
            val radius = currentRadius + radiusVariation
            morphedPoints.add(PointF(centerX + cos(angle) * radius, centerY + sin(angle) * radius))
        }

        // グロー
        val glowRadius = currentRadius * 1.8f
        glowPaint.shader = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(
                Color.argb(80, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.argb(40, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // メインオーブ
        paint.shader = RadialGradient(
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

        if (morphedPoints.isNotEmpty()) {
            val path = Path()
            path.moveTo(morphedPoints[0].x, morphedPoints[0].y)

            for (i in 1 until morphedPoints.size) {
                val current = morphedPoints[i]
                val previous = morphedPoints[i - 1]
                val next = morphedPoints[(i + 1) % morphedPoints.size]
                val cp1x = previous.x + (current.x - previous.x) * 0.5f
                val cp1y = previous.y + (current.y - previous.y) * 0.5f
                val cp2x = current.x + (next.x - current.x) * 0.3f
                val cp2y = current.y + (next.y - current.y) * 0.3f
                path.cubicTo(cp1x, cp1y, cp2x, cp2y, current.x, current.y)
            }

            val first = morphedPoints[0]
            val last = morphedPoints[morphedPoints.size - 1]
            val second = morphedPoints[1]
            val secondLast = morphedPoints[morphedPoints.size - 2]
            path.cubicTo(
                last.x + (first.x - secondLast.x) * 0.3f,
                last.y + (first.y - secondLast.y) * 0.3f,
                first.x + (second.x - last.x) * 0.3f,
                first.y + (second.y - last.y) * 0.3f,
                first.x, first.y
            )
            path.close()
            canvas.drawPath(path, paint)
        }

        // ハイライト
        val highlightRadius = currentRadius * 0.6f
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                centerX - currentRadius * 0.3f,
                centerY - currentRadius * 0.3f,
                highlightRadius,
                intArrayOf(Color.argb(100, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(
            centerX - currentRadius * 0.2f,
            centerY - currentRadius * 0.2f,
            highlightRadius,
            highlightPaint
        )
    }

    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt().coerceIn(0, 255)
        val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt().coerceIn(0, 255)
        val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                dragStartX = event.rawX
                dragStartY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                val totalDx = event.rawX - dragStartX
                val totalDy = event.rawY - dragStartY

                if (!isDragging && (abs(totalDx) > dragThreshold || abs(totalDy) > dragThreshold)) {
                    isDragging = true
                }

                if (isDragging) {
                    onDragListener?.invoke(dx.toInt(), dy.toInt())
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    onTapListener?.invoke()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
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
