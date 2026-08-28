#include "input_cfg.h"
#include <Preferences.h>
#include <string.h>

InputCfgItem inputCfg[INPUT_COUNT];
static bool neutralSimulation = false;
static bool sensorSimulation[INPUT_COUNT] = {false};
static bool bleInDown[INPUT_COUNT] = {false};
static bool effectiveInputState[INPUT_COUNT] = {false};

static void setDefaults() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        inputCfg[i].mode = IN_TOGGLE;
        inputCfg[i].outIndex = (uint8_t)i;
        inputCfg[i].outputEnabled = false;
        inputCfg[i].outputIndex = (uint8_t)i;
        snprintf(inputCfg[i].name, sizeof(inputCfg[i].name), "IN_%d", i + 1);
    }
    // domyślne kierunki
    inputCfg[0].mode = IN_LEFT;
    strncpy(inputCfg[0].name, "Kierunek_L", 15);
    inputCfg[0].name[15] = '\0';

    inputCfg[4].mode = IN_RIGHT;
    strncpy(inputCfg[4].name, "Kierunek_P", 15);
    inputCfg[4].name[15] = '\0';

    // IN_10 = STARTER (domyślnie)
    inputCfg[9].mode = IN_STARTER;
    inputCfg[9].outIndex = 9;  // OUT_10
    strncpy(inputCfg[9].name, "STARTER", 15);
    inputCfg[9].name[15] = '\0';
}

void loadInputModes() {
    setDefaults();

    Preferences p;
    if (!p.begin("yh_in", false)) {
        Serial.println("INCFG: NVS open fail → defaults");
        return;
    }

    const bool hasV2 = p.isKey("v2");
    const bool hasOk = p.isKey("ok");

    if (!hasOk) {
        // pierwszy start – zapisz defaulty 10 slotów
        p.putBool("ok", true);
        p.putBool("v2", true);
        for (int i = 0; i < INPUT_COUNT; i++) {
            char k[8];
            snprintf(k, sizeof(k), "m%d", i);
            p.putUChar(k, inputCfg[i].mode);
            snprintf(k, sizeof(k), "o%d", i);
            p.putUChar(k, inputCfg[i].outIndex);
            snprintf(k, sizeof(k), "e%d", i);
            p.putBool(k, inputCfg[i].outputEnabled);
            snprintf(k, sizeof(k), "s%d", i);
            p.putUChar(k, inputCfg[i].outputIndex);
            snprintf(k, sizeof(k), "n%d", i);
            p.putString(k, inputCfg[i].name);
        }
        p.end();
        Serial.println("INCFG: defaults saved (10)");
        return;
    }

    // odczyt 9 lub 10 pozycji
    const int nLoad = hasV2 ? INPUT_COUNT : 9;
    for (int i = 0; i < nLoad; i++) {
        char k[8];
        snprintf(k, sizeof(k), "m%d", i);
        inputCfg[i].mode = p.getUChar(k, inputCfg[i].mode);
        if (inputCfg[i].mode > IN_STARTER) inputCfg[i].mode = IN_TOGGLE;

        snprintf(k, sizeof(k), "o%d", i);
        inputCfg[i].outIndex = p.getUChar(k, inputCfg[i].outIndex);
        if (inputCfg[i].outIndex > 9) inputCfg[i].outIndex = (uint8_t)i;

        snprintf(k, sizeof(k), "e%d", i);
        inputCfg[i].outputEnabled = p.getBool(k, false);
        snprintf(k, sizeof(k), "s%d", i);
        inputCfg[i].outputIndex = p.getUChar(k, inputCfg[i].outIndex);
        if (inputCfg[i].outputIndex > 9) inputCfg[i].outputIndex = inputCfg[i].outIndex;
        if (inputCfg[i].mode == IN_MOMENT) inputCfg[i].mode = IN_SENSOR;

        snprintf(k, sizeof(k), "n%d", i);
        String s = p.getString(k, inputCfg[i].name);
        strncpy(inputCfg[i].name, s.c_str(), 15);
        inputCfg[i].name[15] = '\0';
    }

    // migracja 9 → 10: dopisz STARTER na IN_10 jeśli go nie ma
    if (!hasV2) {
        bool hasStarter = false;
        for (int i = 0; i < 9; i++) {
            if (inputCfg[i].mode == IN_STARTER) {
                hasStarter = true;
                break;
            }
        }
        if (!hasStarter) {
            inputCfg[9].mode = IN_STARTER;
            inputCfg[9].outIndex = 9;
            inputCfg[9].outputIndex = 9;
            strncpy(inputCfg[9].name, "STARTER", 15);
            inputCfg[9].name[15] = '\0';
        }
        p.putBool("v2", true);
        // dopisz klucze dla slotu 9
        p.putUChar("m9", inputCfg[9].mode);
        p.putUChar("o9", inputCfg[9].outIndex);
        p.putBool("e9", inputCfg[9].outputEnabled);
        p.putUChar("s9", inputCfg[9].outputIndex);
        p.putString("n9", inputCfg[9].name);
        Serial.println("INCFG: migrated 9→10");
    }

    p.end();
    Serial.println("INCFG: loaded (10)");
}

void saveInputModes() {
    Preferences p;
    if (!p.begin("yh_in", false)) return;
    p.putBool("ok", true);
    p.putBool("v2", true);
    for (int i = 0; i < INPUT_COUNT; i++) {
        char k[8];
        snprintf(k, sizeof(k), "m%d", i);
        p.putUChar(k, inputCfg[i].mode);
        snprintf(k, sizeof(k), "o%d", i);
        p.putUChar(k, inputCfg[i].outIndex);
        snprintf(k, sizeof(k), "e%d", i);
        p.putBool(k, inputCfg[i].outputEnabled);
        snprintf(k, sizeof(k), "s%d", i);
        p.putUChar(k, inputCfg[i].outputIndex);
        snprintf(k, sizeof(k), "n%d", i);
        p.putString(k, inputCfg[i].name);
    }
    p.end();
}

bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex,
                 bool outputEnabled, uint8_t outputIndex, const char* name) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    if (outIndex > 9) return false;
    if (mode > IN_STARTER) return false;

    inputCfg[inIndex].mode = mode;
    if (inputCfg[inIndex].mode == IN_MOMENT) inputCfg[inIndex].mode = IN_SENSOR;
    inputCfg[inIndex].outIndex = outIndex;
    inputCfg[inIndex].outputEnabled = outputEnabled &&
                                      (inputCfg[inIndex].mode == IN_SENSOR ||
                                       inputCfg[inIndex].mode == IN_TOGGLE);
    inputCfg[inIndex].outputIndex = (outputIndex <= 9) ? outputIndex : outIndex;
    if (name && name[0]) {
        strncpy(inputCfg[inIndex].name, name, 15);
        inputCfg[inIndex].name[15] = '\0';
    }
    saveInputModes();
    return true;
}

int findStarterInIndex() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].mode == IN_STARTER) return i;
    }
    return -1;
}

int starterOutIndex() {
    int si = findStarterInIndex();
    if (si < 0) return 9;
    uint8_t oi = inputCfg[si].outIndex;
    return (oi <= 9) ? (int)oi : 9;
}

int findNeutralInIndex() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].mode != IN_SENSOR && inputCfg[i].mode != IN_MOMENT) continue;
        String name = inputCfg[i].name;
        name.toLowerCase();
        if (name.indexOf("neutral") >= 0 || name.indexOf("luz") >= 0 ||
            name.indexOf("clutch") >= 0)
            return i;
    }
    return -1;
}

void setNeutralSimulation(bool on) {
    neutralSimulation = on;
}

bool isNeutralSimulation() {
    return neutralSimulation;
}

void setSensorSimulation(int inIndex, bool on) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    sensorSimulation[inIndex] = on;
    if (inIndex == findNeutralInIndex()) neutralSimulation = on;
    bleInDown[inIndex] = on;
    effectiveInputState[inIndex] = on;
}

bool isInputActive(int inIndex, bool physicalPressed) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    effectiveInputState[inIndex] = sensorSimulation[inIndex]
        ? bleInDown[inIndex]
        : physicalPressed || bleInDown[inIndex];
    return effectiveInputState[inIndex];
}

bool getEffectiveInputState(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    return effectiveInputState[inIndex];
}

void setBleInputPressed(int inIndex, bool pressed) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    bleInDown[inIndex] = pressed;
}

bool isBleInputPressed(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    return bleInDown[inIndex];
}

