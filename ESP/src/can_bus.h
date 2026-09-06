#pragma once
#include <Arduino.h>

extern float canSpeedKmh;
extern int canRpm;
extern int canFuelPct;

void setupCAN();
void updateCAN();
void reportCANStatus();
void runCANLoopbackTest();
