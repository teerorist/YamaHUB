# YamaHUB Project - Technical Conversation Part 3

## Overall Goal
Moving business logic to ESP firmware (YamaHUB V4) and adapting the Android app as a flexible control terminal.

## Key Technical Milestones Covered:

### 1. ControlScreen Updates (v4.4)
- **Labels & Colors**: Updated `ControlScreen` to correctly describe and color all 10 outputs (OUT 01-10) based on new `functionId` mapping (Blinkers, Lights, Brakes, Neutral, Starter, Oil, Clutch).
- **Function Icons**: Mapped colors (Orange for Blinkers, Blue/White for Lights, Red for Brakes/Oil, Green for Neutral/Starter).

### 2. Input Slot Editor & Models
- **Dynamic Naming**: Implementation of dynamic naming for Lights (1x vs 2x) and Brakes (Front/Rear).
- **Subtitles**: Replaced static subtitles with assigned output ports (e.g., "OUT 04") in `InputSettingsTab`.

### 3. Blinker Logic Refinement (v4.5)
- **Toggle NS**: Moved blinker toggle logic (Non-Stop mode) directly to ESP firmware.
- **Independence**: Ensured one side does not turn off the other (enabling Hazard mode when both are active).
- **Commands**: Implemented `BLINK:L:TOGGLE` and `BLINK:R:TOGGLE` BLE commands.

### 4. Headlight Logic (One-Button Mode)
- **States**:
    - First press: LOW ON.
    - Short (<400ms): HI PASS (Flash).
    - Long (400ms-2s): HI LATCH (Toggle ON/OFF).
    - >5s: ALL OFF (Kill).
- **Instant Response**: Headlight HI beam switching made instant (0/1), while system-off events retain a smooth fade.

### 5. Engine & Gear Simulation (v4.7)
- **RPM Simulation**: Implemented on ESP based on GPS speed.
- **6 Gears**: Simulated gear changes at 5050 RPM with specific ratios for Focus MK2.
- **BLE Transmission**: Hub sends `RPM:value` and `FUEL:value` to Android.

### 6. Startup Animation (v4.8 - v4.9)
- **Sequence**: Sweep needle to 12k RPM in 1.5s, sequentially lighting up NEUTRAL (1k), LOW (3k), OIL (6k), HI (9k), FUEL (11k), and a HAZARD flash at peak.
- **State-Based**: Rewritten to manipulate actual system states (virtual inputs/outputs) instead of just sending animation frames.

### 7. CAN Bus Integration (Ford Focus MK2)
- **Hardware**: Integration of HW-184 (MCP2515) with Waveshare ESP32-S3-LCD-1.47B.
- **Protocol**: HS-CAN (500kbps) with 8MHz crystal.
- **Pinout Troubleshooting (Critical)**:
    - **Attempt 1**: GPIO 10-13 (Prawy header). Result: PSRAM conflict (`0x00ffffff` error).
    - **Attempt 2**: GPIO 2-5 (Lewy header). Result: SPI Init Err / PSRAM conflict.
    - **Attempt 3 (Current)**: Final resort using **TX (GP43)** for MOSI, **RX (GP44)** for MISO, **GP0** for SCK, and **GP2** for CS.
- **Diagnostic Tools**: Added a Live Debug Console to the Android Dashboard and a `CAN_TEST` (Loopback) command.

### 8. System Startup Fixes
- **Headlights**: Removed automatic LOW ON at boot.
- **Dummy IO**: Set unused IN/OUT pins to 255 to prevent ghost triggering of sensors due to GPIO 0 (BOOT button) state.

## Current Hardware State (Waveshare 1.47B):
- **CAN_MOSI**: 43 (TX)
- **CAN_MISO**: 44 (RX)
- **CAN_SCK**: 0
- **CAN_CS**: 2
- **CAN_INT**: -1 (Disabled)
- **VCC**: 3.3V
- **TJA 5V**: VBUS
