<div align="center">

<img src="https://github.com/Kourva/xnode/blob/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="120px" />

# X N O D E
### Nothing inspired hardware Resource Telemetry HUD for Android

<p align="center">
  <b>xnode</b> transforms your device's camera notch and punch-hole cutout into a minimalist, real-time hardware telemetry HUD. Designed with the distinctive high-contrast dot-matrix aesthetic of Nothing OS.
</p>

[![Android API](https://img.shields.io/badge/Android-API%2031%2B%20(Android%2012%2B)-black?style=for-the-badge&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-black?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-black?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-white?style=for-the-badge)](LICENSE)
[![Download APK](https://img.shields.io/badge/Release-Download%20APK%20(v1.0.0)-red?style=for-the-badge&logo=github)](https://github.com/Kourva/xnode/releases/latest)

</div>

## Inspiration & Origin

**Why build xnode?**

On Linux, I'm used to having CPU and RAM monitors in my taskbar. It's second nature to glance at them and know when to close a heavy app. Android doesn't really have that kind of ambient telemetry without relying on clunky widgets.

Since I use a Nothing Phone (2a), I realized the camera punch-hole is the perfect place for a minimal HUD. It takes up zero screen space, and the dot-matrix style fits the Nothing OS vibe perfectly.

**The "AI-Generated" Part:**
I don't have a background in Android development. I built this using Google AI to bring the concept to life, write the Canvas math, and set up the CI/CD. Since it's AI-assisted, I'm open-sourcing it so real developers (or other AI builders) can fork it, optimize it, or add features like WebSocket remote control and Glyph integration.

## Overview

**xnode** renders lightweight, non-intrusive ambient dot clusters and arc meters directly around your front camera cutout. Track your processor load, physical memory pressure, network throughput, battery level, battery temperature, or disk storage without ever pulling down the notification shade or leaving your active app.

Built entirely with **Kotlin**, **Jetpack Compose**, and Android's native **WindowManager Overlay Service**, xnode is ultra-lightweight, battery-efficient, and optimized for high-refresh-rate displays.

## Features

- **Dynamic Hardware Nodes**:
  - `CPU`: Real-time active CPU utilization parsed via `/proc/stat` with fallback heuristics.
  - `MEM_FREE` & `MEM_USED`: Active RAM pressure via `ActivityManager.MemoryInfo`.
  - `NET_IN` & `NET_OUT`: Real-time network throughput delta via `TrafficStats`.
  - `BAT`: Battery percentage monitoring with charging detection.
  - `TEMP`: Battery thermal sensor tracking with overheat threshold coloring.
  - `DISK`: Internal flash storage capacity monitoring via `StatFs`.
- **Configurable Geometry**:
  - **Shapes**: Adaptive **Arc** (circular contour wrapping punch-holes) or **Straight** (vertical edge line).
  - **Node Direction**: Hug the left or right curve of the cutout.
  - **Configurable Dot Counts**: Choose between 2, 3, 4, or 5 ultra-crisp micro-dots per node.
  - **Sub-Pixel Calibration**: Independent X offset, Y offset, and Arc Size / radius sliders with 1-tap reset buttons.
- **Color Modes**:
  - **Static**: Custom monochrome or accent hex colors.
  - **Dynamic**: Real-time reactive gradients (Normal: Pure White `#FFFFFF`, Warning: Amber `#FFA500`, Critical: Crimson `#FF3B30`).
- **Hardware Sensor & Context Automations**:
  - 📱 **Pocket Mode (Proximity Sensor)**: Automatically hides the overlay when your phone is in your pocket or face-down.
  - 🎮 **Game / Landscape Mode**: Automatically detects screen orientation changes and suppresses the overlay to prevent obstructing full-screen games and media.
  - 🔋 **Low Battery Auto-Kill**: Safely shuts down telemetry services when battery drops below 15% to conserve device power.
  - 🌈 **Shake-to-Rainbow (Easter Egg)**: Give your phone a quick shake to trigger a dynamic RGB wave across all telemetry dots!
- **Nothing OS Inspired UI**:
  - Pure `#000000` AMOLED black styling.
  - Dot-matrix typography (`NDot 55` / `NDot 57`).
  - Tactile haptic feedback across all toggles, sliders, and buttons.
  - Edge-to-edge transparent navigation with fluid Android 14+ transitions.

## Interface Preview

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="https://github.com/user-attachments/assets/079e7f9f-8794-4e82-ae35-04f2cf91f22f" width="250" />
      </td>
      <td align="center">
        <img src="https://github.com/user-attachments/assets/5db92731-eee5-48f3-810d-b511408a8bfc" width="250" />
      </td>
       <td align="center">
        <img src="https://github.com/user-attachments/assets/66ebfa89-a3a4-49ba-8ee0-4f38745f3337" width="250" />
      </td>
       <td align="center">
        <img src="https://github.com/user-attachments/assets/fe7c60bd-78e7-4f74-a006-30545eb4cd92" width="250" />
      </td>
    </tr>
  </table>
</div>

## Installation

### Requirements
- **Android 12.0+ (API Level 31+)**
- Devices with a centered or corner hole-punch camera (default calibrations tailored for **Nothing Phone (2a)**, customizable for any punch-hole display).

### Sideloading
1. Download the latest `xnode-debug.apk` (universal) or your architecture-specific build (e.g. `xnode-arm64-v8a-debug.apk` for modern devices like Nothing Phone) directly from the [Releases](https://github.com/Kourva/xnode/releases) tab. (No GitHub account required).
2. Open the downloaded APK on your Android device and tap **Install**.
3. Launch **XNODE** from your app drawer.
4. When prompted, grant the **Display over other apps** (Overlay) permission so xnode can draw around your camera cutout.

## Usage Guide

1. **Start the HUD**:
   - Tap the large bottom pill button: **START OVERLAY**.
   - The persistent low-priority foreground service will start, and the default nodes will appear around your camera notch.
2. **Add & Customize Nodes**:
   - Tap the circular **+** button next to the Start/Stop button at the bottom to add a new independent telemetry node.
   - Select the data source (`CPU`, `MEM`, `BAT`, `NET`, `TEMP`, or `DISK`).
   - Choose your preferred geometry: **Arc** for curved punch-holes or **Straight** for vertical bars.
   - Adjust the **Arc Size**, **Position X**, and **Position Y** sliders until the dots align symmetrically around your camera hole.
3. **Global Settings**:
   - Tap the **Gear (⚙)** icon in the top right header to access Global Settings.
   - Configure **Dark Mode**, **Ghost Mode** (50% transparent dots), **Pocket Mode**, **Update Interval (1s - 10s)**, and **Max Network Scale (KB/s)**.

## Architecture & Development

xnode follows modern Android best practices:
- **UI Layer**: 100% Jetpack Compose with Material 3 theming and custom Canvas drawing.
- **Service Layer**: Dedicated foreground `OverlayService` utilizing `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
- **Navigation**: Type-safe `androidx.navigation:navigation-compose` with standard predictive back integration.
- **Telemetry Engine**: Background coroutines polling system hardware interfaces (`/proc/stat`, `ActivityManager`, `TrafficStats`, `BatteryManager`, `StatFs`, `SensorManager`).

### Building from Source

```bash
# Clone the repository
git clone https://github.com/Kourva/xnode.git
cd xnode

# Build debug APK
gradle assembleDebug

# Run unit and Robolectric tests
gradle :app:testDebugUnitTest

# Build release bundle
gradle assembleRelease
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.


## Attribution

**AI-Generated, inspired by Nothing.**
