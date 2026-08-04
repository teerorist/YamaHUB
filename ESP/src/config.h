#pragma once
#include <Arduino.h>

struct BlinkerConfig {
    uint8_t fadeSpeed;
    uint8_t blinkCount;
    uint8_t curve;
    uint8_t autoCancelSpeed;
    uint8_t beamFade;   // 0 = wyłączony, 1 = włączony
};

extern BlinkerConfig cfg;

void loadConfig();
void saveConfig();