#include "ble_protocol.h"
#include "ble_hub.h"
#include "config.h"
#include "blinkers.h"
#include "starter.h"
#include "arming.h"
#include "input_cfg.h"
#include "beams.h"
#include "display_hub.h"
#include <NimBLEDevice.h>
#include <cstdio>
#include <cstring>

// gOutputs / pCharacteristic z ble_hub
extern Output* gOutputs;
extern NimBLECharacteristic* pCharacteristic;
extern bool deviceConnected;
static char lastInputBits[INPUT_COUNT + 1] = {0};

void resetInputStatePush() {
    lastInputBits[0] = '\0';
}

static void sendStarterStatus(Output* outputs) {
    if (!deviceConnected || !pCharacteristic || !outputs) return;
    char msg[24];
    snprintf(msg, sizeof(msg), "STARTER_ENABLED:%d", isStarterEnabled(outputs) ? 1 : 0);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
}

void sendState(Output* outputs) {
    if (!deviceConnected || !pCharacteristic || !outputs) return;

    int li = blinkerLeftOutIndex();
    int ri = blinkerRightOutIndex();
    bool leftOn  = (currentMode == MODE_LEFT  || currentMode == MODE_HAZARD);
    bool rightOn = (currentMode == MODE_RIGHT || currentMode == MODE_HAZARD);

    char bits[11];
    for (int i = 0; i < 10; i++) {
        bool on = false;
        if (i == li) {
            on = leftOn;
        } else if (i == ri) {
            on = rightOn;
        } else if (isBeamOutput(i)) {
            on = (outLevel[i] > 20);
        } else {
            on = outputs[i].isOn();
        }
        int neutralIndex = findNeutralInIndex();
        if (neutralIndex >= 0) {
            uint8_t neutralOut = inputCfg[neutralIndex].outPrimary;
            if (outAssigned(neutralOut) && (int)neutralOut == i)
                on = isNeutralSimulation() || on;
        }
        bits[i] = on ? '1' : '0';
    }
    bits[10] = 0;

    char stateMsg[24];
    snprintf(stateMsg, sizeof(stateMsg), "STATE:%s", bits);
    pCharacteristic->setValue(stateMsg);
    pCharacteristic->notify();
    sendStarterStatus(outputs);
}

void sendInputStatesIfChanged(Button* buttons) {
    if (!deviceConnected || !pCharacteristic || !buttons) return;

    char bits[INPUT_COUNT + 1];
    const int neutralIndex = findNeutralInIndex();
    for (int i = 0; i < INPUT_COUNT; i++) {
        bool on = isInputActive(i, buttons[i].isPressed());
        if (i == neutralIndex && isNeutralSimulation()) on = true;
        bits[i] = on ? '1' : '0';
    }
    bits[INPUT_COUNT] = '\0';
    if (strcmp(bits, lastInputBits) == 0) return;

    strcpy(lastInputBits, bits);
    char msg[32];
    snprintf(msg, sizeof(msg), "INSTATE:%s", bits);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    sendStarterStatus(gOutputs);
}

void sendConfig() {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[48];
    snprintf(msg, sizeof(msg), "CFG:%d,%d,%d,%d,%d,%d,%d,%d",
             cfg.fadeSpeed, cfg.blinkCount, cfg.curve,
             cfg.autoCancelSpeed, cfg.autoCancel, cfg.autoLights,
             cfg.commonBrakePositionWire, cfg.positionBrightness);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.printf("CFG: %s\n", msg);
}

static int wireOut(uint8_t o) {
    return outAssigned(o) ? (int)o + 1 : 0;
}

void sendInputCfg() {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[400];
    int pos = snprintf(msg, sizeof(msg), "INCFG:");
    for (int i = 0; i < INPUT_COUNT; i++) {
        pos += snprintf(msg + pos, sizeof(msg) - pos, "%s%d,%d,%d,%d,%s",
                        (i ? ";" : ""),
                        (int)inputCfg[i].category,
                        wireOut(inputCfg[i].outPrimary),
                inputCfg[i].outputEnabled ? 1 : 0,
                wireOut(inputCfg[i].outPrimary),
                        inputCfg[i].name);
        if (pos >= (int)sizeof(msg) - 8) break;
    }
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.printf("INCFG sent (%d bytes)\n", pos);
    sendStarterStatus(gOutputs);
}

void sendModesV4() {
    if (!deviceConnected || !pCharacteristic) return;

    int lightsCount = 0;
    int brakeCount = 0;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == FN_LIGHTS_1 || inputCfg[i].functionId == FN_LIGHTS_2) lightsCount++;
        if (inputCfg[i].functionId == FN_BRAKE_1 || inputCfg[i].functionId == FN_BRAKE_2) brakeCount++;
    }

    // Flagi: 1=wybieralne, 0=ukryte w menu (bo osiagnieto limit)
    int canAddLight2 = (lightsCount < 2) ? 1 : 0;
    int canAddBrake2 = (brakeCount < 2) ? 1 : 0;

    // Definicje unikalnych funkcji (id, category, label, flags)
    struct ModeDef { int id; int cat; const char* label; int flag; };
    ModeDef defs[] = {
        {9, 0, "STARTER", 0},        // Fixed
        {1, 0, "LEFT BLINKER", 0},   // Fixed
        {2, 0, "RIGHT BLINKER", 0},  // Fixed
        {3, 0, "LIGHTS", 0},
        {4, 0, "LOW BEAM", canAddLight2},
        {12, 0, "USER DEFINED", 1},
        {13, 0, "KILL SWITCH", 1},

        {7, 1, "NEUTRAL", 0},
        {5, 1, "BRAKES", 0},
        {6, 1, "BRAKE", canAddBrake2},
        {8, 1, "CLUTCH", 1},
        {10, 1, "OIL", 1},
        {11, 1, "FUEL", 1},
        {12, 1, "USER DEFINED", 1},

        {0, 2, "DISABLED", 1}
    };

    pCharacteristic->setValue("MODES_START");
    pCharacteristic->notify();
    delay(50);

    for (const auto& d : defs) {
        char msg[64];
        snprintf(msg, sizeof(msg), "MODEPART:%d,%d,%s,%d", d.id, d.cat, d.label, d.flag);
        pCharacteristic->setValue(msg);
        pCharacteristic->notify();
        delay(50);
    }

    pCharacteristic->setValue("MODES_DONE");
    pCharacteristic->notify();
}

void sendInputCfgV4() {
    if (!deviceConnected || !pCharacteristic) return;

    pCharacteristic->setValue("INCFG_START");
    pCharacteristic->notify();
    delay(20);

    for (int i = 0; i < INPUT_COUNT; i++) {
        const InputCfgItem& item = inputCfg[i];
        char msg[128];
        snprintf(msg, sizeof(msg), "INPART:%d,%d,%d,%d,%d,%d,%d,%d,%d,%s",
                        i, (int)item.category, (int)item.functionId,
                        wireOut(item.outPrimary),
                        wireOut(item.outSecondary),
                        item.outputEnabled ? 1 : 0, wireOut(item.outPrimary),
                        item.fixed ? 1 : 0,
                        item.outLocked ? 1 : 0,
                        item.name);
        pCharacteristic->setValue(msg);
        pCharacteristic->notify();
        delay(20);
    }
    pCharacteristic->setValue("INCFG_DONE");
    pCharacteristic->notify();
}

void sendRpm(int rpm) {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[16];
    snprintf(msg, sizeof(msg), "RPM:%d", rpm);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
}

void sendFuel(int pct) {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[16];
    snprintf(msg, sizeof(msg), "FUEL:%d", pct);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
}

void sendOil(int on) {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[16];
    snprintf(msg, sizeof(msg), "OIL:%d", on ? 1 : 0);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
}

static void applyDigitalOrBeam(Output* outputs, int oi, bool on) {
    if (oi < 0 || oi > 9 || !outputs) return;
    if (isBlinkerOut(oi)) {
        Serial.printf("OUT_%d zablokowany (kierunek)\n", oi + 1);
        return;
    }
    if (isBeamOutput(oi)) {
        requestBeamLevel(oi, on ? 255 : 0);
        return;
    }
    if (on) outputs[oi].on();
    else    outputs[oi].off();
    setOutLevel(oi, on ? 255 : 0);
}

static void toggleOutputFromControl(Output* outputs, int oi) {
    if (oi < 0 || oi > 9 || !outputs) return;

    if (isBlinkerOut(oi)) {
        connectionBlink = false;
        toggleBlinkerFromControl(oi);
    } else if (isBeamOutput(oi)) {
        requestBeamLevel(oi, outLevel[oi] > 20 ? 0 : 255);
    } else {
        applyDigitalOrBeam(outputs, oi, !outputs[oi].isOn());
    }
}

void handleBleCommand(const char* value) {
    if (!value || !value[0]) return;
    Serial.printf("Otrzymano: %s\n", value);

    if (strcmp(value, "GET") == 0) {
        if (gOutputs) sendState(gOutputs);
        return;
    }
    if (strcmp(value, "GET_CFG") == 0) {
        sendConfig();
        return;
    }
    if (strcmp(value, "GET_INCFG") == 0) {
        sendInputCfgV4();
        return;
    }
    if (strcmp(value, "GET_MODES") == 0 || strcmp(value, "GET_MODES_DEF") == 0) {
        sendModesV4();
        return;
    }

    if (strcmp(value, "SET_INCFG_COMMIT") == 0) {
        InCfgApply r = commitInputCfg();
        if (r == INCFG_COMMITTED) {
            refreshBlinkerPins();
            setupBeams();
            if (gOutputs) sendState(gOutputs);
        } else if (r == INCFG_REJECTED) {
            bleLog(inputCfgRejectReason());
            sendInputCfgV4();
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    if (strncmp(value, "SET_INCFG_V4:", 13) == 0) {
        int inNum = 0, category = 0, functionId = 0;
        int primary = 0, secondary = 0, outputEnabled = 0, outputIndex = 0;
        char name[16] = {0};
        int n = sscanf(value + 13, "%d,%d,%d,%d,%d,%d,%d,%15s",
                       &inNum, &category, &functionId, &primary, &secondary,
                       &outputEnabled, &outputIndex, name);
        if (n >= 7 && inNum >= 1 && inNum <= 10 &&
            primary >= 0 && primary <= 10 && secondary >= 0 && secondary <= 10) {
            uint8_t pri = primary ? (uint8_t)(primary - 1) : OUT_NONE;
            uint8_t sec = secondary ? (uint8_t)(secondary - 1) : OUT_NONE;
            uint8_t oix = (outputIndex >= 1 && outputIndex <= 10)
                ? (uint8_t)(outputIndex - 1) : OUT_NONE;
            InCfgApply r = stageInputCfgV4(inNum - 1, (uint8_t)category,
                                           (uint8_t)functionId, pri, sec,
                                           outputEnabled != 0, oix,
                                           n >= 8 ? name : nullptr);
            Serial.printf("SET_INCFG_V4 IN_%d → %d\n", inNum, (int)r);
            if (r == INCFG_COMMITTED) {
                refreshBlinkerPins();
                setupBeams();
                if (gOutputs) sendState(gOutputs);
            } else if (r == INCFG_REJECTED) {
                bleLog(inputCfgRejectReason());
            }
        } else {
            Serial.println("SET_INCFG_V4 FAIL (range)");
        }
        return;
    }

    if (strncmp(value, "SET_CFG:", 8) == 0) {
        int fade = 12, blinks = 3, curve = 1, ac = 20, acOn = 1, lightsOn = 0;
        int commonWire = 0, positionBrightness = 50;
        int n = sscanf(value + 8, "%d,%d,%d,%d,%d,%d,%d,%d",
                       &fade, &blinks, &curve, &ac, &acOn, &lightsOn,
                       &commonWire, &positionBrightness);
        if (n >= 4) {
            cfg.fadeSpeed = (uint8_t)constrain(fade, 4, 40);
            cfg.blinkCount = (uint8_t)constrain(blinks, 2, 6);
            cfg.curve = (uint8_t)constrain(curve, 0, 2);
            if (n == 4) {
                cfg.autoCancel = (ac != 0) ? 1 : 0;
                if (ac < 5 || ac > 30) cfg.autoCancelSpeed = 20;
                else cfg.autoCancelSpeed = (uint8_t)ac;
            } else {
                cfg.autoCancelSpeed = (uint8_t)constrain(ac, 5, 30);
                cfg.autoCancel = acOn ? 1 : 0;
                if (n >= 6) cfg.autoLights = lightsOn ? 1 : 0;
                if (n >= 7) cfg.commonBrakePositionWire = commonWire ? 1 : 0;
                if (n >= 8)
                    cfg.positionBrightness = (uint8_t)constrain(positionBrightness, 1, 99);
            }
            saveConfig();
            sendConfig();
        }
        return;
    }

    if (strncmp(value, "SET_INCFG:", 10) == 0) {
        int inNum = 0, mode = 0, outNum = 0;
        int outputEnabled = 0, outputNum = 0;
        char name[16] = {0};
        int n = sscanf(value + 10, "%d,%d,%d,%d,%d,%15s",
                       &inNum, &mode, &outNum, &outputEnabled, &outputNum, name);
        if (n >= 3 && inNum >= 1 && inNum <= 10 && outNum >= 1 && outNum <= 10) {
            bool ok = setInputCfg(inNum - 1, (uint8_t)mode,
                                  (uint8_t)(outNum - 1),
                                  n >= 5 && outputEnabled != 0,
                                  n >= 5 && outputNum >= 1 && outputNum <= 10
                                      ? (uint8_t)(outputNum - 1)
                                      : (uint8_t)(outNum - 1),
                                  n >= 6 ? name : nullptr);
            Serial.println(ok ? "SET_INCFG OK" : "SET_INCFG FAIL");
            if (ok) {
                refreshBlinkerPins();
                setupBeams();
            }
            sendInputCfgV4();
            if (gOutputs) sendState(gOutputs);
        } else {
            Serial.println("SET_INCFG FAIL (range)");
        }
        return;
    }

    // Wirtualny przycisk IN_01..IN_10 (apka: press/release; ESP liczy short/long)
    if (strncmp(value, "IN:", 3) == 0) {
        int num = 0, state = 0;
        if (sscanf(value + 3, "%d:%d", &num, &state) == 2 &&
            num >= 1 && num <= 10) {
            setBleInputPressed(num - 1, state != 0);
            if (inputCfg[num - 1].category == CAT_SENSOR)
                setSensorSimulation(num - 1, state != 0);
            if (inputCfg[num - 1].functionId == FN_STARTER)
                setBleStarterPressed(state != 0);
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    if (strncmp(value, "NEUTRAL_TEST:", 13) == 0) {
        int state = 0;
        if (sscanf(value + 13, "%d", &state) == 1) {
            int neutralIndex = findNeutralInIndex();
            if (neutralIndex >= 0) setSensorSimulation(neutralIndex, state != 0);
            else setNeutralSimulation(state != 0);
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    // LEFT:0=off  LEFT:1=short(N)  LEFT:2=long(NS)
    if (strncmp(value, "LEFT:", 5) == 0) {
        int state = 0;
        if (sscanf(value + 5, "%d", &state) == 1) {
            connectionBlink = false;
            if (state == 0) {
                forceMode(MODE_OFF);
                Serial.println("LEFT OFF");
            } else if (state == 2) {
                applyLeftHoldLong();
                Serial.println("LEFT HOLD/NS");
            } else {
                applyLeftShort();
                Serial.println("LEFT SHORT/N");
            }
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    // RIGHT:0=off  RIGHT:1=short(N)  RIGHT:2=long(NS)
    if (strncmp(value, "RIGHT:", 6) == 0) {
        int state = 0;
        if (sscanf(value + 6, "%d", &state) == 1) {
            connectionBlink = false;
            if (state == 0) {
                forceMode(MODE_OFF);
                Serial.println("RIGHT OFF");
            } else if (state == 2) {
                applyRightHoldLong();
                Serial.println("RIGHT HOLD/NS");
            } else {
                applyRightShort();
                Serial.println("RIGHT SHORT/N");
            }
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    // HAZARD:0/1
    if (strncmp(value, "HAZARD:", 7) == 0) {
        int state = 0;
        if (sscanf(value + 7, "%d", &state) == 1) {
            connectionBlink = false;
            forceMode(state ? MODE_HAZARD : MODE_OFF);
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    // OUT:n:0/1 — wg inputCfg, BEZ twardego OUT_10 = starter
    if (strncmp(value, "OUT_TOGGLE:", 11) == 0) {
        int num = 0;
        if (sscanf(value + 11, "%d", &num) == 1 &&
            num >= 1 && num <= 10 && gOutputs) {
            toggleOutputFromControl(gOutputs, num - 1);
            sendState(gOutputs);
        }
        return;
    }

    if (strncmp(value, "OUT:", 4) == 0) {
        int num = 0, state = 0;
        if (sscanf(value + 4, "%d:%d", &num, &state) == 2 &&
            num >= 1 && num <= 10 && gOutputs) {
            int oi = num - 1;

            bool isLeft = false, isRight = false;
            bool isStarterOut = false;
            for (int i = 0; i < INPUT_COUNT; i++) {
                if (!outAssigned(inputCfg[i].outPrimary) ||
                    (int)inputCfg[i].outPrimary != oi) continue;
                if (inputCfg[i].functionId == FN_LEFT)    isLeft = true;
                if (inputCfg[i].functionId == FN_RIGHT)   isRight = true;
                if (inputCfg[i].functionId == FN_STARTER) isStarterOut = true;
            }

            if (isLeft && !isRight) {
                connectionBlink = false;
                if (state) applyLeftHoldLong();
                else forceMode(MODE_OFF);
                Serial.println(state ? "LEFT TOGGLE/NS" : "LEFT OFF");
            } else if (isRight && !isLeft) {
                connectionBlink = false;
                if (state) applyRightHoldLong();
                else forceMode(MODE_OFF);
                Serial.println(state ? "RIGHT TOGGLE/NS" : "RIGHT OFF");
            } else if (isStarterOut) {
                // tylko OUT przypisany do STARTER w InputSettings
                setBleStarterPressed(state != 0);
                Serial.printf("STARTER via OUT_%d → %s\n", num, state ? "press" : "release");
            } else {
                applyDigitalOrBeam(gOutputs, oi, state != 0);
                Serial.printf("Wyjście %d → %s\n", num, state ? "ON" : "OFF");
            }
            sendState(gOutputs);
        }
        return;
    }

    // IN10: = wirtualny przycisk startera (Dashboard / Control), niezależny od numeru OUT
    if (strncmp(value, "IN10:", 5) == 0) {
        int state = 0;
        if (sscanf(value + 5, "%d", &state) == 1)
            setBleStarterPressed(state != 0);
        return;
    }
    if (strncmp(value, "SPEED:", 6) == 0) {
        float kmh = 0;
        if (sscanf(value + 6, "%f", &kmh) == 1) {
            setCurrentSpeed(kmh);
            if (applyAutoLights(kmh) && gOutputs) sendState(gOutputs);
        }
        return;
    }

    if (strcmp(value, "SHUTDOWN_NOW") == 0) {
        if (gOutputs) requestShutdown(gOutputs);
        return;
    }
}

