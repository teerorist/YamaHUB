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
/** Indeks OUT 0..9 albo -1 gdy brak w inputCfg. */
int blinkerLeftOutIndex();
int blinkerRightOutIndex();
bool isBlinkerOut(int outIndex0);

void forceMode(BlinkerMode mode);
/** Jak fizyczny short/long – tabela stanów (N z cfg.blinkCount / NS). */
void applyLeftShort();
void applyLeftLong();
void applyRightShort();
void applyRightLong();
void applyLeftHoldLong();
void applyRightHoldLong();
void handleBlinkerButtons(Button* buttons, bool& stateChanged);
void updateBlinkers(bool& stateChanged);
void setLeft(int v);
void setRight(int v);
void setCurrentSpeed(float kmh);
void suspendBlinkers();
void resumeBlinkers();
void requestBlinkersGracefulOff();
bool blinkersAreOff();
