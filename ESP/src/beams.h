#pragma once
#include <Arduino.h>

void setupBeams();
void updateBeams(bool& stateChanged);
void requestBeamLevel(int outIndex, uint8_t target);
bool isBeamOutput(int outIndex);
