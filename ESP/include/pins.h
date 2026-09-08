#pragma once

// ESP32-S3-DevKitC-1 N16R8 (WROOM-1)
// Flash 16 MB QIO, PSRAM 8 MB OPI — GPIO 33–37 zajęte przez PSRAM.
// Unikamy: 19/20 USB D−/D+, 43/44 UART0 (USB-UART), 0 BOOT, 33–37 PSRAM.

static inline bool pinValid(int p) {
    return p >= 0 && p <= 48;
}

// CAN (MCP2515) — wyłączony w main; piny poza UART0/PSRAM/USB
#define CAN_MOSI  9
#define CAN_MISO  48
#define CAN_SCK   3
#define CAN_CS    9
#define CAN_INT   -1

// Opcjonalny ST7789 na goldpinach (nie ma LCD na DevKit)
#define LCD_SCLK  40
#define LCD_MOSI  45
#define LCD_DC    41
#define LCD_CS    42
#define LCD_RST   39
#define LCD_BL    46
#define LCD_BL_PIN LCD_BL
#define V_BAT_PIN -1

// IN 01–10: active LOW, INPUT_PULLUP  |  OUT 01–10: GPIO
#define IN_1      7
#define IN_2      15
#define IN_3      17
#define IN_4      18
#define IN_5      14
#define IN_6      8
#define IN_7      16
#define IN_8      6
#define IN_9      5
#define IN_10     4

#define OUT_1     1
#define OUT_2     2
#define OUT_3     42
#define OUT_4     41
#define OUT_5     40
#define OUT_6     39
#define OUT_7     38
#define OUT_8     48
#define OUT_9     47
#define OUT_10    21
