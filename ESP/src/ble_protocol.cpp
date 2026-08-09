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
        bits[i] = on ? '1' : '0';
    }
    bits[10] = 0;

    char stateMsg[24];
    snprintf(stateMsg, sizeof(stateMsg), "STATE:%s", bits);
    pCharacteristic->setValue(stateMsg);
    pCharacteristic->notify();
}

void sendConfig() {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[40];
    snprintf(msg, sizeof(msg), "CFG:%d,%d,%d,%d,%d",
             cfg.fadeSpeed, cfg.blinkCount, cfg.curve,
             cfg.autoCancelSpeed, cfg.beamFade);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.printf("CFG: %s\n", msg);
}

void sendInputCfg() {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[400];
    int pos = snprintf(msg, sizeof(msg), "INCFG:");
    for (int i = 0; i < INPUT_COUNT; i++) {
        pos += snprintf(msg + pos, sizeof(msg) - pos, "%s%d,%d,%s",
                        (i ? ";" : ""),
                        (int)inputCfg[i].mode,
                        (int)inputCfg[i].outIndex + 1,
                        inputCfg[i].name);
        if (pos >= (int)sizeof(msg) - 8) break;
    }
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.printf("INCFG sent (%d bytes)\n", pos);
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
        sendInputCfg();
        return;
    }

    if (strncmp(value, "SET_CFG:", 8) == 0) {
        int fade = 12, blinks = 3, curve = 1, ac = 20, beamFade = (int)cfg.beamFade;
        int n = sscanf(value + 8, "%d,%d,%d,%d,%d",
                       &fade, &blinks, &curve, &ac, &beamFade);
        if (n >= 4) {
            cfg.fadeSpeed = (uint8_t)constrain(fade, 4, 40);
            cfg.blinkCount = (uint8_t)constrain(blinks, 1, 20);
            cfg.curve = (uint8_t)constrain(curve, 0, 2);
            cfg.autoCancelSpeed = (uint8_t)constrain(ac, 0, 200);
            if (n >= 5) cfg.beamFade = beamFade ? 1 : 0;
            saveConfig();
            sendConfig();
        }
        return;
    }

    if (strncmp(value, "SET_INCFG:", 10) == 0) {
        int inNum = 0, mode = 0, outNum = 0;
        char name[16] = {0};
        int n = sscanf(value + 10, "%d,%d,%d,%15s",
                       &inNum, &mode, &outNum, name);
        if (n >= 3 && inNum >= 1 && inNum <= 10 && outNum >= 1 && outNum <= 10) {
            bool ok = setInputCfg(inNum - 1, (uint8_t)mode,
                                  (uint8_t)(outNum - 1),
                                  n >= 4 ? name : nullptr);
            Serial.println(ok ? "SET_INCFG OK" : "SET_INCFG FAIL");
            refreshBlinkerPins();
            setupBeams();
            sendInputCfg();
        } else {
            Serial.println("SET_INCFG FAIL (range)");
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

    // OUT:n:0/1 — TYLKO po inputCfg.mode, BEZ fallbacku 1=L / 5=P
    if (strncmp(value, "OUT:", 4) == 0) {
        int num = 0, state = 0;
        if (sscanf(value + 4, "%d:%d", &num, &state) == 2 &&
            num >= 1 && num <= 10 && gOutputs) {
            int oi = num - 1;

            bool isLeft = false, isRight = false;
            for (int i = 0; i < INPUT_COUNT; i++) {
                if ((int)inputCfg[i].outIndex != oi) continue;
                if (inputCfg[i].mode == IN_LEFT)  isLeft = true;
                if (inputCfg[i].mode == IN_RIGHT) isRight = true;
            }

            if (isLeft && !isRight) {
                connectionBlink = false;
                if (state) applyLeftShort();
                else forceMode(MODE_OFF);
                Serial.println(state ? "LEFT SHORT/N" : "LEFT OFF");
            } else if (isRight && !isLeft) {
                connectionBlink = false;
                if (state) applyRightShort();
                else forceMode(MODE_OFF);
                Serial.println(state ? "RIGHT SHORT/N" : "RIGHT OFF");
            } else if (oi == starterOutIndex() || num == 10) {
                setBleStarterPressed(state != 0);
            } else {
                applyDigitalOrBeam(gOutputs, oi, state != 0);
                Serial.printf("Wyjście %d → %s\n", num, state ? "ON" : "OFF");
            }
            sendState(gOutputs);
        }
        return;
    }

    if (strncmp(value, "IN10:", 5) == 0) {
        int state = 0;
        if (sscanf(value + 5, "%d", &state) == 1)
            setBleStarterPressed(state != 0);
        return;
    }

    if (strncmp(value, "SPEED:", 6) == 0) {
        float kmh = 0;
        if (sscanf(value + 6, "%f", &kmh) == 1)
            setCurrentSpeed(kmh);
        return;
    }

    if (strcmp(value, "SHUTDOWN_NOW") == 0) {
        if (gOutputs) requestShutdown(gOutputs);
        return;
    }
}

