#pragma once

// ESP32-S3-LCD-1.47B (TESTY) – bez GPIO 35/36/37 (Octal PSRAM!)
// LCD: 39,40,41,42,45,46

#define IN_1      7
#define IN_2      15
#define IN_3      17
#define IN_4      18
#define IN_5      4
#define IN_6      8
#define IN_7      16
#define IN_8      6
#define IN_9      5
#define IN_10     0

#define OUT_1     3
#define OUT_2     10
#define OUT_3     11
#define OUT_4     9      // NIE 36
#define OUT_5     12
#define OUT_6     47
#define OUT_7     21     // NIE 35
#define OUT_8     48     // NIE 37
#define OUT_9     13
#define OUT_10    14

#define U3_EN     19
#define I2C_SDA   1
#define I2C_SCL   2
#define V_BAT_PIN 20     // ostrożnie – testowo
#define GPS_RX    2      // tymczasowo (I2C i tak nie używasz)
#define GPS_TX    1
#define LED_STATUS 38