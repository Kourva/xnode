package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.model.MetricType
import com.example.model.NodeConfig
import com.example.model.NodeDirection
import com.example.model.NodeShape
import com.example.model.NotchConfig
import com.example.model.NotchPreferences
import com.example.service.MetricsCollector
import com.example.ui.GlobalSettingsScreen
import com.example.ui.rememberHapticFeedbackHelper
import com.example.ui.theme.GeistMonoFontFamily
import com.example.ui.theme.MainSystemFontFamily
import com.example.ui.theme.NDotFontFamily
import com.example.ui.theme.XNodeTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var extraDarkEnabled by remember { mutableStateOf(true) }

            XNodeTheme(
                darkTheme = systemDark,
                extraDark = extraDarkEnabled
            ) {
                MainContent(
                    extraDarkEnabled = extraDarkEnabled,
                    onToggleExtraDark = { extraDarkEnabled = it }
                )
            }
        }
    }
}

@Composable
fun MainContent(
    extraDarkEnabled: Boolean,
    onToggleExtraDark: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var config by remember { mutableStateOf(NotchPreferences.loadConfig(context)) }

    fun updateConfig(newConfig: NotchConfig) {
        config = newConfig
        NotchPreferences.saveConfig(context, newConfig)
    }

    NavHost(
        navController = navController,
        startDestination = "main",
        enterTransition = { slideInHorizontally { it } },
        exitTransition = { slideOutHorizontally { -it / 3 } },
        popEnterTransition = { slideInHorizontally { -it / 3 } },
        popExitTransition = { slideOutHorizontally { it } }
    ) {
        composable("main") {
            SettingsScreen(
                config = config,
                onUpdateConfig = { updateConfig(it) },
                onOpenGlobalSettings = { navController.navigate("global_settings") }
            )
        }
        composable("global_settings") {
            GlobalSettingsScreen(
                config = config,
                extraDarkEnabled = extraDarkEnabled,
                onToggleExtraDark = onToggleExtraDark,
                onUpdateConfig = { updateConfig(it) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    config: NotchConfig,
    onUpdateConfig: (NotchConfig) -> Unit,
    onOpenGlobalSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = XNodeTheme.colors
    val haptic = rememberHapticFeedbackHelper()

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isRunning by OverlayService.isRunning.collectAsState()
    val liveMetrics by OverlayService.metricRatios.collectAsState()
    val liveStatsSummary by OverlayService.systemStatsSummary.collectAsState()

    val metricsCollector = remember { MetricsCollector(context) }
    val previewMetrics = remember { mutableStateMapOf<MetricType, Float>() }
    var localStatsSummary by remember { mutableStateOf("CPU: 24% | MEM: 4.2GB | BAT: 85%") }

    LaunchedEffect(isRunning, config.maxNetSpeedMB) {
        while (!isRunning) {
            val fresh = metricsCollector.readAllMetrics(config.maxNetSpeedMB)
            fresh.forEach { (type, value) ->
                previewMetrics[type] = value
            }
            localStatsSummary = metricsCollector.getSystemStatsReadout()
            delay(1500L)
        }
    }

    fun getMetricRatio(type: MetricType): Float {
        return if (isRunning) {
            liveMetrics[type] ?: 0.5f
        } else {
            previewMetrics[type] ?: 0.5f
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        OverlayService.start(context)
    }

    fun handleToggleOverlay() {
        if (isRunning) {
            OverlayService.stop(context)
        } else {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(
                    context,
                    "Please grant 'Display over other apps' for xnode",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            OverlayService.start(context)
        }
    }

    fun openGitHub() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kourva/xnode")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    Scaffold(
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
        ) {
            // Scrollable Column for Header and Dynamic Node List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Transparent background
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "XNODE".uppercase(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textPrimary,
                                fontFamily = NDotFontFamily,
                                modifier = Modifier.testTag("app_title")
                            )
                            Text(
                                text = "system resource telemetry.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF8A8A8A),
                                fontFamily = MainSystemFontFamily
                            )
                        }

                        // Header Action Icons: GitHub and Settings (Gear)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.click()
                                    openGitHub()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("header_github_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_github),
                                    contentDescription = "Open GitHub repository",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.click()
                                    onOpenGlobalSettings()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("open_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Global Settings",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Real-Time System Stats Readout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("system_stats_box")
                    ) {
                        Text(
                            text = if (isRunning) liveStatsSummary else localStatsSummary,
                            fontSize = 12.sp,
                            fontFamily = GeistMonoFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF8A8A8A),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.testTag("system_stats_readout")
                        )
                    }
                }

                // Dynamic Node List (gray #1A1A1A surface cards, 16dp radius, no borders)
                config.nodes.forEachIndexed { index, node ->
                    DynamicNodeCard(
                        node = node,
                        index = index,
                        currentRatio = getMetricRatio(node.metric),
                        onUpdate = { updatedNode ->
                            val newNodes = config.nodes.toMutableList()
                            newNodes[index] = updatedNode
                            onUpdateConfig(config.copy(nodes = newNodes))
                        },
                        onDelete = {
                            val newNodes = config.nodes.toMutableList()
                            newNodes.removeAt(index)
                            onUpdateConfig(config.copy(nodes = newNodes))
                        }
                    )
                }

                if (!hasOverlayPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Permission required:\nSYSTEM_ALERT_WINDOW is needed to draw over the camera notch. Tap 'Start overlay' to grant.",
                            fontSize = 12.sp,
                            fontFamily = MainSystemFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFFFF9500),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Top fade overlay (gradient from #000000 to transparent) so status bar icons remain visible
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xDD000000),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Sticky Footer (Aligned to BottomCenter)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC000000),
                                Color(0xFF000000),
                                Color(0xFF000000)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: Action Buttons (Start/Stop pill button + Add node circle button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Button: Start/Stop pill shape
                    Button(
                        onClick = {
                            haptic.click()
                            handleToggleOverlay()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("toggle_overlay_button"),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFFF3B30) else Color(0xFFFFFFFF),
                            contentColor = if (isRunning) Color(0xFFFFFFFF) else Color(0xFF000000)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = if (isRunning) "Stop overlay" else "Start overlay",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MainSystemFontFamily,
                            color = if (isRunning) Color(0xFFFFFFFF) else Color(0xFF000000)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right Button: Perfect circle (44dp x 44dp), surface background #1A1A1A, white + icon
                    IconButton(
                        onClick = {
                            haptic.click()
                            val newNode = NodeConfig.createNew(config.nodes.size)
                            onUpdateConfig(config.copy(nodes = config.nodes + newNode))
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                            .testTag("add_node_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add New Node",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Row 2: Footer Label
                Text(
                    text = "XNODE // AI-GENERATED, INSPIRED BY NOTHING, BY KOURVA",
                    fontSize = 10.sp,
                    fontFamily = GeistMonoFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF8A8A8A),
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.click()
                            openGitHub()
                        }
                        .padding(top = 10.dp, bottom = 4.dp)
                        .testTag("footer_label")
                )
            }
        }
    }
}

@Composable
fun DynamicNodeCard(
    node: NodeConfig,
    index: Int,
    currentRatio: Float,
    onUpdate: (NodeConfig) -> Unit,
    onDelete: () -> Unit
) {
    val colors = XNodeTheme.colors
    val haptic = rememberHapticFeedbackHelper()
    var expanded by remember { mutableStateOf(true) }

    // Nothing OS Style: Surface gray background, 16dp rounded corners, outer border REMOVED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Node Header: Title, Telemetry %, Expand/Collapse, Enable Switch, Delete Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable {
                    haptic.click()
                    expanded = !expanded
                }
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle expand",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Node ${index + 1}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MainSystemFontFamily,
                    color = colors.textPrimary
                )
                Text(
                    text = "${node.metric.label}: ${(currentRatio * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = NDotFontFamily,
                    color = Color(if (node.isDynamicColor && currentRatio >= node.alertThreshold) node.alertColor else if (node.isDynamicColor) node.baseColor else node.staticColor)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Preview button (1-second sweep)
                IconButton(
                    onClick = {
                        haptic.click()
                        OverlayService.triggerPreview(node.id)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("node_${index}_preview")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Preview node animation",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Enable/Disable switch
                Switch(
                    checked = node.enabled,
                    onCheckedChange = {
                        haptic.click()
                        onUpdate(node.copy(enabled = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF000000),
                        checkedTrackColor = Color(0xFFE8E8E8),
                        uncheckedThumbColor = Color(0xFF8A8A8A),
                        uncheckedTrackColor = Color(0xFF2A2A2A)
                    ),
                    modifier = Modifier.testTag("node_${index}_toggle_enabled")
                )

                // Delete button
                IconButton(
                    onClick = {
                        haptic.click()
                        onDelete()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("node_${index}_delete")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete node",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (expanded) {
            // 1. Data Source Grid (Row 1: CPU, MEM FREE, MEM USED, NET IN, NET OUT. Row 2: BAT, DISK, TEMP)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Data source",
                    fontSize = 11.sp,
                    fontFamily = MainSystemFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary
                )

                val row1 = listOf(MetricType.CPU, MetricType.MEM_FREE, MetricType.MEM_USED, MetricType.NET_IN, MetricType.NET_OUT)
                val row2 = listOf(MetricType.BAT, MetricType.DISK, MetricType.TEMP)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    row1.forEachIndexed { i, metric ->
                        val isSelected = metric == node.metric
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(
                                    if (isSelected) {
                                        if (colors.isDark) Color(0xFFE8E8E8) else Color(0xFF0D0D0D)
                                    } else {
                                        colors.innerCard
                                    }
                                )
                                .clickable {
                                    haptic.click()
                                    onUpdate(node.copy(metric = metric))
                                }
                                .testTag("node_${index}_metric_${metric.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = metric.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = MainSystemFontFamily,
                                color = if (isSelected) {
                                    if (colors.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
                                } else {
                                    colors.textSecondary
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                        if (i < row1.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(colors.border)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    row2.forEachIndexed { i, metric ->
                        val isSelected = metric == node.metric
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(
                                    if (isSelected) {
                                        if (colors.isDark) Color(0xFFE8E8E8) else Color(0xFF0D0D0D)
                                    } else {
                                        colors.innerCard
                                    }
                                )
                                .clickable {
                                    haptic.click()
                                    onUpdate(node.copy(metric = metric))
                                }
                                .testTag("node_${index}_metric_${metric.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = metric.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = MainSystemFontFamily,
                                color = if (isSelected) {
                                    if (colors.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
                                } else {
                                    colors.textSecondary
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                        if (i < row2.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(colors.border)
                            )
                        }
                    }
                }
            }

            // 2. Shape Toggle & Direction Toggle (Segmented controls filling full width with weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Shape Toggle: Arc or Straight (Full-width segmented control)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Shape",
                        fontSize = 11.sp,
                        fontFamily = MainSystemFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    ) {
                        NodeShape.entries.forEachIndexed { i, s ->
                            val isSel = s == node.shape
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(if (isSel) (if (colors.isDark) Color(0xFFE8E8E8) else Color(0xFF0D0D0D)) else colors.innerCard)
                                    .clickable {
                                        haptic.click()
                                        onUpdate(node.copy(shape = s))
                                    }
                                    .testTag("node_${index}_shape_${s.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = s.label,
                                    fontSize = 11.sp,
                                    fontFamily = MainSystemFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isSel) (if (colors.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)) else colors.textSecondary
                                )
                            }
                            if (i < NodeShape.entries.size - 1) {
                                Spacer(modifier = Modifier.width(1.dp).height(34.dp).background(colors.border))
                            }
                        }
                    }
                }

                // Direction Toggle: Left or Right (Full-width segmented control)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Direction",
                        fontSize = 11.sp,
                        fontFamily = MainSystemFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    ) {
                        NodeDirection.entries.forEachIndexed { i, d ->
                            val isSel = d == node.direction
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(if (isSel) (if (colors.isDark) Color(0xFFE8E8E8) else Color(0xFF0D0D0D)) else colors.innerCard)
                                    .clickable {
                                        haptic.click()
                                        onUpdate(node.copy(direction = d))
                                    }
                                    .testTag("node_${index}_direction_${d.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d.label,
                                    fontSize = 11.sp,
                                    fontFamily = MainSystemFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isSel) (if (colors.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)) else colors.textSecondary
                                )
                            }
                            if (i < NodeDirection.entries.size - 1) {
                                Spacer(modifier = Modifier.width(1.dp).height(34.dp).background(colors.border))
                            }
                        }
                    }
                }
            }

            // 3. Dynamic Dot Count (2 to 5 dots)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Dot count",
                    fontSize = 11.sp,
                    fontFamily = MainSystemFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                ) {
                    listOf(2, 3, 4, 5).forEachIndexed { i, count ->
                        val isSel = count == node.dotCount
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (isSel) (if (colors.isDark) Color(0xFFE8E8E8) else Color(0xFF0D0D0D))
                                    else colors.innerCard
                                )
                                .clickable {
                                    haptic.click()
                                    onUpdate(node.copy(dotCount = count))
                                }
                                .testTag("node_${index}_dot_count_$count"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count dots",
                                fontSize = 11.sp,
                                fontFamily = MainSystemFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = if (isSel) (if (colors.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)) else colors.textSecondary
                            )
                        }
                        if (i < 3) {
                            Spacer(modifier = Modifier.width(1.dp).height(34.dp).background(colors.border))
                        }
                    }
                }
            }

            // 4. Sliders with Reset Buttons:
            ResetSliderRow(
                label = if (node.shape == NodeShape.ARC) "Arc size" else "Line length",
                value = node.sizeDp,
                defaultValue = NodeConfig.MIN_SIZE_DP,
                valueRange = NodeConfig.MIN_SIZE_DP..NodeConfig.MAX_SIZE_DP,
                unit = "dp",
                isSigned = false,
                testTag = "node_${index}_size",
                accentColor = Color(node.staticColor),
                onReset = { onUpdate(node.copy(sizeDp = NodeConfig.MIN_SIZE_DP)) },
                onValueChange = { onUpdate(node.copy(sizeDp = it)) }
            )

            ResetSliderRow(
                label = "Position X",
                value = node.positionXDp,
                defaultValue = NodeConfig.DEFAULT_POSITION_X,
                valueRange = NodeConfig.MIN_OFFSET_DP..NodeConfig.MAX_OFFSET_DP,
                unit = "dp",
                isSigned = true,
                testTag = "node_${index}_position_x",
                accentColor = Color(node.staticColor),
                onReset = { onUpdate(node.copy(positionXDp = NodeConfig.DEFAULT_POSITION_X)) },
                onValueChange = { onUpdate(node.copy(positionXDp = it)) }
            )

            ResetSliderRow(
                label = "Position Y",
                value = node.positionYDp,
                defaultValue = NodeConfig.DEFAULT_POSITION_Y,
                valueRange = NodeConfig.MIN_OFFSET_DP..NodeConfig.MAX_OFFSET_DP,
                unit = "dp",
                isSigned = true,
                testTag = "node_${index}_position_y",
                accentColor = Color(node.staticColor),
                onReset = { onUpdate(node.copy(positionYDp = NodeConfig.DEFAULT_POSITION_Y)) },
                onValueChange = { onUpdate(node.copy(positionYDp = it)) }
            )

            // 5. Static / Dynamic Color Toggle (Full width segmented tabs with weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Color mode",
                    fontSize = 11.sp,
                    fontFamily = MainSystemFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary
                )

                // Full-width segmented tabs for Static / Dynamic
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                ) {
                    listOf(false to "Static", true to "Dynamic").forEachIndexed { i, (isDyn, label) ->
                        val isSel = isDyn == node.isDynamicColor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (isSel) (if (colors.isDark) Color(0xFFE8E8E8) else Color(0xFF0D0D0D))
                                    else colors.innerCard
                                )
                                .clickable {
                                    haptic.click()
                                    onUpdate(node.copy(isDynamicColor = isDyn))
                                }
                                .testTag("node_${index}_colormode_${label.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontFamily = MainSystemFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = if (isSel) (if (colors.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)) else colors.textSecondary
                            )
                        }
                        if (i == 0) {
                            Spacer(modifier = Modifier.width(1.dp).height(34.dp).background(colors.border))
                        }
                    }
                }

                if (!node.isDynamicColor) {
                    ColorWheelPicker(
                        selectedColor = node.staticColor,
                        label = "Static color",
                        testTagPrefix = "node_${index}_static",
                        onColorSelected = { onUpdate(node.copy(staticColor = it)) }
                    )
                } else {
                    ColorWheelPicker(
                        selectedColor = node.baseColor,
                        label = "Base color",
                        testTagPrefix = "node_${index}_base",
                        onColorSelected = { onUpdate(node.copy(baseColor = it)) }
                    )

                    ColorWheelPicker(
                        selectedColor = node.alertColor,
                        label = "Alert color",
                        testTagPrefix = "node_${index}_alert",
                        onColorSelected = { onUpdate(node.copy(alertColor = it)) }
                    )

                    ResetSliderRow(
                        label = "Alert threshold",
                        value = node.alertThreshold * 100f,
                        defaultValue = NodeConfig.DEFAULT_ALERT_THRESHOLD * 100f,
                        valueRange = 10f..100f,
                        unit = "%",
                        isSigned = false,
                        testTag = "node_${index}_alert_threshold",
                        accentColor = Color(node.alertColor),
                        onReset = { onUpdate(node.copy(alertThreshold = NodeConfig.DEFAULT_ALERT_THRESHOLD)) },
                        onValueChange = { onUpdate(node.copy(alertThreshold = it / 100f)) }
                    )
                }
            }
        }
    }
}

@Composable
fun ColorWheelPicker(
    selectedColor: Int,
    label: String,
    testTagPrefix: String,
    onColorSelected: (Int) -> Unit
) {
    val colors = XNodeTheme.colors
    val haptic = rememberHapticFeedbackHelper()
    var hexText by remember(selectedColor) {
        mutableStateOf(String.format("%06X", (0xFFFFFF and selectedColor)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.innerCard)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = MainSystemFontFamily,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary
            )

            // Hex input field & preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor))
                )
                Text(
                    text = "#",
                    fontSize = 11.sp,
                    fontFamily = MainSystemFontFamily,
                    color = colors.textSecondary
                )
                BasicTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
                        hexText = clean
                        if (clean.length == 6) {
                            try {
                                val parsed = (0xFF000000 or clean.toLong(16)).toInt()
                                onColorSelected(parsed)
                            } catch (_: Exception) {}
                        }
                    },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontFamily = MainSystemFontFamily
                    ),
                    modifier = Modifier
                        .width(60.dp)
                        .background(colors.surface, RoundedCornerShape(4.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("${testTagPrefix}_hex_input")
                )
            }
        }

        // Palette swatches
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotchPreferences.COLOR_PALETTE.forEachIndexed { i, colorInt ->
                val isSelected = colorInt == selectedColor
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            haptic.click()
                            onColorSelected(colorInt)
                        }
                        .testTag("${testTagPrefix}_swatch_$i"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.dp, colors.textPrimary, CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                    )
                }
            }
        }
    }
}

@Composable
fun ResetSliderRow(
    label: String,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String = "dp",
    isSigned: Boolean = true,
    testTag: String,
    accentColor: Color,
    customFormatter: ((Float) -> String)? = null,
    onReset: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    val colors = XNodeTheme.colors
    val haptic = rememberHapticFeedbackHelper()
    var lastTickVal by remember { mutableFloatStateOf(value) }

    val formattedValue = customFormatter?.invoke(value) ?: if (isSigned) {
        "${String.format("%+.0f", value)} $unit"
    } else {
        "${String.format("%.0f", value)} $unit"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.innerCard)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = MainSystemFontFamily,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedValue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = MainSystemFontFamily,
                    color = colors.textPrimary
                )

                // Circular reset icon button at the flex-end
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .clickable {
                            haptic.click()
                            onReset()
                        }
                        .testTag("${testTag}_reset"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset $label to default",
                        tint = if (abs(value - defaultValue) < 0.1f) {
                            colors.textSecondary.copy(alpha = 0.3f)
                        } else {
                            colors.textPrimary
                        },
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Slider(
            value = value,
            onValueChange = { newVal ->
                if (abs(newVal - lastTickVal) >= 1.0f) {
                    haptic.tick()
                    lastTickVal = newVal
                }
                onValueChange(newVal)
            },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = accentColor,
                inactiveTrackColor = colors.border
            )
        )
    }
}
