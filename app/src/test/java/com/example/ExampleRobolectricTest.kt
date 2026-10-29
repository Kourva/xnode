package com.example

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.example.model.MetricType
import com.example.model.NodeConfig
import com.example.model.NodeDirection
import com.example.model.NodeShape
import com.example.model.NotchConfig
import com.example.model.NotchPreferences
import com.example.service.MetricsCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("xnode", appName)
        val appDesc = context.getString(R.string.app_desc)
        assertEquals("system resource telemetry HUD", appDesc)
    }

    @Test
    fun `verify notification drawable resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_notification)
        assertNotNull(drawable)
    }

    @Test
    fun `verify launcher background is pure white`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val color = androidx.core.content.ContextCompat.getColor(context, R.color.ic_launcher_background)
        assertEquals(0xFFFFFFFF.toInt(), color)
    }

    @Test
    fun `verify font loading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ndot = ResourcesCompat.getFont(context, R.font.ndot_55)
        assertNotNull(ndot)

        val geist = ResourcesCompat.getFont(context, R.font.geist_regular)
        assertNotNull(geist)

        val geistMono = ResourcesCompat.getFont(context, R.font.geist_mono)
        assertNotNull(geistMono)
    }

    @Test
    fun `default dynamic nodes list configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = NotchPreferences.loadConfig(context)
        assertNotNull(config)
        assertTrue(config.nodes.isNotEmpty())
        assertTrue(config.pocketModeEnabled)
        assertEquals(5.0f, config.maxNetSpeedMB, 0.01f)
        assertEquals(2, config.updateIntervalSeconds)

        val first = config.nodes[0]
        assertEquals(MetricType.CPU, first.metric)
        assertEquals(NodeShape.ARC, first.shape)
        assertEquals(NodeDirection.LEFT, first.direction)
        assertEquals(5, first.dotCount)
    }

    @Test
    fun `save and load dynamic nodes with json persistence, pocket mode, and dot counts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testNodes = listOf(
            NodeConfig(
                id = "test_node_1",
                name = "Node 1",
                enabled = true,
                metric = MetricType.MEM_USED,
                shape = NodeShape.ARC,
                direction = NodeDirection.LEFT,
                dotCount = 3,
                sizeDp = 35.0f,
                positionXDp = 12.0f,
                positionYDp = -5.0f,
                isDynamicColor = true,
                baseColor = 0xFF34C759.toInt(),
                alertColor = 0xFFFF3B30.toInt(),
                alertThreshold = 0.85f
            ),
            NodeConfig(
                id = "test_node_2",
                name = "Node 2",
                enabled = true,
                metric = MetricType.TEMP,
                shape = NodeShape.STRAIGHT,
                direction = NodeDirection.RIGHT,
                dotCount = 4,
                sizeDp = 40.0f,
                positionXDp = 0.0f,
                positionYDp = 0.0f,
                isDynamicColor = false,
                staticColor = 0xFF0A84FF.toInt()
            )
        )

        val newConfig = NotchConfig(
            pocketModeEnabled = false,
            ghostMode = true,
            maxNetSpeedMB = 10.0f,
            updateIntervalSeconds = 4,
            nodes = testNodes
        )

        NotchPreferences.saveConfig(context, newConfig)
        val loaded = NotchPreferences.loadConfig(context)

        assertEquals(false, loaded.pocketModeEnabled)
        assertTrue(loaded.ghostMode)
        assertEquals(10.0f, loaded.maxNetSpeedMB, 0.01f)
        assertEquals(4, loaded.updateIntervalSeconds)
        assertEquals(2, loaded.nodes.size)

        val n1 = loaded.nodes[0]
        assertEquals("test_node_1", n1.id)
        assertEquals(3, n1.dotCount)
        assertEquals(MetricType.MEM_USED, n1.metric)
        assertEquals(NodeShape.ARC, n1.shape)
        assertEquals(NodeDirection.LEFT, n1.direction)
        assertEquals(35.0f, n1.sizeDp, 0.01f)
        assertEquals(12.0f, n1.positionXDp, 0.01f)
        assertEquals(-5.0f, n1.positionYDp, 0.01f)
        assertTrue(n1.isDynamicColor)
        assertEquals(0.85f, n1.alertThreshold, 0.01f)

        val n2 = loaded.nodes[1]
        assertEquals("test_node_2", n2.id)
        assertEquals(4, n2.dotCount)
        assertEquals(MetricType.TEMP, n2.metric)
        assertEquals(NodeShape.STRAIGHT, n2.shape)
        assertEquals(NodeDirection.RIGHT, n2.direction)
        assertEquals(40.0f, n2.sizeDp, 0.01f)
    }

    @Test
    fun `metrics collector reads all 8 metric types and formats system stats readout`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val collector = MetricsCollector(context)
        val allMetrics = collector.readAllMetrics(maxNetSpeedMB = 5.0f)

        assertEquals(8, allMetrics.size)
        MetricType.entries.forEach { metric ->
            assertTrue(allMetrics.containsKey(metric))
            val value = allMetrics[metric] ?: -1f
            assertTrue("Value for $metric should be between 0 and 1, got $value", value in 0f..1f)
        }

        val readout = collector.getSystemStatsReadout()
        assertNotNull(readout)
        assertTrue("Readout should contain 'CPU:', got '$readout'", readout.contains("CPU:"))
        assertTrue("Readout should contain 'MEM:', got '$readout'", readout.contains("MEM:"))
        assertTrue("Readout should contain 'BAT:', got '$readout'", readout.contains("BAT:"))

        val tempCelsius = collector.readRawTemperatureCelsius()
        assertTrue("Temp celsius should be positive, got $tempCelsius", tempCelsius > 0f)

        // Verify charging check doesn't throw
        val charging = collector.isCharging()
        assertTrue(charging == true || charging == false)
    }
}
