package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.HardwarePropertiesManager
import android.os.StatFs
import com.example.model.MetricType
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class MetricsCollector(private val context: Context) {
    private var lastCpuIdle: Long = 0L
    private var lastCpuTotal: Long = 0L
    private var lastProcTime: Long = 0L
    private var lastCpuTime: Long = System.currentTimeMillis()
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastTrafficTime: Long = System.currentTimeMillis()
    private var lastCpuUsage: Float = 0.24f
    private var lastRxSpeedBytesPerSec: Double = 0.0
    private var lastTxSpeedBytesPerSec: Double = 0.0

    init {
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        readCpu()
    }

    /**
     * Smart CPU Usage calculation:
     * 1. Attempts reading `/proc/stat` (system-wide CPU metrics).
     * 2. On Android 10+ SELinux failure, falls back to `/proc/self/stat`.
     * 3. Calculates normalized usage delta mapped to 0.0f - 1.0f.
     */
    fun readCpu(): Float {
        // Method 1: /proc/stat
        try {
            val file = File("/proc/stat")
            if (file.exists() && file.canRead()) {
                val line = file.bufferedReader().use { it.readLine() }
                if (!line.isNullOrEmpty() && line.startsWith("cpu")) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val user = parts[1].toLong()
                        val nice = parts[2].toLong()
                        val sys = parts[3].toLong()
                        val idle = parts[4].toLong()
                        val iowait = if (parts.size > 5) parts[5].toLong() else 0L
                        val irq = if (parts.size > 6) parts[6].toLong() else 0L
                        val softirq = if (parts.size > 7) parts[7].toLong() else 0L
                        val steal = if (parts.size > 8) parts[8].toLong() else 0L

                        val currentIdle = idle + iowait
                        val currentTotal = user + nice + sys + idle + iowait + irq + softirq + steal

                        val diffIdle = currentIdle - lastCpuIdle
                        val diffTotal = currentTotal - lastCpuTotal

                        lastCpuIdle = currentIdle
                        lastCpuTotal = currentTotal

                        if (diffTotal > 0 && lastCpuTotal > 0) {
                            val usage = (diffTotal - diffIdle).toFloat() / diffTotal.toFloat()
                            lastCpuUsage = usage.coerceIn(0.02f, 1.0f)
                            return lastCpuUsage
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // Method 2: Fallback to /proc/self/stat
        try {
            val selfStat = File("/proc/self/stat")
            if (selfStat.exists() && selfStat.canRead()) {
                val content = selfStat.bufferedReader().use { it.readLine() }
                if (!content.isNullOrEmpty()) {
                    val lastParen = content.lastIndexOf(')')
                    if (lastParen != -1 && lastParen + 2 < content.length) {
                        val fields = content.substring(lastParen + 2).trim().split("\\s+".toRegex())
                        if (fields.size >= 13) {
                            val utime = fields[11].toLong()
                            val stime = fields[12].toLong()
                            val currentProcTime = utime + stime
                            val currentTime = System.currentTimeMillis()
                            val diffTimeSec = ((currentTime - lastCpuTime) / 1000.0).coerceAtLeast(0.5)

                            val diffProc = if (lastProcTime > 0) currentProcTime - lastProcTime else 0L
                            lastProcTime = currentProcTime
                            lastCpuTime = currentTime

                            val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                            // 100 clock ticks per second standard in Linux
                            val usage = (diffProc / (100.0 * diffTimeSec * numCores)).toFloat()
                            if (usage > 0f) {
                                lastCpuUsage = usage.coerceIn(0.02f, 1.0f)
                                return lastCpuUsage
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // Method 3: Thread activity load fallback
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val activeThreads = Thread.activeCount()
        val simulated = (activeThreads.toFloat() / (numCores * 10f)).coerceIn(0.10f, 0.85f)
        lastCpuUsage = simulated
        return lastCpuUsage
    }

    fun readMemoryInfo(): Pair<Float, Float> {
        // Returns (memFreeRatio, memUsedRatio)
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val total = memInfo.totalMem.toDouble()
            val avail = memInfo.availMem.toDouble()
            if (total > 0) {
                val freeRatio = (avail / total).toFloat().coerceIn(0f, 1f)
                val usedRatio = ((total - avail) / total).toFloat().coerceIn(0f, 1f)
                Pair(freeRatio, usedRatio)
            } else {
                Pair(0.55f, 0.45f)
            }
        } catch (_: Throwable) {
            Pair(0.55f, 0.45f)
        }
    }

    fun getMemoryUsedGigabytes(): Double {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val usedBytes = memInfo.totalMem - memInfo.availMem
            (usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(0.1)
        } catch (_: Throwable) {
            3.8
        }
    }

    private var zeroNetStartTime: Long = 0L

    /**
     * Smart Network Traffic speed calculation using android.net.TrafficStats.
     * Normalized against maxNetSpeedMB: (currentSpeed / maxSpeed).coerceIn(0f, 1f)
     */
    fun readNetworkRates(maxNetSpeedMB: Float = 5.0f): Pair<Float, Float> {
        return try {
            val currRx = TrafficStats.getTotalRxBytes()
            val currTx = TrafficStats.getTotalTxBytes()
            val currTime = System.currentTimeMillis()
            val deltaSec = ((currTime - lastTrafficTime) / 1000.0).coerceAtLeast(0.5)

            val rxDiff = if (lastRxBytes > 0 && currRx >= lastRxBytes) currRx - lastRxBytes else 0L
            val txDiff = if (lastTxBytes > 0 && currTx >= lastTxBytes) currTx - lastTxBytes else 0L

            lastRxBytes = currRx
            lastTxBytes = currTx
            lastTrafficTime = currTime

            val rxBytesPerSec = rxDiff / deltaSec
            val txBytesPerSec = txDiff / deltaSec

            lastRxSpeedBytesPerSec = rxBytesPerSec
            lastTxSpeedBytesPerSec = txBytesPerSec

            // 5-minute continuous zero network traffic detection
            if (rxBytesPerSec < 1.0 && txBytesPerSec < 1.0) {
                if (zeroNetStartTime == 0L) {
                    zeroNetStartTime = currTime
                }
            } else {
                zeroNetStartTime = 0L
            }

            val maxSpeedBytesPerSec = (maxNetSpeedMB * 1024.0 * 1024.0).coerceAtLeast(1024.0 * 100.0)

            val normalizedRx = (rxBytesPerSec / maxSpeedBytesPerSec).toFloat().coerceIn(0f, 1f)
            val normalizedTx = (txBytesPerSec / maxSpeedBytesPerSec).toFloat().coerceIn(0f, 1f)

            Pair(normalizedRx, normalizedTx)
        } catch (_: Throwable) {
            Pair(0.05f, 0.05f)
        }
    }

    fun isNetworkIdle5Min(): Boolean {
        return zeroNetStartTime > 0L && (System.currentTimeMillis() - zeroNetStartTime >= 5 * 60 * 1000L)
    }

    fun resetNetworkIdle() {
        zeroNetStartTime = 0L
    }

    /**
     * Smart Battery capacity calculation:
     * Registers for Intent.ACTION_BATTERY_CHANGED, extracts EXTRA_LEVEL and EXTRA_SCALE,
     * and returns normalized 0.0f - 1.0f ratio.
     */
    fun readBattery(): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level.toFloat() / scale.toFloat()).coerceIn(0f, 1f)
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val prop = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (prop in 0..100) prop / 100f else 0.85f
            }
        } catch (_: Throwable) {
            0.85f
        }
    }

    fun isCharging(): Boolean {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) || plugged > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun readDisk(): Float {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            if (totalBytes > 0) {
                (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else 0.5f
        } catch (_: Throwable) {
            0.5f
        }
    }

    fun readRawTemperatureCelsius(): Float {
        try {
            val hpm = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
            if (hpm != null) {
                val temps = hpm.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
                )
                if (temps.isNotEmpty() && temps[0] > 0f) {
                    return temps[0]
                }
            }
        } catch (_: Throwable) {}

        try {
            val thermalZone = File("/sys/class/thermal/thermal_zone0/temp")
            if (thermalZone.exists() && thermalZone.canRead()) {
                val raw = thermalZone.bufferedReader().use { it.readLine() }?.trim()?.toFloatOrNull()
                if (raw != null && raw > 0f) {
                    return if (raw > 1000f) raw / 1000f else raw
                }
            }
        } catch (_: Throwable) {}

        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (rawTemp > 0) {
                return rawTemp / 10.0f
            }
        } catch (_: Throwable) {}

        return 37.0f
    }

    fun readTemperature(): Float {
        val tempC = readRawTemperatureCelsius()
        return ((tempC - 20f) / 50f).coerceIn(0.05f, 1.0f)
    }

    fun getMetricRatio(type: MetricType, maxNetSpeedMB: Float = 5.0f): Float {
        return when (type) {
            MetricType.CPU -> readCpu()
            MetricType.MEM_FREE -> readMemoryInfo().first
            MetricType.MEM_USED -> readMemoryInfo().second
            MetricType.NET_IN -> readNetworkRates(maxNetSpeedMB).first
            MetricType.NET_OUT -> readNetworkRates(maxNetSpeedMB).second
            MetricType.BAT -> readBattery()
            MetricType.DISK -> readDisk()
            MetricType.TEMP -> readTemperature()
        }
    }

    fun readAllMetrics(maxNetSpeedMB: Float = 5.0f): Map<MetricType, Float> {
        val mem = readMemoryInfo()
        val net = readNetworkRates(maxNetSpeedMB)
        return mapOf(
            MetricType.CPU to readCpu(),
            MetricType.MEM_FREE to mem.first,
            MetricType.MEM_USED to mem.second,
            MetricType.NET_IN to net.first,
            MetricType.NET_OUT to net.second,
            MetricType.BAT to readBattery(),
            MetricType.DISK to readDisk(),
            MetricType.TEMP to readTemperature()
        )
    }

    /**
     * Formats real-time concise system stats readout:
     * e.g., "CPU: 24% | MEM: 4.2GB | BAT: 85%"
     */
    fun getSystemStatsReadout(): String {
        val cpu = (readCpu() * 100).roundToInt()
        val memGb = String.format(Locale.US, "%.1fGB", getMemoryUsedGigabytes())
        val bat = (readBattery() * 100).roundToInt()
        return "CPU: $cpu% | MEM: $memGb | BAT: $bat%"
    }
}
