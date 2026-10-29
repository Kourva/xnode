package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.model.MetricType
import com.example.model.NotchPreferences
import com.example.service.MetricsCollector
import com.example.service.NotchOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: NotchOverlayView? = null
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var metricsCollector: MetricsCollector

    private var isScreenOn: Boolean = true
    private var isLandscape: Boolean = false
    private var isCoveredInPocket: Boolean = false
    private var lastShakeTime: Long = 0L

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshOverlayConfig()
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> {
                    val distance = event.values.getOrNull(0) ?: 100f
                    val currentConfig = NotchPreferences.loadConfig(this@OverlayService)
                    if (currentConfig.pocketModeEnabled) {
                        val maxRange = event.sensor.maximumRange.coerceAtMost(5.0f)
                        val covered = distance < 1.0f || distance < maxRange
                        if (covered != isCoveredInPocket) {
                            isCoveredInPocket = covered
                            if (covered) {
                                pauseMetricsLoop()
                                overlayView?.stopAllAnimations()
                                overlayView?.visibility = View.GONE
                            } else {
                                if (isScreenOn && !isLandscape) {
                                    overlayView?.visibility = View.VISIBLE
                                    resumeMetricsLoop()
                                }
                            }
                        }
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH
                    if (gForce > 2.5f) {
                        val now = System.currentTimeMillis()
                        if (now - lastShakeTime > 3000L) {
                            lastShakeTime = now
                            overlayView?.triggerRainbowMode(3000L)
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    pauseMetricsLoop()
                    overlayView?.stopAllAnimations()
                    overlayView?.visibility = View.GONE
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    metricsCollector.resetNetworkIdle()
                    if (!isLandscape && !isCoveredInPocket) {
                        overlayView?.visibility = View.VISIBLE
                        resumeMetricsLoop()
                    }
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    val currentOrientation = resources.configuration.orientation
                    handleOrientationChanged(currentOrientation)
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "xnode_telemetry_channel"
        const val NOTIFICATION_ID = 4096

        private var activeInstance: OverlayService? = null

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _metricRatios = MutableStateFlow<Map<MetricType, Float>>(emptyMap())
        val metricRatios = _metricRatios.asStateFlow()

        private val _systemStatsSummary = MutableStateFlow("CPU: -- | MEM: -- | BAT: --")
        val systemStatsSummary = _systemStatsSummary.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }

        fun triggerPreview(nodeId: String) {
            activeInstance?.overlayView?.triggerPreview(nodeId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        metricsCollector = MetricsCollector(this)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createNotificationChannel()
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        setupOverlayWindow()
        NotchPreferences.getPrefs(this).registerOnSharedPreferenceChangeListener(prefListener)

        // Sensors registration (Proximity for Pocket Mode & Accelerometer for Shake Rainbow)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        proximitySensor?.let {
            sensorManager?.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager?.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        registerReceiver(systemStateReceiver, filter)

        // Initial orientation check (Game Mode / Landscape)
        handleOrientationChanged(resources.configuration.orientation)

        _isRunning.value = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handleOrientationChanged(newConfig.orientation)
    }

    /**
     * Game Mode (Landscape Hide):
     * If device is in landscape orientation, hide the overlay immediately and pause fetching
     * so it never obstructs games, videos, or camera previews.
     * When returning to portrait, restore overlay and resume fetching.
     */
    private fun handleOrientationChanged(orientation: Int) {
        isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            overlayView?.stopAllAnimations()
            overlayView?.visibility = View.GONE
            pauseMetricsLoop()
        } else {
            if (isScreenOn) {
                overlayView?.visibility = View.VISIBLE
                resumeMetricsLoop()
            }
        }
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = NotchOverlayView(this).apply {
            config = NotchPreferences.loadConfig(this@OverlayService)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun refreshOverlayConfig() {
        val config = NotchPreferences.loadConfig(this)
        overlayView?.config = config
    }

    private fun pauseMetricsLoop() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun resumeMetricsLoop() {
        if (updateJob?.isActive == true || isLandscape || !isScreenOn || isCoveredInPocket) return
        updateJob = serviceScope.launch {
            while (isActive) {
                val currentConfig = NotchPreferences.loadConfig(this@OverlayService)
                overlayView?.config = currentConfig

                val metrics = withContext(Dispatchers.IO) {
                    metricsCollector.readAllMetrics(currentConfig.maxNetSpeedMB)
                }

                // Low Battery Auto-Kill: if battery < 5%, terminate service to save power
                val batRatio = metrics[MetricType.BAT] ?: 1.0f
                if (batRatio < 0.05f) {
                    stopSelf()
                    break
                }

                val summary = withContext(Dispatchers.IO) {
                    metricsCollector.getSystemStatsReadout()
                }

                val isCharging = withContext(Dispatchers.IO) { metricsCollector.isCharging() }
                val rawTemp = withContext(Dispatchers.IO) { metricsCollector.readRawTemperatureCelsius() }
                val isNetIdle = withContext(Dispatchers.IO) { metricsCollector.isNetworkIdle5Min() }

                _metricRatios.value = metrics
                _systemStatsSummary.value = summary
                overlayView?.updateMetricRatios(
                    metricsMap = metrics,
                    charging = isCharging,
                    tempCelsius = rawTemp,
                    netIdle = isNetIdle
                )

                // Network Idle Sleep: if idle, poll slower to conserve resources
                val loopDelay = if (isNetIdle) 3500L else 1500L
                delay(loopDelay)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "xnode telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time dot-matrix metrics overlay around screen cutout"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("xnode")
            .setContentText("system resource telemetry HUD")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        _isRunning.value = false
        pauseMetricsLoop()
        serviceScope.cancel()

        try {
            sensorManager?.unregisterListener(sensorEventListener)
        } catch (_: Exception) {}

        try {
            unregisterReceiver(systemStateReceiver)
        } catch (_: Exception) {}

        try {
            NotchPreferences.getPrefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Exception) {}

        overlayView?.let {
            try {
                it.stopAllAnimations()
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }
}
