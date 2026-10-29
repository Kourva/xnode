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
            if (field == value) return
            val activeNodeIds = value.nodes.map { it.id }.toSet()
            val iterator = runningAnimators.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in activeNodeIds) {
                    entry.value.cancel()
                    iterator.remove()
                }
            }
            field = value
            recalculateDotPositions()
            postInvalidateOnAnimation()
        }

    // Precomputed dot coordinate holding raw floats
    class DotPoint(val x: Float, val y: Float)

    // Precomputed node container to ensure zero allocations inside onDraw()
    private class CachedNode(
        val node: NodeConfig,
        val dots: Array<DotPoint>,
        val isNetNode: Boolean
    )

    private var cachedNodes: Array<CachedNode> = emptyArray()
    private var dotRadiusPx: Float = 0f

    // Animated ratios per node ID
    private val animatedRatios = mutableMapOf<String, Float>()
    private val runningAnimators = mutableMapOf<String, ValueAnimator>()

    // Hardware Telemetry States & Easter Eggs
    var isCharging: Boolean = false
    var rawTempCelsius: Float = 37.0f
    var isNetworkIdle: Boolean = false
    private var rainbowModeEndTimeMs: Long = 0L

    // Dedicated reusable Paint objects (never allocated in onDraw)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Reusable HSV buffer to prevent float array allocations during rainbow mode
    private val rainbowHsv = floatArrayOf(0f, 1.0f, 1.0f)

    companion object {
        private const val COLOR_GHOST_ON = 0xFF111111.toInt()
        private const val COLOR_GHOST_OFF = 0xFF3D3D3D.toInt()
        private const val COLOR_TEMP_ALERT = 0xFFFF0000.toInt()
        private const val COLOR_TEMP_DIM = 0xFF444444.toInt()
    }

    init {
        // Explicitly set background to null to eliminate any full-screen overdraw pass on the GPU
        background = null
        setWillNotDraw(false)
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
                postInvalidateOnAnimation()
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
                        postInvalidateOnAnimation()
                    }
                }
                runningAnimators[node.id] = animator
                animator.start()
            } else {
                animatedRatios[node.id] = targetRatio
            }
        }
        postInvalidateOnAnimation()
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
        var changed = false
        val cutout = insets.displayCutout
        if (cutout != null && cutout.boundingRects.isNotEmpty()) {
            val rect = cutout.boundingRects[0]
            val newX = rect.exactCenterX()
            val newY = rect.exactCenterY()
            val newW = rect.width().toFloat()
            if (cutoutOriginX != newX || cutoutOriginY != newY || cutoutWidth != newW || !hasCutout) {
                cutoutOriginX = newX
                cutoutOriginY = newY
                cutoutWidth = newW
                hasCutout = true
                changed = true
            }
        } else if (cutoutOriginX < 0) {
            val statusBarTop = insets.getInsets(WindowInsets.Type.statusBars()).top
            cutoutOriginX = resources.displayMetrics.widthPixels / 2f
            cutoutOriginY = if (statusBarTop > 0) statusBarTop / 2f else (24f * resources.displayMetrics.density)
            cutoutWidth = 24f * resources.displayMetrics.density
            hasCutout = false
            changed = true
        }

        if (changed) {
            recalculateDotPositions()
            postInvalidateOnAnimation()
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
        recalculateDotPositions()
    }

    /**
     * Precomputes dot positions, sizes, and sorts them once, avoiding heavy calculations
     * and array allocations inside onDraw() for 120fps performance.
     */
    private fun recalculateDotPositions() {
        val density = resources.displayMetrics.density
        dotRadiusPx = 1.8f * density
        val baseCenterX = if (cutoutOriginX > 0) cutoutOriginX else (width.takeIf { it > 0 }?.toFloat() ?: 1080f) / 2f
        val baseCenterY = if (cutoutOriginY > 0) cutoutOriginY else 24f * density
        val defaultHoleRadius = if (cutoutWidth > 0) cutoutWidth / 2f else 12f * density
        val gapPx = 2.5f

        val newCached = mutableListOf<CachedNode>()

        config.nodes.forEach { node ->
            if (!node.enabled) return@forEach

            val n = node.dotCount.coerceIn(2, 5)
            val dots: Array<DotPoint> = when (node.shape) {
                NodeShape.ARC -> {
                    val radiusPx = node.sizeDp * density
                    val targetChord = (2f * dotRadiusPx) + gapPx
                    val stepRad = 2.0 * asin((targetChord / (2.0 * radiusPx)).coerceIn(0.04, 0.45))
                    val stepDeg = Math.toDegrees(stepRad)
                    val centerAngle = if (node.direction == NodeDirection.LEFT) 180.0 else 0.0
                    val nodeCenterX = baseCenterX + (node.positionXDp * density)
                    val nodeCenterY = baseCenterY + (node.positionYDp * density)

                    val rawDots = ArrayList<DotPoint>(n)
                    for (i in 0 until n) {
                        val offsetIndex = i - (n - 1) / 2.0
                        val angleDeg = centerAngle + (offsetIndex * stepDeg)
                        val rad = Math.toRadians(angleDeg)
                        val x = nodeCenterX + (radiusPx * cos(rad)).toFloat()
                        val y = nodeCenterY + (radiusPx * sin(rad)).toFloat()
                        rawDots.add(DotPoint(x, y))
                    }
                    rawDots.sortByDescending { it.y }
                    rawDots.toTypedArray()
                }
                NodeShape.STRAIGHT -> {
                    val lengthPx = node.sizeDp * density
                    val baseSideOffset = if (node.direction == NodeDirection.LEFT) {
                        -(defaultHoleRadius + 6f * density)
                    } else {
                        +(defaultHoleRadius + 6f * density)
                    }
                    val nodeCenterX = baseCenterX + baseSideOffset + (node.positionXDp * density)
                    val nodeCenterY = baseCenterY + (node.positionYDp * density)

                    val stepY = if (n > 1) lengthPx / (n - 1) else 0f
                    val totalColumnHeight = if (n > 1) (n - 1) * stepY else 0f
                    val startY = nodeCenterY - (totalColumnHeight / 2f)

                    val rawDots = ArrayList<DotPoint>(n)
                    for (i in 0 until n) {
                        val y = startY + (i * stepY)
                        rawDots.add(DotPoint(nodeCenterX, y))
                    }
                    rawDots.sortByDescending { it.y }
                    rawDots.toTypedArray()
                }
            }

            val isNet = node.metric == MetricType.NET_IN || node.metric == MetricType.NET_OUT
            newCached.add(CachedNode(node, dots, isNet))
        }

        cachedNodes = newCached.toTypedArray()
    }

    override fun onDraw(canvas: Canvas) {
        val totalNodes = cachedNodes.size
        if (totalNodes == 0) return

        // Ghost mode styling using pre-resolved integer colors (no string parsing)
        basePaint.color = if (config.ghostMode) COLOR_GHOST_ON else COLOR_GHOST_OFF

        // Check active rainbow mode (Easter egg shake)
        val now = System.currentTimeMillis()
        val isRainbow = now < rainbowModeEndTimeMs
        if (isRainbow) {
            postInvalidateOnAnimation()
        }

        val rainbowColor = if (isRainbow) {
            rainbowHsv[0] = ((now % 1000L) / 1000f) * 360f
            Color.HSVToColor(rainbowHsv)
        } else {
            0
        }

        val radius = dotRadiusPx

        // Iterate over pre-cached nodes with zero object allocations
        for (index in 0 until totalNodes) {
            val cached = cachedNodes[index]

            // Skip drawing NET_IN or NET_OUT nodes if network is idle
            if (cached.isNetNode && isNetworkIdle) {
                continue
            }

            val node = cached.node
            val ratio = animatedRatios[node.id] ?: 0.5f

            // Dynamic effects detection
            val isBatCharging = node.metric == MetricType.BAT && isCharging
            val isTempWarning = node.metric == MetricType.TEMP && rawTempCelsius >= 80.0f

            if (isBatCharging || isTempWarning) {
                postInvalidateOnAnimation()
            }

            // Determine active color without allocations
            val activeColorInt = when {
                isRainbow -> rainbowColor
                isTempWarning -> {
                    val strobeOn = ((now / 250L) % 2L) == 0L
                    if (strobeOn) COLOR_TEMP_ALERT else COLOR_TEMP_DIM
                }
                node.isDynamicColor -> {
                    if (ratio >= node.alertThreshold) node.alertColor else node.baseColor
                }
                else -> node.staticColor
            }

            renderDots(canvas, cached.dots, radius, activeColorInt, ratio, isBatCharging, now)
        }
    }

    private fun renderDots(
        canvas: Canvas,
        dots: Array<DotPoint>,
        dotRadius: Float,
        activeColor: Int,
        ratio: Float,
        isBatCharging: Boolean,
        now: Long
    ) {
        val n = dots.size
        val totalFill = ratio.coerceIn(0f, 1f) * n
        val fullCount = totalFill.toInt().coerceAtMost(n)

        // 1. Draw base background dots
        for (i in 0 until n) {
            val pt = dots[i]
            canvas.drawCircle(pt.x, pt.y, dotRadius, basePaint)
        }

        if (totalFill <= 0f) return

        // 2. Draw fully illuminated dots
        fillPaint.color = activeColor
        for (i in 0 until fullCount) {
            val pt = dots[i]
            val pulseMultiplier = if (isBatCharging) {
                ((sin((now + i * 180) / 250.0).toFloat() * 0.35f) + 0.65f).coerceIn(0.2f, 1.0f)
            } else {
                1.0f
            }
            fillPaint.alpha = (255 * pulseMultiplier).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x, pt.y, dotRadius, fillPaint)
        }

        // 3. Draw partial dot at threshold (if any)
        if (fullCount < n) {
            val fraction = totalFill - fullCount
            if (fraction > 0.01f) {
                val pt = dots[fullCount]
                val pulseMultiplier = if (isBatCharging) {
                    ((sin((now + fullCount * 180) / 250.0).toFloat() * 0.35f) + 0.65f).coerceIn(0.2f, 1.0f)
                } else {
                    1.0f
                }
                fillPaint.alpha = (fraction * 255f * pulseMultiplier).toInt().coerceIn(0, 255)
                canvas.drawCircle(pt.x, pt.y, dotRadius, fillPaint)
            }
        }
    }
}
