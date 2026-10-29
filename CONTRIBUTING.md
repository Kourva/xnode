# Contributing to XNODE

Thank you for your interest in contributing to **XNODE**! We welcome bug reports, feature requests, and code contributions that align with our design ethos and performance standards.

---

## Code of Conduct

Please be respectful, constructive, and considerate in all issues, pull requests, and discussions.

---

## How to Contribute

### 1. Reporting Bugs

Before submitting a bug report:
- Check existing [Issues](https://github.com/Kourva/xnode/issues) to ensure the issue has not already been reported.
- Verify that the issue reproduces on the latest release or `main` branch.

When opening a bug report, please include:
- **Device Model & Android Version** (e.g., Nothing Phone (2a), Android 14 / Nothing OS 2.6).
- **Steps to Reproduce**: Clear, numbered step-by-step instructions.
- **Expected vs. Actual Behavior**: Describe what you expected to happen versus what actually occurred.
- **Relevant Logs / Screenshots**: `adb logcat` output or screenshots illustrating the issue (especially for camera cutout alignment or overlay issues).

### 2. Requesting Features

We welcome ideas that enhance hardware telemetry, improve power efficiency, or refine the Nothing-inspired aesthetic.
- Open a feature request issue with a clear summary of the proposal.
- Explain the motivation and use case.
- Provide mockups or visual references if proposing UI changes.

### 3. Submitting Pull Requests

1. **Fork and Branch**:
   - Fork the repository and create a feature branch off `main`:
     ```bash
     git checkout -b feature/your-feature-name
     ```
   - Use descriptive branch names (`fix/proximity-sensor-leak`, `feature/gpu-frequency-node`, etc.).

2. **Code Standards & Architecture**:
   - Write clean, idiomatic **Kotlin** and modern **Jetpack Compose** UI.
   - Strictly follow the **Nothing OS design philosophy**: pure black backgrounds (`#000000`), stark monochrome contrasts (`#FFFFFF`, `#8A8A8A`), dot-matrix accents, and tactile haptic feedback.
   - Keep background telemetry lightweight with zero unnecessary allocations in render loops.
   - Respect user privacy: overlay permissions must only be used for the HUD.

3. **Verify Builds and Tests**:
   - Ensure the project builds cleanly without warnings or errors:
     ```bash
     gradle assembleDebug
     ```
   - Verify all unit and Robolectric tests pass:
     ```bash
     gradle :app:testDebugUnitTest
     ```
   - Ensure your changes do not introduce lint warnings or memory leaks in the background overlay service.

4. **Open a Pull Request**:
   - Provide a clear, detailed PR description explaining what was changed and why.
   - Link any related issues (e.g., `Closes #12`).
   - Include before/after screenshots or screen recordings for any visual changes.

---

## Attribution

**AI-Generated, inspired by Nothing. Built by Kourva.**
