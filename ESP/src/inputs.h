#pragma once
#include <Arduino.h>
#include "Button.h"
#include "Output.h"

// tryby IN_1..IN_9
enum InputMode : uint8_t {
    IN_TOGGLE   = 0,
    IN_MOMENT   = 1,
    IN_LEFT     = 2,
    IN_RIGHT    = 3,
    IN_SENSOR   = 4,
    IN_DISABLED = 5
};

struct InputCfgItem {
    uint8_t mode;      // InputMode
    uint8_t outIndex;  // 0..8 → OUT_1..OUT_9
    char name[16];
};

extern InputCfgItem inputCfg[9];

void loadInputModes();
void saveInputModes();
bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex, const char* name);
void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged);