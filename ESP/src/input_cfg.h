#pragma once
#include <Arduino.h>
#include "Button.h"

static const int INPUT_COUNT = 10;
static const uint8_t OUT_NONE = 255;

enum InputCategory : uint8_t {
    CAT_BUTTON = 0,
    CAT_SENSOR = 1,
    CAT_DISABLED = 2
};

enum InputFunction : uint8_t {
    FN_NONE = 0,
    FN_LEFT = 1,
    FN_RIGHT = 2,
    FN_LIGHTS_1 = 3,
    FN_LIGHTS_2 = 4,
    FN_BRAKE_1 = 5,
    FN_BRAKE_2 = 6,
    FN_NEUTRAL = 7,
    FN_CLUTCH = 8,
    FN_STARTER = 9,
    FN_OIL = 10,
    FN_FUEL = 11,
    FN_USER = 12,
    FN_KILL_SWITCH = 13
};

/** Wynik zapisu konfiguracji (transakcja 10 slotów). */
enum InCfgApply : uint8_t {
    INCFG_IDLE = 0,
    INCFG_STAGED = 1,
    INCFG_COMMITTED = 2,
    INCFG_REJECTED = 3
};

/**
 * Jeden slot IN_01..IN_10.
 * outPrimary / outSecondary: 0..9 albo OUT_NONE.
 * Systemowe (LEFT/RIGHT/LIGHTS_1/BRAKE_1/NEUTRAL/STARTER) nie schodzą
 * z tablicy — tylko DnD między IN i zmiana OUT.
 */
struct InputCfgItem {
    uint8_t category;
    uint8_t functionId;
    uint8_t outPrimary;
    uint8_t outSecondary;
    bool outputEnabled;
    bool fixed;
    bool outLocked;
    char name[16];
};

extern InputCfgItem inputCfg[INPUT_COUNT];

void loadInputModes();
void saveInputModes();

bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex,
                 bool outputEnabled, uint8_t outputIndex, const char* name);
InCfgApply stageInputCfgV4(int inIndex, uint8_t category, uint8_t functionId,
                           uint8_t outPrimary, uint8_t outSecondary,
                           bool outputEnabled, uint8_t outputIndex, const char* name);
InCfgApply commitInputCfg();
InCfgApply pollInputCfgTxn();
const char* inputCfgRejectReason();

bool isSystemFunction(uint8_t functionId);
bool outAssigned(uint8_t outIndex);
int findFunctionInIndex(uint8_t functionId);  // 0..9 lub -1
int findStarterInIndex();
int starterOutIndex();                        // 0..9 lub -1
int starterKillOutIndex();                    // 0..9 lub -1
int findNeutralInIndex();
int findOilInIndex();
int findFuelInIndex();

void setNeutralSimulation(bool on);
bool isNeutralSimulation();
void setSensorSimulation(int inIndex, bool on);
bool isInputActive(int inIndex, bool physicalPressed);
bool getEffectiveInputState(int inIndex);
void updateAllEffectiveInputStates(Button* buttons);
void setBleInputPressed(int inIndex, bool pressed);
bool isBleInputPressed(int inIndex);

void setAnimInputOverride(int inIndex, bool on);
void clearAnimInputOverride(int inIndex);
void clearAllAnimInputOverrides();
