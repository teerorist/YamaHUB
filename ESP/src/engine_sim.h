#pragma once
#include <Arduino.h>

extern int currentRpm;
extern int currentGear;

void updateEngineSim(float speedKmh);
