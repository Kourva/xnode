package com.example.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MetricType(val id: String, val label: String) {
    CPU("CPU", "CPU"),
    MEM_FREE("MEM_FREE", "MEM FREE"),
    MEM_USED("MEM_USED", "MEM USED"),
    NET_IN("NET_IN", "NET IN"),
    NET_OUT("NET_OUT", "NET OUT"),
    BAT("BAT", "BAT"),
    DISK("DISK", "DISK"),
    TEMP("TEMP", "TEMP");

    companion object {
        fun fromId(id: String): MetricType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: CPU
        }
    }
}

enum class NodeShape(val label: String) {
    ARC("Arc"),
    STRAIGHT("Straight");

    companion object {
        fun fromString(value: String): NodeShape {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ARC
        }
    }
}

enum class NodeDirection(val label: String) {
    LEFT("Left"),
    RIGHT("Right");

    companion object {
        fun fromString(value: String): NodeDirection {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LEFT
        }
    }
}

data class NodeConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val metric: MetricType = MetricType.CPU,
    val shape: NodeShape = NodeShape.ARC,
    val direction: NodeDirection = NodeDirection.LEFT,
    val dotCount: Int = DEFAULT_DOT_COUNT,
    val sizeDp: Float = MIN_SIZE_DP,
    val positionXDp: Float = DEFAULT_POSITION_X,
    val positionYDp: Float = DEFAULT_POSITION_Y,
    val isDynamicColor: Boolean = false,
    val staticColor: Int = DEFAULT_STATIC_COLOR,
    val baseColor: Int = DEFAULT_STATIC_COLOR,
    val alertColor: Int = DEFAULT_ALERT_COLOR,
    val alertThreshold: Float = DEFAULT_ALERT_THRESHOLD
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("enabled", enabled)
        json.put("metric", metric.id)
        json.put("shape", shape.name)
        json.put("direction", direction.name)
        json.put("dotCount", dotCount)
        json.put("sizeDp", sizeDp.toDouble())
        json.put("positionXDp", positionXDp.toDouble())
        json.put("positionYDp", positionYDp.toDouble())
        json.put("isDynamicColor", isDynamicColor)
        json.put("staticColor", staticColor)
        json.put("baseColor", baseColor)
        json.put("alertColor", alertColor)
        json.put("alertThreshold", alertThreshold.toDouble())
        return json
    }

    companion object {
        const val MIN_DOT_COUNT = 2
        const val MAX_DOT_COUNT = 5
        const val DEFAULT_DOT_COUNT = 5
        const val MIN_SIZE_DP = 20.0f
        const val MAX_SIZE_DP = 150.0f
        const val MIN_OFFSET_DP = -150.0f
        const val MAX_OFFSET_DP = 150.0f
        const val DEFAULT_POSITION_X = 0.0f
        const val DEFAULT_POSITION_Y = 0.0f
        const val DEFAULT_STATIC_COLOR = 0xFF34C759.toInt() // Green
        const val DEFAULT_ALERT_COLOR = 0xFFFF3B30.toInt()  // Red
        const val DEFAULT_ALERT_THRESHOLD = 0.80f

        fun createNew(index: Int): NodeConfig {
            val direction = if (index % 2 == 0) NodeDirection.LEFT else NodeDirection.RIGHT
            val metric = if (index == 0) MetricType.CPU else if (index == 1) MetricType.BAT else MetricType.MEM_USED
            return NodeConfig(
                id = UUID.randomUUID().toString(),
                name = "Node ${index + 1}",
                enabled = true,
                metric = metric,
                shape = NodeShape.ARC,
                direction = direction,
                dotCount = DEFAULT_DOT_COUNT,
                sizeDp = MIN_SIZE_DP,
                positionXDp = DEFAULT_POSITION_X,
                positionYDp = DEFAULT_POSITION_Y,
                isDynamicColor = false,
                staticColor = if (index % 2 == 0) 0xFF34C759.toInt() else 0xFF0A84FF.toInt(),
                baseColor = 0xFF34C759.toInt(),
                alertColor = DEFAULT_ALERT_COLOR,
                alertThreshold = DEFAULT_ALERT_THRESHOLD
            )
        }
        fun fromJson(json: JSONObject): NodeConfig {
            return NodeConfig(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Node"),
                enabled = json.optBoolean("enabled", true),
                metric = MetricType.fromId(json.optString("metric", "CPU")),
                shape = NodeShape.fromString(json.optString("shape", "ARC")),
                direction = NodeDirection.fromString(json.optString("direction", "LEFT")),
                dotCount = json.optInt("dotCount", DEFAULT_DOT_COUNT).coerceIn(MIN_DOT_COUNT, MAX_DOT_COUNT),
                sizeDp = json.optDouble("sizeDp", MIN_SIZE_DP.toDouble()).toFloat().coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
                positionXDp = json.optDouble("positionXDp", DEFAULT_POSITION_X.toDouble()).toFloat().coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP),
                positionYDp = json.optDouble("positionYDp", DEFAULT_POSITION_Y.toDouble()).toFloat().coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP),
                isDynamicColor = json.optBoolean("isDynamicColor", false),
                staticColor = json.optInt("staticColor", DEFAULT_STATIC_COLOR),
                baseColor = json.optInt("baseColor", DEFAULT_STATIC_COLOR),
                alertColor = json.optInt("alertColor", DEFAULT_ALERT_COLOR),
                alertThreshold = json.optDouble("alertThreshold", DEFAULT_ALERT_THRESHOLD.toDouble()).toFloat().coerceIn(0.1f, 1.0f)
            )
        }
    }
}

data class NotchConfig(
    val pocketModeEnabled: Boolean = true,
    val ghostMode: Boolean = false,
    val maxNetSpeedMB: Float = 5.0f,
    val updateIntervalSeconds: Int = 2,
    val nodes: List<NodeConfig> = emptyList()
)

object NotchPreferences {
    private const val PREFS_NAME = "xnode_dynamic_nodes_v4"
    private const val KEY_POCKET_MODE = "pocket_mode"
    private const val KEY_GHOST_MODE = "ghost_mode"
    private const val KEY_MAX_NET_SPEED = "max_net_speed_mb"
    private const val KEY_UPDATE_INTERVAL = "update_interval_sec"
    private const val KEY_NODES_JSON = "nodes_json"

    val COLOR_PALETTE = listOf(
        0xFFFF3B30.toInt(), // Red #FF3B30
        0xFF34C759.toInt(), // Green #34C759
        0xFF0A84FF.toInt(), // Blue #0A84FF
        0xFFFF9500.toInt(), // Orange #FF9500
        0xFFFFFFFF.toInt(), // White #FFFFFF
        0xFFAF52DE.toInt(), // Purple #AF52DE
        0xFF5AC8FA.toInt(), // Cyan #5AC8FA
        0xFFFFCC00.toInt()  // Amber #FFCC00
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadConfig(context: Context): NotchConfig {
        val prefs = getPrefs(context)
        val pocketMode = prefs.getBoolean(KEY_POCKET_MODE, true)
        val ghostMode = prefs.getBoolean(KEY_GHOST_MODE, false)
        val maxNetSpeed = prefs.getFloat(KEY_MAX_NET_SPEED, 5.0f)
        val updateInterval = prefs.getInt(KEY_UPDATE_INTERVAL, 2).coerceIn(1, 10)
        val nodesJsonStr = prefs.getString(KEY_NODES_JSON, null)

        val nodes = mutableListOf<NodeConfig>()
        if (!nodesJsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(nodesJsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    nodes.add(NodeConfig.fromJson(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If no nodes found, create starter calibrated nodes
        if (nodes.isEmpty()) {
            nodes.add(
                NodeConfig(
                    id = "node_default_left",
                    name = "Node 1",
                    enabled = true,
                    metric = MetricType.CPU,
                    shape = NodeShape.ARC,
                    direction = NodeDirection.LEFT,
                    sizeDp = 26.0f,
                    positionXDp = 17.0f,
                    positionYDp = -2.0f,
                    isDynamicColor = false,
                    staticColor = 0xFF34C759.toInt()
                )
            )
            nodes.add(
                NodeConfig(
                    id = "node_default_right",
                    name = "Node 2",
                    enabled = true,
                    metric = MetricType.BAT,
                    shape = NodeShape.ARC,
                    direction = NodeDirection.RIGHT,
                    sizeDp = 26.0f,
                    positionXDp = -17.0f,
                    positionYDp = -2.0f,
                    isDynamicColor = false,
                    staticColor = 0xFF0A84FF.toInt()
                )
            )
        }

        return NotchConfig(
            pocketModeEnabled = pocketMode,
            ghostMode = ghostMode,
            maxNetSpeedMB = maxNetSpeed.coerceIn(0.1f, 50.0f),
            updateIntervalSeconds = updateInterval,
            nodes = nodes
        )
    }

    fun saveConfig(context: Context, config: NotchConfig) {
        val array = JSONArray()
        config.nodes.forEach { node ->
            array.put(node.toJson())
        }

        getPrefs(context).edit()
            .putBoolean(KEY_POCKET_MODE, config.pocketModeEnabled)
            .putBoolean(KEY_GHOST_MODE, config.ghostMode)
            .putFloat(KEY_MAX_NET_SPEED, config.maxNetSpeedMB)
            .putInt(KEY_UPDATE_INTERVAL, config.updateIntervalSeconds.coerceIn(1, 10))
            .putString(KEY_NODES_JSON, array.toString())
            .apply()
    }
}
