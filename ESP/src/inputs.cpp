#include "inputs.h"
#include <Preferences.h>
#include <string.h>
#include "beams.h"

InputCfgItem inputCfg[9];

static void setDefaults() {
    for (int i = 0; i < 9; i++) {
        inputCfg[i].mode = IN_TOGGLE;
        inputCfg[i].outIndex = (uint8_t)i;
        snprintf(inputCfg[i].name, sizeof(inputCfg[i].name), "IN_%d", i + 1);
    }
    // domyślne kierunki
    inputCfg[0].mode = IN_LEFT;
    strncpy(inputCfg[0].name, "Kierunek L", 15);
    inputCfg[0].name[15] = '\0';

    inputCfg[4].mode = IN_RIGHT;
    strncpy(inputCfg[4].name, "Kierunek P", 15);
    inputCfg[4].name[15] = '\0';
}

void loadInputModes() {
    setDefaults();

    Preferences p;
    if (!p.begin("yh_in", false)) {
        Serial.println("INCFG: NVS open fail → defaults");
        return;
    }

    if (!p.isKey("ok")) {
        p.putBool("ok", true);
        for (int i = 0; i < 9; i++) {
            char k[8];
            snprintf(k, sizeof(k), "m%d", i);
            p.putUChar(k, inputCfg[i].mode);
            snprintf(k, sizeof(k), "o%d", i);
            p.putUChar(k, inputCfg[i].outIndex);
            snprintf(k, sizeof(k), "n%d", i);
            p.putString(k, inputCfg[i].name);
        }
        p.end();
        Serial.println("INCFG: defaults saved");
        return;
    }

    for (int i = 0; i < 9; i++) {
        char k[8];
        snprintf(k, sizeof(k), "m%d", i);
        inputCfg[i].mode = p.getUChar(k, inputCfg[i].mode);
        if (inputCfg[i].mode > IN_DISABLED) inputCfg[i].mode = IN_TOGGLE;

        snprintf(k, sizeof(k), "o%d", i);
        inputCfg[i].outIndex = p.getUChar(k, inputCfg[i].outIndex);
        if (inputCfg[i].outIndex > 8) inputCfg[i].outIndex = (uint8_t)i;

        snprintf(k, sizeof(k), "n%d", i);
        String s = p.getString(k, inputCfg[i].name);
        strncpy(inputCfg[i].name, s.c_str(), 15);
        inputCfg[i].name[15] = '\0';
    }
    p.end();
    Serial.println("INCFG: loaded");
}

void saveInputModes() {
    Preferences p;
    if (!p.begin("yh_in", false)) return;
    p.putBool("ok", true);
    for (int i = 0; i < 9; i++) {
        char k[8];
        snprintf(k, sizeof(k), "m%d", i);
        p.putUChar(k, inputCfg[i].mode);
        snprintf(k, sizeof(k), "o%d", i);
        p.putUChar(k, inputCfg[i].outIndex);
        snprintf(k, sizeof(k), "n%d", i);
        p.putString(k, inputCfg[i].name);
    }
    p.end();
}

bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex, const char* name) {
    if (inIndex < 0 || inIndex > 8) return false;
    if (outIndex > 8) return false;
    if (mode > IN_DISABLED) return false;

    inputCfg[inIndex].mode = mode;
    inputCfg[inIndex].outIndex = outIndex;
    if (name && name[0]) {
        strncpy(inputCfg[inIndex].name, name, 15);
        inputCfg[inIndex].name[15] = '\0';
    }
    saveInputModes();
    return true;
}

void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged) {
    for (int i = 0; i < 9; i++) {
        uint8_t mode = inputCfg[i].mode;
        if (mode == IN_DISABLED || mode == IN_LEFT || mode == IN_RIGHT) continue;

        uint8_t oi = inputCfg[i].outIndex;
        if (oi > 8) continue;

        bool pressed = buttons[i].isPressed();

        switch (mode) {
            case IN_TOGGLE:
                if (buttons[i].wasPressed()) {
                    if (isBeamOutput(oi)) {
            // fade: jeśli poziom > 0 → gaś, inaczej zapal
            // prosty toggle względem targetu
            static uint8_t beamOn[9] = {0};
            beamOn[oi] = beamOn[oi] ? 0 : 255;
            requestBeamLevel(oi, beamOn[oi]);
        } else {
            outputs[oi].toggle();
        }
        stateChanged = true;
    }
    break;

            case IN_MOMENT: {
                static bool last[9] = {false};
                if (pressed != last[i]) {
                    last[i] = pressed;
                    if (pressed) outputs[oi].on();
                    else outputs[oi].off();
                    stateChanged = true;
                }
                break;
            }

            case IN_SENSOR:
                // na razie jak moment (HIGH = on)
                {
                    static bool lastS[9] = {false};
                    if (pressed != lastS[i]) {
                        lastS[i] = pressed;
                        if (pressed) outputs[oi].on();
                        else outputs[oi].off();
                        stateChanged = true;
                    }
                }
                break;

            default:
                break;
        }
    }
}