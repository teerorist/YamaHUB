#pragma once
#include <Arduino.h>

/** Tryby IN_01..IN_10 – GR I INPUTS */
enum InputMode : uint8_t {
    IN_TOGGLE   = 0,  // BUTTON / LIGHTS (toggle)
    IN_MOMENT   = 1,  // legacy value, migrated to IN_SENSOR
    IN_LEFT     = 2,  // → blinkers
    IN_RIGHT    = 3,  // → blinkers
    IN_SENSOR   = 4,
    IN_DISABLED = 5,
    IN_STARTER  = 6   // → starter
};

struct InputCfgItem {
    uint8_t mode;      // InputMode
    uint8_t outIndex;  // 0..9 → OUT_1..OUT_10
    bool outputEnabled; // SENSOR/BUTTON may control an output
    uint8_t outputIndex; // 0..9 → controlled OUT
    char name[16];
};

static const int INPUT_COUNT = 10;
extern InputCfgItem inputCfg[INPUT_COUNT];

void loadInputModes();
void saveInputModes();
bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex,
                 bool outputEnabled, uint8_t outputIndex, const char* name);

int findStarterInIndex();   // 0..9 lub -1
int starterOutIndex();      // 0..9 lub -1
int findNeutralInIndex();    // 0..9 lub -1
void setNeutralSimulation(bool on);
bool isNeutralSimulation();
void setSensorSimulation(int inIndex, bool on);
bool isInputActive(int inIndex, bool physicalPressed);
bool getEffectiveInputState(int inIndex);

void setBleInputPressed(int inIndex, bool pressed);
bool isBleInputPressed(int inIndex);
