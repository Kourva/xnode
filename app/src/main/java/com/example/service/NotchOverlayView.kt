package com.example.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import com.example.model.MetricType
import com.example.model.NodeConfig
import com.example.model.NodeDirection
import com.example.model.NodeShape
import com.example.model.NotchConfig
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class NotchOverlayView(context: Context) : View(context) {

    private var cutoutOriginX: Float = -1f
    private var cutoutOriginY: Float = -1f
    private var cutoutWidth: Float = -1f
    var hasCutout: Boolean = false
        private set

    var config: NotchConfig = NotchConfig()
        set(value) {
            field = value
            invalidate()
        }

    // Animated ratios per node ID
    private val animatedRatios = mutableMapOf<String, Float>()
    private val runningAnimators = mutableMapOf<String, ValueAnimator>()

    // Hardware Telemetry States & Easter Eggs
    var isCharging: Boolean = false
    var rawTempCelsius: Float = 37.0f
    var isNetworkIdle: Boolean = false
    private var rainbowModeEndTimeMs: Long = 0L

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun triggerRainbowMode(durationMs: Long = 3000L) {
        rainbowModeEndTimeMs = System.currentTimeMillis() + durationMs
        postInvalidateOnAnimation()
    }

    fun triggerPreview(nodeId: String) {
        runningAnimators[nodeId]?.cancel()
        val animator = ValueAnimator.ofFloat(0.0f, 1.0f, 0.0f).apply {
            duration = 1000L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                animatedRatios[nodeId] = anim.animatedValue as Float
                invalidate()
            }
        }
        runningAnimators[nodeId] = animator
        animator.start()
    }

    fun updateMetricRatios(
        metricsMap: Map<MetricType, Float>,
        charging: Boolean = false,
        tempCelsius: Float = 37.0f,
        netIdle: Boolean = false
    ) {
        isCharging = charging
        rawTempCelsius = tempCelsius
        isNetworkIdle = netIdle

        config.nodes.forEach { node ->
            val targetRatio = metricsMap[node.metric] ?: 0.5f
            val currentVal = animatedRatios[node.id] ?: targetRatio

            if (kotlin.math.abs(currentVal - targetRatio) > 0.005f) {
                runningAnimators[node.id]?.cancel()
                val animator = ValueAnimator.ofFloat(currentVal, targetRatio).apply {
                    duration = 300L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { anim ->
                        animatedRatios[node.id] = anim.animatedValue as Float
                        invalidate()
                    }
                }
                runningAnimators[node.id] = animator
                animator.start()
            } else {
                animatedRatios[node.id] = targetRatio
            }
        }
        invalidate()
    }

    fun stopAllAnimations() {
        runningAnimators.values.forEach { it.cancel() }
        runningAnimators.clear()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        checkCutoutInsets(rootWindowInsets)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
    }

    private fun checkCutoutInsets(insets: WindowInsets?) {
        if (insets == null) return
        val cutout = insets.displayCutout
        if (cutout != null && cutout.boundingRects.isNotEmpty()) {
            val rect = cutout.boundingRects[0]
            cutoutOriginX = rect.exactCenterX()
            cutoutOriginY = rect.exactCenterY()
            cutoutWidth = rect.width().toFloat()
            hasCutout = true
            invalidate()
        } else if (cutoutOriginX < 0) {
            val statusBarTop = insets.getInsets(WindowInsets.Type.statusBars()).top
            cutoutOriginX = resources.displayMetrics.widthPixels / 2f
            cutoutOriginY = if (statusBarTop > 0) statusBarTop / 2f else (24f * resources.displayMetrics.density)
            cutoutWidth = 24f * resources.displayMetrics.density
            hasCutout = false
            invalidate()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        checkCutoutInsets(insets)
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cutoutOriginX < 0 && w > 0) {
            cutoutOriginX = w / 2f
            cutoutOriginY = 24f * resources.displayMetrics.density
            cutoutWidth = 24f * resources.displayMetrics.density
        }
        checkCutoutInsets(rootWindowInsets)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val baseCenterX = if (cutoutOriginX > 0) cutoutOriginX else width / 2f
        val baseCenterY = if (cutoutOriginY > 0) cutoutOriginY else 24f * density
        val defaultHoleRadius = if (cutoutWidth > 0) cutoutWidth / 2f else 12f * density

        // 1. Ghost mode styling: #111111 when ON, #3D3D3D when OFF
        val unfilledColorInt = if (config.ghostMode) Color.parseColor("#111111") else Color.parseColor("#3D3D3D")
        basePaint.color = unfilledColorInt

        // Scaled dot radius (1.8dp)
        val dotRadius = 1.8f * density
        val gapPx = 2.5f

        // Check active rainbow mode (Easter egg shake)
        val now = System.currentTimeMillis()
        val isRainbow = now < rainbowModeEndTimeMs
        if (isRainbow) {
            postInvalidateOnAnimation()
        }
        val rainbowHue = ((now % 1000L) / 1000f) * 360f
        val rainbowColor = Color.HSVToColor(floatArrayOf(rainbowHue, 1.0f, 1.0f))

        // Draw each enabled custom node
        config.nodes.forEach { node ->
            if (!node.enabled) return@forEach

            val isNetNode = node.metric == MetricType.NET_IN || node.metric == MetricType.NET_OUT
            val ratio = if (isNetNode && isNetworkIdle) 0.0f else (animatedRatios[node.id] ?: 0.5f)

            // Dynamic effects detection
            val isBatCharging = node.metric == MetricType.BAT && isCharging
            val isTempWarning = node.metric == MetricType.TEMP && rawTempCelsius >= 80.0f

            if (isBatCharging || isTempWarning) {
                postInvalidateOnAnimation()
            }

            // Determine active color based on static vs dynamic vs rainbow vs temp warning
            val activeColorInt = if (isRainbow) {
                rainbowColor
            } else if (isTempWarning) {
                val strobeOn = ((now / 250L) % 2L) == 0L
                if (strobeOn) Color.RED else Color.DKGRAY
            } else if (node.isDynamicColor) {
                if (ratio >= node.alertThreshold) node.alertColor else node.baseColor
            } else {
                node.staticColor
            }

            when (node.shape) {
                NodeShape.ARC -> {
                    drawArcNode(
                        canvas = canvas,
                        node = node,
                        baseCenterX = baseCenterX,
                        baseCenterY = baseCenterY,
                        density = density,
                        dotRadius = dotRadius,
                        gapPx = gapPx,
                        activeColor = activeColorInt,
                        ratio = ratio,
                        isBatCharging = isBatCharging
                    )
                }
                NodeShape.STRAIGHT -> {
                    drawStraightNode(
                        canvas = canvas,
                        node = node,
                        baseCenterX = baseCenterX,
                        baseCenterY = baseCenterY,
                        density = density,
                        dotRadius = dotRadius,
                        defaultHoleRadius = defaultHoleRadius,
                        activeColor = activeColorInt,
                        ratio = ratio,
                        isBatCharging = isBatCharging
                    )
                }
            }
        }
    }

    private fun drawArcNode(
        canvas: Canvas,
        node: NodeConfig,
        baseCenterX: Float,
        baseCenterY: Float,
        density: Float,
        dotRadius: Float,
        gapPx: Float,
        activeColor: Int,
        ratio: Float,
        isBatCharging: Boolean
    ) {
        val n = node.dotCount.coerceIn(2, 5)
        val radiusPx = node.sizeDp * density
        val targetChord = (2f * dotRadius) + gapPx
        val stepRad = 2.0 * asin((targetChord / (2.0 * radiusPx)).coerceIn(0.04, 0.45))
        val stepDeg = Math.toDegrees(stepRad)

        val centerAngle = if (node.direction == NodeDirection.LEFT) 180.0 else 0.0
        val nodeCenterX = baseCenterX + (node.positionXDp * density)
        val nodeCenterY = baseCenterY + (node.positionYDp * density)

        val rawDots = mutableListOf<DotPoint>()
        for (i in 0 until n) {
            val offsetIndex = i - (n - 1) / 2.0
            val angleDeg = centerAngle + (offsetIndex * stepDeg)
            val rad = Math.toRadians(angleDeg)
            val x = nodeCenterX + (radiusPx * cos(rad)).toFloat()
            val y = nodeCenterY + (radiusPx * sin(rad)).toFloat()
            rawDots.add(DotPoint(x, y))
        }

        // Sort dots from bottom to top (decreasing Y)
        val sortedDots = rawDots.sortedByDescending { it.y }
        renderDots(canvas, sortedDots, dotRadius, activeColor, ratio, n, isBatCharging)
    }

    private fun drawStraightNode(
        canvas: Canvas,
        node: NodeConfig,
        baseCenterX: Float,
        baseCenterY: Float,
        density: Float,
        dotRadius: Float,
        defaultHoleRadius: Float,
        activeColor: Int,
        ratio: Float,
        isBatCharging: Boolean
    ) {
        val n = node.dotCount.coerceIn(2, 5)
        val lengthPx = node.sizeDp * density

        // If direction is LEFT, default to the left of the cutout; if RIGHT, default to the right
        val baseSideOffset = if (node.direction == NodeDirection.LEFT) {
            -(defaultHoleRadius + 6f * density)
        } else {
            +(defaultHoleRadius + 6f * density)
        }

        val nodeCenterX = baseCenterX + baseSideOffset + (node.positionXDp * density)
        val nodeCenterY = baseCenterY + (node.positionYDp * density)

        val rawDots = mutableListOf<DotPoint>()
        val halfLength = lengthPx / 2f
        val stepY = if (n > 1) lengthPx / (n - 1) else 0f

        for (i in 0 until n) {
            val y = (nodeCenterY - halfLength) + (i * stepY)
            rawDots.add(DotPoint(nodeCenterX, y))
        }

        // Sort dots from bottom to top (decreasing Y)
        val sortedDots = rawDots.sortedByDescending { it.y }
        renderDots(canvas, sortedDots, dotRadius, activeColor, ratio, n, isBatCharging)
    }

    private fun renderDots(
        canvas: Canvas,
        sortedDots: List<DotPoint>,
        dotRadius: Float,
        activeColor: Int,
        ratio: Float,
        n: Int,
        isBatCharging: Boolean
    ) {
        val totalFill = ratio.coerceIn(0f, 1f) * n
        val now = System.currentTimeMillis()

        for (i in 0 until n) {
            val pt = sortedDots[i]

            // 1. Draw base unfilled dot
            canvas.drawCircle(pt.x, pt.y, dotRadius, basePaint)

            // Charging pulse multiplier for breathing heartbeat effect
            val pulseMultiplier = if (isBatCharging) {
                ((sin((now + i * 180) / 250.0).toFloat() * 0.35f) + 0.65f).coerceIn(0.2f, 1.0f)
            } else {
                1.0f
            }

            // 2. Draw active fill from bottom up
            if (totalFill >= (i + 1)) {
                fillPaint.color = activeColor
                fillPaint.alpha = (255 * pulseMultiplier).toInt().coerceIn(0, 255)
                canvas.drawCircle(pt.x, pt.y, dotRadius, fillPaint)
            } else if (totalFill > i) {
                val fraction = (totalFill - i).coerceIn(0f, 1f)
                fillPaint.color = activeColor
                fillPaint.alpha = (fraction * 255 * pulseMultiplier).toInt().coerceIn(0, 255)
                canvas.drawCircle(pt.x, pt.y, dotRadius, fillPaint)
            }
        }
    }

    private data class DotPoint(val x: Float, val y: Float)
}
