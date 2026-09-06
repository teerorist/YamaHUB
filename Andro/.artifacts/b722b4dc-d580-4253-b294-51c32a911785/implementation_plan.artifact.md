# Implementation Plan - Android Logic Cleanup

Cleanup of redundant logic in the Android application that has been migrated to the ESP32 firmware. The Android app should primarily act as a terminal/dashboard and sensor provider (GPS speed), while the ESP32 handles control logic like automatic lights and blinker management.

## Proposed Changes

### Android App

#### [MODIFY] [DashboardScreen.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)
- Remove `lastDisplaySpeed` state variable.
- Remove the `LaunchedEffect` that handles `autoLights` and `acSpeedThreshold` logic. This logic is now handled by the ESP32 firmware (see `beams.cpp` and `ble_protocol.cpp`).
- Verify that no other "control logic" is hidden in the `DashboardScreen`.

#### [MODIFY] [ControlButtons.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/ControlButtons.kt)
- Review and potentially simplify if there's any logic that incorrectly bypasses ESP behavior, though `ControlScreen` is intended for direct output manipulation.

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator.
- Verify that the Dashboard still displays speed and RPM correctly.
- Verify that automatic lights (if configured) are still activated by the ESP32 (requires testing with the firmware or simulating speed via the app's test panel).
- Ensure no regressions in manual output control from the Control screen.
