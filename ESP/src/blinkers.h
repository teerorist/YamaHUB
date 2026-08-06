#pragma once
#include <Arduino.h>
#include "Output.h"
#include "Button.h"

enum BlinkerMode {
    MODE_OFF, MODE_LEFT, MODE_RIGHT, MODE_HAZARD, MODE_FADING_OUT
};

extern BlinkerMode currentMode;
extern int blinksRemaining;
extern bool connectionBlink;
extern unsigned long connectionBlinkStart;

void setupBlinkers();
void refreshBlinkerPins();
int blinkerLeftOutIndex();
int blinkerRightOutIndex();
void forceMode(BlinkerMode mode);
void handleBlinkerButtons(Button* buttons, bool& stateChanged);
void updateBlinkers(bool& stateChanged);
void setLeft(int v);
void setRight(int v);
void setCurrentSpeed(float kmh);
void suspendBlinkers();
void resumeBlinkers();
void requestBlinkersGracefulOff();
bool blinkersAreOff();