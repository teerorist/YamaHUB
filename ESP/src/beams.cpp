#include "beams.h"
#include "inputs.h"
#include "config.h"
#include "display_hub.h"
#include "blinkers.h"
#include "pins.h"
#include "driver/gpio.h"
#include <string.h>
#include <stdlib.h>
#include <math.h>

static const unsigned long LIGHTS_LONG_MS = 400;

static const int PWM_CH_BASE = 4; // 4.. – 2/3 kierunki, 7 BL LCD
static const int MAX_BEAMS = 4;

static int beamOut[MAX_BEAMS];
static int beamCh[MAX_BEAMS];
static int beamLevel[MAX_BEAMS];
static int beamTarget[MAX_BEAMS];
static unsigned long beamLast[MAX_BEAMS];
static int beamCount = 0;

static const int OUT_PINS[10] = {
    OUT_1, OUT_2, OUT_3, OUT_4, OUT_5,
    OUT_6, OUT_7, OUT_8, OUT_9, OUT_10
};

static bool nameIsBeam(const char* n) {
    if (!n) return false;
    // LIGHTS / beam / hi / low
    if (strstr(n, "ight") || strstr(n, "IGHT")) return true; // light/lights
    if (strstr(n, "beam") || strstr(n, "Beam") || strstr(n, "BEAM")) return true;
    if (strstr(n, "hi") || strstr(n, "Hi") || strstr(n, "HI")) return true;
    if (strstr(n, "low") || strstr(n, "Low")) return true;
    return false;
}

bool isBeamOutput(int outIndex) {
    for (int i = 0; i < beamCount; i++)
        if (beamOut[i] == outIndex) return true;
    return false;
}

static int parseTaggedOut(const char* n, const char* tag) {
    if (!n || !tag) return -1;
    const char* p = strstr(n, tag);
    if (!p) return -1;
    int num = atoi(p + (int)strlen(tag));
    if (num < 1 || num > 10) return -1;
    return num - 1;
}

static int parseLightsHiOut(const char* n) { return parseTaggedOut(n, "_H"); }
static int parseLightsLowOut(const char* n) { return parseTaggedOut(n, "_L"); }

bool isLightsInput(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    if (inputCfg[inIndex].mode != IN_TOGGLE) return false;
    if (!nameIsBeam(inputCfg[inIndex].name)) return false;
    int oi = (int)inputCfg[inIndex].outIndex;
    if (oi < 0 || oi > 9) return false;
    if (isBlinkerOut(oi)) return false;
    return true;
}

static bool isFirstLightsIn(int inIndex) {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (!isLightsInput(i)) continue;
        return i == inIndex;
    }
    return false;
}

static int lowOutFromCfg() {
    int first = -1, second = -1;
    const char* firstName = nullptr;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (!isLightsInput(i)) continue;
        int oi = (int)inputCfg[i].outIndex;
        if (first < 0) {
            first = oi;
            firstName = inputCfg[i].name;
        } else {
            second = oi;
            break;
        }
    }
    if (second >= 0) return second;
    int L = parseLightsLowOut(firstName);
    if (L >= 0) return L;
    int H = parseLightsHiOut(firstName);
    if (H >= 0) return first;
    return first;
}

static int hiOutFromCfg() {
    int first = -1, second = -1;
    const char* firstName = nullptr;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (!isLightsInput(i)) continue;
        int oi = (int)inputCfg[i].outIndex;
        if (first < 0) {
            first = oi;
            firstName = inputCfg[i].name;
        } else {
            second = oi;
            break;
        }
    }
    if (second >= 0) return first;
    int L = parseLightsLowOut(firstName);
    if (L >= 0) return first;
    int H = parseLightsHiOut(firstName);
    if (H >= 0) return H;
    return first;
}

static int hiOutForLow(int lowOut) {
    int hi = hiOutFromCfg();
    if (hi >= 0 && hi != lowOut) return hi;
    return -1;
}

static bool beamWantOn(int outIndex) {
    for (int i = 0; i < beamCount; i++) {
        if (beamOut[i] == outIndex) return beamTarget[i] > 20;
    }
    return outLevel[outIndex] > 20;
}

static void setBeamOn(int outIndex, bool on, Output* outputs) {
    if (outIndex < 0 || outIndex > 9) return;
    uint8_t t = on ? 255 : 0;
    if (isBeamOutput(outIndex)) {
        requestBeamLevel(outIndex, t);
        return;
    }
    if (!outputs) return;
    if (on) outputs[outIndex].on();
    else    outputs[outIndex].off();
    setOutLevel(outIndex, t);
}

static int countLightsInputs() {
    int n = 0;
    for (int i = 0; i < INPUT_COUNT; i++)
        if (isLightsInput(i)) n++;
    return n;
}

static bool hiLatched = false;

void handleLightsInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged) {
    if (!isLightsInput(inIndex)) return;

    static bool held[INPUT_COUNT] = {false};
    static bool longDone[INPUT_COUNT] = {false};
    static bool pressHiOn[INPUT_COUNT] = {false};
    static bool pressUnlatched[INPUT_COUNT] = {false};
    static unsigned long downAt[INPUT_COUNT] = {0};

    bool now = btn.isPressed() || isBleInputPressed(inIndex);
    int nL = countLightsInputs();
    bool isFirst = isFirstLightsIn(inIndex);
    int lowNamed = parseLightsLowOut(inputCfg[inIndex].name);
    int hiNamed = parseLightsHiOut(inputCfg[inIndex].name);
    bool versionII = (nL == 1 && (lowNamed >= 0 || hiNamed >= 0));
    bool isLowBtn = (nL >= 2 && !isFirst);
    bool isHiBtn = isFirst || versionII || nL == 1;

    int lowOut = lowOutFromCfg();
    int hiOut = hiOutFromCfg();

    if (isLowBtn) {
        if (now && !held[inIndex]) {
            bool wasOn = beamWantOn(lowOut);
            setBeamOn(lowOut, !wasOn, outputs);
            if (wasOn) {
                int hi = hiOutForLow(lowOut);
                if (hi >= 0) setBeamOn(hi, false, outputs);
                hiLatched = false;
            }
            stateChanged = true;
            Serial.printf("LIGHTS IN_%d LOW → %s\n", inIndex + 1, wasOn ? "off" : "on");
        }
        held[inIndex] = now;
        return;
    }

    if (!isHiBtn || hiOut < 0) {
        held[inIndex] = now;
        return;
    }

    bool lowOn = (lowOut >= 0) && beamWantOn(lowOut);
    bool allowLatch = versionII || lowOn;

    if (now && !held[inIndex]) {
        downAt[inIndex] = millis();
        longDone[inIndex] = false;
        pressHiOn[inIndex] = false;
        pressUnlatched[inIndex] = false;
        if (beamWantOn(hiOut)) {
            setBeamOn(hiOut, false, outputs);
            hiLatched = false;
            pressUnlatched[inIndex] = true;
            stateChanged = true;
            Serial.printf("LIGHTS IN_%d HI off\n", inIndex + 1);
        } else {
            setBeamOn(hiOut, true, outputs);
            pressHiOn[inIndex] = true;
            stateChanged = true;
            Serial.printf("LIGHTS IN_%d HI pass\n", inIndex + 1);
        }
    }
    if (now && !longDone[inIndex] && !pressUnlatched[inIndex] &&
        downAt[inIndex] && (millis() - downAt[inIndex]) > LIGHTS_LONG_MS) {
        longDone[inIndex] = true;
        if (allowLatch && pressHiOn[inIndex]) {
            hiLatched = true;
            Serial.printf("LIGHTS IN_%d HI latch\n", inIndex + 1);
        }
    }
    if (!now && held[inIndex]) {
        if (!hiLatched && pressHiOn[inIndex]) {
            setBeamOn(hiOut, false, outputs);
            stateChanged = true;
            Serial.printf("LIGHTS IN_%d HI pass end\n", inIndex + 1);
        }
        downAt[inIndex] = 0;
        longDone[inIndex] = false;
        pressHiOn[inIndex] = false;
        pressUnlatched[inIndex] = false;
    }
    held[inIndex] = now;
}

int lowBeamOutIndex() {
    return lowOutFromCfg();
}

bool applyAutoLights(float kmh) {
    if (!cfg.autoLights) return false;
    if (kmh < (float)cfg.autoCancelSpeed) return false;
    int oi = lowBeamOutIndex();
    if (oi < 0 || !isBeamOutput(oi)) return false;
    if (outLevel[oi] > 20) return false;
    requestBeamLevel(oi, 255);
    Serial.printf("AutoLights ON (OUT_%d @ %.0f km/h)\n", oi + 1, (double)kmh);
    return true;
}

static float curveFactor(float t) {
    if (cfg.curve == 1) return t * t * (3.0f - 2.0f * t);
    if (cfg.curve == 2) return (t < 0.5f) ? 0.0f : 1.0f;
    return t;
}

void setupBeams() {
    // odłącz poprzednie
    for (int i = 0; i < beamCount; i++) {
        ledcDetachPin(OUT_PINS[beamOut[i]]);
        gpio_reset_pin((gpio_num_t)OUT_PINS[beamOut[i]]);
        pinMode(OUT_PINS[beamOut[i]], OUTPUT);
        digitalWrite(OUT_PINS[beamOut[i]], LOW);
        setOutLevel(beamOut[i], 0);
    }
    beamCount = 0;

    for (int i = 0; i < INPUT_COUNT && beamCount < MAX_BEAMS; i++) {
        if (inputCfg[i].mode == IN_DISABLED || inputCfg[i].mode == IN_SENSOR) continue;
        if (inputCfg[i].mode == IN_LEFT || inputCfg[i].mode == IN_RIGHT) continue;
        if (inputCfg[i].mode == IN_STARTER) continue;
        if (!nameIsBeam(inputCfg[i].name)) continue;

        int oi = (int)inputCfg[i].outIndex;
        if (oi < 0 || oi > 9) continue;
        if (isBlinkerOut(oi)) continue; // NIGDY pin kierunku

        auto attach = [&](int out, const char* tag) {
            if (out < 0 || out > 9 || beamCount >= MAX_BEAMS) return;
            if (isBlinkerOut(out) || isBeamOutput(out)) return;
            int ch = PWM_CH_BASE + beamCount;
            ledcSetup(ch, 5000, 8);
            ledcAttachPin(OUT_PINS[out], ch);
            ledcWrite(ch, 0);
            beamOut[beamCount] = out;
            beamCh[beamCount] = ch;
            beamLevel[beamCount] = 0;
            beamTarget[beamCount] = 0;
            beamLast[beamCount] = millis();
            beamCount++;
            Serial.printf("Beam: OUT_%d ch=%d %s\n", out + 1, ch, tag);
        };

        attach(oi, inputCfg[i].name);
        int extraL = parseLightsLowOut(inputCfg[i].name);
        int extraH = parseLightsHiOut(inputCfg[i].name);
        if (extraL >= 0 && extraL != oi) attach(extraL, "LOW");
        if (extraH >= 0 && extraH != oi) attach(extraH, "HI");
    }
    Serial.printf("Beams: %d\n", beamCount);

    // jeden IN świateł (drugi wyłączony / wersja II) → LOW on
    if (countLightsInputs() == 1) {
        int low = lowBeamOutIndex();
        if (low >= 0 && isBeamOutput(low)) {
            requestBeamLevel(low, 255);
            Serial.printf("LIGHTS: jeden IN → LOW ON (OUT_%d)\n", low + 1);
        }
    }
}

void requestBeamLevel(int outIndex, uint8_t target) {
    for (int i = 0; i < beamCount; i++) {
        if (beamOut[i] != outIndex) continue;
        beamTarget[i] = target;   // ten sam fade/curve co kierunki
        return;
    }
    // nie jest beamem – caller zrobi digital
}

void updateBeams(bool& stateChanged) {
    if (beamCount == 0) return;
    unsigned long step = (unsigned long)cfg.fadeSpeed;
    if (step < 4) step = 4;
    unsigned long now = millis();

    for (int i = 0; i < beamCount; i++) {
        if (beamLevel[i] == beamTarget[i]) continue;
        if (now - beamLast[i] < step) continue;
        beamLast[i] = now;

        int delta = 8;
        if (beamLevel[i] < beamTarget[i]) {
            beamLevel[i] += delta;
            if (beamLevel[i] > beamTarget[i]) beamLevel[i] = beamTarget[i];
        } else {
            beamLevel[i] -= delta;
            if (beamLevel[i] < beamTarget[i]) beamLevel[i] = beamTarget[i];
        }
        int out = (int)(curveFactor(beamLevel[i] / 255.0f) * 255.0f);
        ledcWrite(beamCh[i], out);
        uint8_t prevVis = outLevel[beamOut[i]];
        setOutLevel(beamOut[i], (uint8_t)out);
        if ((prevVis > 20) != (out > 20)) stateChanged = true;
    }
}
