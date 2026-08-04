#pragma once
#include <Arduino.h>
#include "Output.h"

void setupBeams();
void requestBeamLevel(int outIndex, uint8_t target);  // 0 lub 255
void updateBeams(bool& stateChanged);
bool isBeamOutput(int outIndex);