#pragma once
#include <Arduino.h>

extern uint8_t outLevel[10];

void setupDisplay();
void drawOutputs(bool force = false);
void setOutLevel(int index, uint8_t level);