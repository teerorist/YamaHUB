#pragma once
#include <Arduino.h>
#include "Button.h"
#include "Output.h"

void setupBeams();
void updateBeams(bool& stateChanged);
void requestBeamLevel(int outIndex, uint8_t target);
bool isBeamOutput(int outIndex);
int lowBeamOutIndex();
bool applyAutoLights(float kmh);
bool isLightsInput(int inIndex);
void handleLightsInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged);
