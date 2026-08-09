#include "beams.h"
#include "inputs.h"
#include "config.h"
#include "display_hub.h"
#include "blinkers.h"
#include "pins.h"
#include "driver/gpio.h"
#include <string.h>
#include <math.h>

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

        bool exists = false;
        for (int j = 0; j < beamCount; j++)
            if (beamOut[j] == oi) exists = true;
        if (exists) continue;

        // drugi OUT z LIGHTS_H{n}
        // (obsługa przy request – tu primary)

        int ch = PWM_CH_BASE + beamCount;
        ledcSetup(ch, 5000, 8);
        ledcAttachPin(OUT_PINS[oi], ch);
        ledcWrite(ch, 0);

        beamOut[beamCount] = oi;
        beamCh[beamCount] = ch;
        beamLevel[beamCount] = 0;
        beamTarget[beamCount] = 0;
        beamLast[beamCount] = millis();
        beamCount++;
        Serial.printf("Beam: OUT_%d ch=%d name=%s\n", oi + 1, ch, inputCfg[i].name);
    }
    Serial.printf("Beams: %d\n", beamCount);
}

void requestBeamLevel(int outIndex, uint8_t target) {
    for (int i = 0; i < beamCount; i++) {
        if (beamOut[i] != outIndex) continue;
        if (!cfg.beamFade) {
            beamLevel[i] = target;
            beamTarget[i] = target;
            ledcWrite(beamCh[i], target);
            setOutLevel(outIndex, target);
            return;
        }
        beamTarget[i] = target;
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
        setOutLevel(beamOut[i], (uint8_t)out);
        stateChanged = true;
    }
}
