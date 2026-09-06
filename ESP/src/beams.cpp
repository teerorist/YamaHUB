#include "beams.h"
#include "inputs.h"
#include "config.h"
#include "display_hub.h"
#include "blinkers.h"
#include "pins.h"
#include "driver/gpio.h"
#include <math.h>

static const unsigned long MS_SHORT = 400;
static const unsigned long MS_LATCH_HI = 1500;
static const unsigned long MS_KILL_LOW = 3500;

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

bool isBeamOutput(int outIndex) {
    for (int i = 0; i < beamCount; i++)
        if (beamOut[i] == outIndex) return true;
    return false;
}

bool isLightsInput(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    if (inputCfg[inIndex].functionId != FN_LIGHTS_1 &&
        inputCfg[inIndex].functionId != FN_LIGHTS_2) return false;
    int oi = (int)inputCfg[inIndex].outPrimary;
    if (!outAssigned((uint8_t)oi)) return false;
    if (isBlinkerOut(oi)) return false;
    return true;
}

// HI = LIGHTS 1, LOW = LIGHTS 2 (v4.9 rule)
static int hiOutFromCfg() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == FN_LIGHTS_1) return inputCfg[i].outPrimary;
    }
    return -1;
}

static int lowOutFromCfg() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == FN_LIGHTS_1 && outAssigned(inputCfg[i].outSecondary))
            return inputCfg[i].outSecondary;
    }
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == FN_LIGHTS_2 && outAssigned(inputCfg[i].outPrimary))
            return inputCfg[i].outPrimary;
    }
    return -1;
}

static bool beamWantOn(int outIndex) {
    for (int i = 0; i < beamCount; i++) {
        if (beamOut[i] == outIndex) return beamTarget[i] > 20;
    }
    return outLevel[outIndex] > 20;
}

static void setBeamOn(int outIndex, bool on, Output* outputs, bool immediate = false) {
    if (outIndex < 0 || outIndex > 9) return;
    uint8_t t = on ? 255 : 0;
    if (isBeamOutput(outIndex)) {
        if (immediate) {
            for (int i = 0; i < beamCount; i++) {
                if (beamOut[i] != outIndex) continue;
                beamTarget[i] = t;
                beamLevel[i] = t;
                if (pinValid(OUT_PINS[outIndex])) ledcWrite(beamCh[i], t);
                setOutLevel(outIndex, t);
                return;
            }
        }
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

void handleLightsInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged) {
    if (!isLightsInput(inIndex)) return;

    static bool held[INPUT_COUNT] = {false};
    static unsigned long downAt[INPUT_COUNT] = {0};
    static bool lowOnThisPress[INPUT_COUNT] = {false};
    static bool hiLatchedAtDown[INPUT_COUNT] = {false};
    static bool killDone[INPUT_COUNT] = {false};

    bool now = btn.isPressed() || isBleInputPressed(inIndex);
    int nL = countLightsInputs();

    int hiIn = -1, lowIn = -1;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (!isLightsInput(i)) continue;
        if (inputCfg[i].functionId == FN_LIGHTS_1) hiIn = i;
        if (inputCfg[i].functionId == FN_LIGHTS_2) lowIn = i;
    }

    int hiOut = hiOutFromCfg();
    int lowOut = lowOutFromCfg();
    bool lowOn = beamWantOn(lowOut);
    bool hiOn = beamWantOn(hiOut);

    auto hiLatchWindow = [](unsigned long ms) {
        return ms >= MS_SHORT && ms <= MS_LATCH_HI;
    };

    if (nL >= 2) {
        if (inIndex == lowIn) {
            if (now && !held[inIndex]) {
                downAt[inIndex] = millis();
                lowOnThisPress[inIndex] = !lowOn;
                if (!lowOn) {
                    setBeamOn(lowOut, true, outputs);
                    stateChanged = true;
                    Serial.println("LOW ON");
                }
            }
            if (!now && held[inIndex]) {
                unsigned long ms = millis() - downAt[inIndex];
                if (!lowOnThisPress[inIndex] && ms > MS_SHORT) {
                    setBeamOn(lowOut, false, outputs);
                    setBeamOn(hiOut, false, outputs, true);
                    stateChanged = true;
                    Serial.println("LOW OFF (HI off)");
                }
            }
        } else if (inIndex == hiIn) {
            if (now && !held[inIndex]) {
                downAt[inIndex] = millis();
                hiLatchedAtDown[inIndex] = hiOn;
                if (hiOn) {
                    setBeamOn(hiOut, false, outputs, true);
                    stateChanged = true;
                    Serial.println("HI OFF");
                } else {
                    setBeamOn(hiOut, true, outputs, true);
                    stateChanged = true;
                    Serial.println("HI PASS start");
                }
            }
            if (!now && held[inIndex]) {
                unsigned long ms = millis() - downAt[inIndex];
                if (!hiLatchedAtDown[inIndex]) {
                    bool latch = beamWantOn(lowOut) && hiLatchWindow(ms);
                    if (!latch) {
                        setBeamOn(hiOut, false, outputs, true);
                        stateChanged = true;
                        Serial.println("HI PASS end");
                    } else {
                        Serial.println("HI LATCH");
                    }
                }
            }
        }
    } else {
        if (now && !held[inIndex]) {
            downAt[inIndex] = millis();
            killDone[inIndex] = false;
            lowOnThisPress[inIndex] = !lowOn;
            hiLatchedAtDown[inIndex] = hiOn;
            if (!lowOn) {
                setBeamOn(lowOut, true, outputs);
                stateChanged = true;
                Serial.println("LOW ON (shared)");
            } else if (hiOn) {
                setBeamOn(hiOut, false, outputs, true);
                stateChanged = true;
                Serial.println("HI OFF (shared)");
            } else {
                setBeamOn(hiOut, true, outputs, true);
                stateChanged = true;
                Serial.println("HI PASS start (shared)");
            }
        }
        if (now && !killDone[inIndex] && downAt[inIndex] > 0 &&
            (millis() - downAt[inIndex] > MS_KILL_LOW)) {
            killDone[inIndex] = true;
            setBeamOn(lowOut, false, outputs);
            setBeamOn(hiOut, false, outputs, true);
            stateChanged = true;
            Serial.println("LIGHTS KILL (>3.5s)");
        }
        if (!now && held[inIndex]) {
            unsigned long ms = millis() - downAt[inIndex];
            if (killDone[inIndex] || lowOnThisPress[inIndex] || hiLatchedAtDown[inIndex]) {
                // LOW już zapalony tym gestem / HI już zgaszony / kill zrobiony
            } else {
                bool latch = hiLatchWindow(ms);
                if (!latch) {
                    setBeamOn(hiOut, false, outputs, true);
                    stateChanged = true;
                    Serial.println("HI PASS end (shared)");
                } else {
                    Serial.println("HI LATCH (shared)");
                }
            }
        }
    }

    held[inIndex] = now;
}

int lowBeamOutIndex() {
    return lowOutFromCfg();
}

int hiBeamOutIndex() {
    return hiOutFromCfg();
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
    for (int i = 0; i < beamCount; i++) {
        int gpio = OUT_PINS[beamOut[i]];
        if (pinValid(gpio)) {
            ledcDetachPin(gpio);
            gpio_reset_pin((gpio_num_t)gpio);
            pinMode(gpio, OUTPUT);
            digitalWrite(gpio, LOW);
        }
        setOutLevel(beamOut[i], 0);
    }
    beamCount = 0;

    auto attach = [&](int out, const char* tag) {
        if (out < 0 || out > 9 || beamCount >= MAX_BEAMS) return;
        if (isBlinkerOut(out) || isBeamOutput(out)) return;
        int gpio = OUT_PINS[out];
        int ch = PWM_CH_BASE + beamCount;
        if (pinValid(gpio)) {
            ledcSetup(ch, 5000, 8);
            ledcAttachPin(gpio, ch);
            ledcWrite(ch, 0);
        }
        beamOut[beamCount] = out;
        beamCh[beamCount] = ch;
        beamLevel[beamCount] = 0;
        beamTarget[beamCount] = 0;
        beamLast[beamCount] = millis();
        beamCount++;
        Serial.printf("Beam: OUT_%d ch=%d %s\n", out + 1, ch, tag);
    };

    for (int i = 0; i < INPUT_COUNT && beamCount < MAX_BEAMS; i++) {
        uint8_t fn = inputCfg[i].functionId;
        if (fn != FN_LIGHTS_1 && fn != FN_LIGHTS_2) continue;

        int oi = (int)inputCfg[i].outPrimary;
        if (!outAssigned((uint8_t)oi) || isBlinkerOut(oi)) continue;
        attach(oi, inputCfg[i].name);
        if (fn == FN_LIGHTS_1 && outAssigned(inputCfg[i].outSecondary) &&
            inputCfg[i].outSecondary != (uint8_t)oi)
            attach((int)inputCfg[i].outSecondary, "LOW");
    }
    Serial.printf("Beams: %d\n", beamCount);
}

void requestBeamLevel(int outIndex, uint8_t target) {
    for (int i = 0; i < beamCount; i++) {
        if (beamOut[i] != outIndex) continue;
        beamTarget[i] = target;
        return;
    }
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
        if (pinValid(OUT_PINS[beamOut[i]])) ledcWrite(beamCh[i], out);
        uint8_t prevVis = outLevel[beamOut[i]];
        setOutLevel(beamOut[i], (uint8_t)out);
        if ((prevVis > 20) != (out > 20)) stateChanged = true;
    }
}
