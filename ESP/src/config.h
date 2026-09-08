#pragma once
#include <Arduino.h>

struct BlinkerConfig {
    uint8_t fadeSpeed;
    uint8_t blinkCount;
    uint8_t curve;
    uint8_t autoCancelSpeed;  // 5..30 km/h – wspólny próg
    uint8_t autoCancel;       // 0/1 NS→N kierunków
    uint8_t autoLights;       // 0/1 LOW BEAM przy SPEED
    uint8_t commonBrakePositionWire;  // 0/1 BRAKE i pozycja na wspólnym OUT
    uint8_t positionBrightness;       // 1..99 %
};

extern BlinkerConfig cfg;

void loadConfig();
void saveConfig();