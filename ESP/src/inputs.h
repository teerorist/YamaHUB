#pragma once
#include <Arduino.h>
#include "Button.h"
#include "Output.h"

// tryby IN_1..IN_10
enum InputMode : uint8_t {
    IN_TOGGLE   = 0,
    IN_MOMENT   = 1,
    IN_LEFT     = 2,
    IN_RIGHT    = 3,
    IN_SENSOR   = 4,
    IN_DISABLED = 5,
    IN_STARTER  = 6
};

struct InputCfgItem {
    uint8_t mode;      // InputMode
    uint8_t outIndex;  // 0..9 → OUT_1..OUT_10
    char name[16];
};

static const int INPUT_COUNT = 10;

extern InputCfgItem inputCfg[INPUT_COUNT];

void loadInputModes();
void saveInputModes();
bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex, const char* name);
void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged);

/** Indeks IN z trybem STARTER (0..9) lub -1 gdy brak. */
int findStarterInIndex();
/** OUT index (0..9) przypisany do STARTER lub 9 jako fallback. */
int starterOutIndex();
