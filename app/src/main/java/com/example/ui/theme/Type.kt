package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Nothing NDot-55 font for title "xnode" and numeric percentage data displays
val NDotFontFamily = FontFamily(
    Font(R.font.ndot_55, FontWeight.Normal),
    Font(R.font.ndot_55, FontWeight.Bold)
)

// Main System Font: Android device's default system font (Geist / Roboto) for all UI text, buttons, and sliders
val MainSystemFontFamily = FontFamily.Default

// Geist Mono Font: Monospaced font for telemetry readouts and system stats
val GeistMonoFontFamily = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal)
)

val XNodeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NDotFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NDotFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MainSystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    )
)
