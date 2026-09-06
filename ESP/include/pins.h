#pragma once

// ESP32-S3-LCD-1.47B (WAVESHARE) - Final Safe Pinout
// Avoiding Octal PSRAM conflicts (GP8-GP13, GP33-GP37)

// CAN BUS (MCP2515) - Pins 43, 44, 0, 2
#define CAN_MOSI  43     // TX
#define CAN_MISO  44     // RX
#define CAN_SCK   0      // GP0
#define CAN_CS    2      // GP2
#define CAN_INT   -1

// Waveshare Internal/Fixed
#define LCD_BL_PIN 48
#define V_BAT_PIN  1

// Dummy GPIO (test board). ESP-IDF abortuje gpio_reset_pin / gpio_set_level poza 0..48.
static inline bool pinValid(int p) {
    return p >= 0 && p <= 48;
}

// Muted IO for testing - set to 254 (unused) to avoid narrowing error
#define IN_1      254
#define IN_2      254
#define IN_3      254
#define IN_4      254
#define IN_5      254
#define IN_6      254
#define IN_7      254
#define IN_8      254
#define IN_9      254
#define IN_10     254

#define OUT_1     254
#define OUT_2     254
#define OUT_3     254
#define OUT_4     254
#define OUT_5     254
#define OUT_6     254
#define OUT_7     254
#define OUT_8     254
#define OUT_9     254
#define OUT_10    254
