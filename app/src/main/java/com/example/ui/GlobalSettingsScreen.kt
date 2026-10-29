package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NotchConfig
import com.example.ui.theme.GeistMonoFontFamily
import com.example.ui.theme.MainSystemFontFamily
import com.example.ui.theme.NDotFontFamily
import com.example.ui.theme.XNodeTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    config: NotchConfig,
    extraDarkEnabled: Boolean,
    onToggleExtraDark: (Boolean) -> Unit,
    onUpdateConfig: (NotchConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    val colors = XNodeTheme.colors
    val systemDark = isSystemInDarkTheme()
    val haptic = rememberHapticFeedbackHelper()

    Scaffold(
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SETTINGS".uppercase(),
                        fontSize = 24.sp,
                        fontFamily = NDotFontFamily,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.click()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to main",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Behavior & Display Toggles Card (Nothing OS style: surface bg, 16dp radius, no border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Display & Behavior",
                    fontSize = 13.sp,
                    fontFamily = MainSystemFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                // Pocket Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pocket mode",
                            fontSize = 14.sp,
                            fontFamily = MainSystemFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Hides overlay when proximity sensor is covered",
                            fontSize = 11.sp,
                            fontFamily = MainSystemFontFamily,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = config.pocketModeEnabled,
                        onCheckedChange = {
                            haptic.click()
                            onUpdateConfig(config.copy(pocketModeEnabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF000000),
                            checkedTrackColor = Color(0xFFE8E8E8),
                            uncheckedThumbColor = Color(0xFF8A8A8A),
                            uncheckedTrackColor = Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.testTag("toggle_pocket_mode")
                    )
                }

                // Ghost Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ghost mode",
                            fontSize = 14.sp,
                            fontFamily = MainSystemFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Dims unfilled background dots for stealth look",
                            fontSize = 11.sp,
                            fontFamily = MainSystemFontFamily,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = config.ghostMode,
                        onCheckedChange = {
                            haptic.click()
                            onUpdateConfig(config.copy(ghostMode = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF000000),
                            checkedTrackColor = Color(0xFFE8E8E8),
                            uncheckedThumbColor = Color(0xFF8A8A8A),
                            uncheckedTrackColor = Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.testTag("toggle_ghost_mode")
                    )
                }

                // Dark Theme Toggle (When in system dark mode, toggles extra dark mode)
                if (systemDark) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Extra dark mode",
                                fontSize = 14.sp,
                                fontFamily = MainSystemFontFamily,
                                fontWeight = FontWeight.Normal,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Pure pitch black background (#000000)",
                                fontSize = 11.sp,
                                fontFamily = MainSystemFontFamily,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = extraDarkEnabled,
                            onCheckedChange = {
                                haptic.click()
                                onToggleExtraDark(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF000000),
                                checkedTrackColor = Color(0xFFE8E8E8),
                                uncheckedThumbColor = Color(0xFF8A8A8A),
                                uncheckedTrackColor = Color(0xFF2A2A2A)
                            ),
                            modifier = Modifier.testTag("toggle_extra_dark")
                        )
                    }
                }
            }

            // Section 2: Telemetry & Network Scaling (Nothing OS surface card)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Telemetry & Timing",
                    fontSize = 13.sp,
                    fontFamily = MainSystemFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                // Network Speed Slider with direct KB/s text input box
                NetworkSpeedSettingRow(
                    valueMB = config.maxNetSpeedMB,
                    onValueChange = { onUpdateConfig(config.copy(maxNetSpeedMB = it)) },
                    haptic = haptic
                )

                // Update Interval Slider (1s to 10s, default 2s)
                UpdateIntervalSettingRow(
                    valueSeconds = config.updateIntervalSeconds,
                    onValueChange = { onUpdateConfig(config.copy(updateIntervalSeconds = it)) },
                    haptic = haptic
                )
            }
        }
    }
}

@Composable
private fun NetworkSpeedSettingRow(
    valueMB: Float,
    onValueChange: (Float) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val colors = XNodeTheme.colors
    val currentKB = (valueMB * 1000f).roundToInt()
    var textInput by remember(currentKB) { mutableStateOf(currentKB.toString()) }
    var lastTickMB by remember { mutableFloatStateOf(valueMB) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.innerCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Max network speed",
                fontSize = 12.sp,
                fontFamily = MainSystemFontFamily,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            // Reset to 5.0 MB/s (5000 KB/s)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .clickable {
                        haptic.click()
                        onValueChange(5.0f)
                    }
                    .testTag("global_max_net_speed_reset"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset network speed",
                    tint = if (abs(valueMB - 5.0f) < 0.1f) colors.textSecondary.copy(alpha = 0.3f) else colors.textPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Text(
            text = "Used to scale NET IN / OUT dots accurately",
            fontSize = 10.sp,
            fontFamily = MainSystemFontFamily,
            color = colors.textSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Slider (0.1 to 50 MB/s)
            Slider(
                value = valueMB,
                onValueChange = { newVal ->
                    if (abs(newVal - lastTickMB) >= 1.0f) {
                        haptic.tick()
                        lastTickMB = newVal
                    }
                    onValueChange(newVal)
                },
                valueRange = 0.1f..50.0f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("global_max_net_speed"),
                colors = SliderDefaults.colors(
                    thumbColor = colors.textPrimary,
                    activeTrackColor = Color(0xFF0A84FF),
                    inactiveTrackColor = colors.border
                )
            )

            // Direct outlined text input box in KB/s
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                BasicTextField(
                    value = textInput,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }.take(6)
                        textInput = clean
                        val kbVal = clean.toFloatOrNull()
                        if (kbVal != null && kbVal >= 100f) {
                            val newMB = (kbVal / 1000f).coerceIn(0.1f, 50.0f)
                            onValueChange(newMB)
                        }
                    },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontFamily = GeistMonoFontFamily,
                        textAlign = TextAlign.End
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .width(46.dp)
                        .testTag("global_max_net_speed_input")
                )
                Text(
                    text = "KB/s",
                    fontSize = 10.sp,
                    fontFamily = GeistMonoFontFamily,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun UpdateIntervalSettingRow(
    valueSeconds: Int,
    onValueChange: (Int) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val colors = XNodeTheme.colors
    var lastTickSec by remember { mutableIntStateOf(valueSeconds) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.innerCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Update interval",
                fontSize = 12.sp,
                fontFamily = MainSystemFontFamily,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${valueSeconds}s",
                    fontSize = 12.sp,
                    fontFamily = GeistMonoFontFamily,
                    color = colors.textPrimary
                )

                // Reset button to 2s
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                    .clickable {
                        haptic.click()
                        onValueChange(2)
                    }
                    .testTag("global_update_interval_reset"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset update interval",
                        tint = if (valueSeconds == 2) colors.textSecondary.copy(alpha = 0.3f) else colors.textPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Text(
            text = "Background telemetry polling frequency",
            fontSize = 10.sp,
            fontFamily = MainSystemFontFamily,
            color = colors.textSecondary
        )

        Slider(
            value = valueSeconds.toFloat(),
            onValueChange = { raw ->
                val rounded = raw.roundToInt().coerceIn(1, 10)
                if (rounded != lastTickSec) {
                    haptic.tick()
                    lastTickSec = rounded
                }
                onValueChange(rounded)
            },
            valueRange = 1.0f..10.0f,
            steps = 8,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("global_update_interval"),
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = Color(0xFF34C759),
                inactiveTrackColor = colors.border
            )
        )
    }
}
