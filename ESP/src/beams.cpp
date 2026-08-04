#include "beams.h"
#include "inputs.h"
#include "config.h"
#include "display_hub.h"
#include "pins.h"
#include <string.h>
#include <math.h>

static const int PWM_CH_BASE = 4;   // 4,5,6… (2/3 = kierunki, 7 = BL)
static const int MAX_BEAMS = 4;

static int beamOut[MAX_BEAMS];      // index OUT 0..8
static int beamCh[MAX_BEAMS];
static int beamLevel[MAX_BEAMS];
static int beamTarget[MAX_BEAMS];
static int beamDir[MAX_BEAMS];
static unsigned long beamLast[MAX_BEAMS];
static int beamCount = 0;

static const int OUT_PINS[10] = {
    OUT_1, OUT_2, OUT_3, OUT_4, OUT_5,
    OUT_6, OUT_7, OUT_8, OUT_9, OUT_10
};

static bool nameIsBeam(const char* n) {
    if (!n) return false;
    if (strstr(n, "hi") || strstr(n, "Hi") || strstr(n, "HI") ||
        strstr(n, "high") || strstr(n, "High")) return true;
    if (strstr(n, "low") || strstr(n, "Low") || strstr(n, "mij")) return true;
    return false;
}

bool isBeamOutput(int outIndex) {
    for (int i = 0; i < beamCount; i++)
        if (beamOut[i] == outIndex) return true;
    return false;
}

static float curveFactor(float t) {
    if (cfg.curve == 1) return t * t * (3.0f - 2.0f * t);
    if (cfg.curve == 2) return t * t;
    return t;
}

void setupBeams() {
    beamCount = 0;
    for (int i = 0; i < 9 && beamCount < MAX_BEAMS; i++) {
        if (!nameIsBeam(inputCfg[i].name)) continue;
        int oi = inputCfg[i].outIndex;
        if (oi < 0 || oi > 8) continue;
        if (oi == 0 || oi == 4) continue; // OUT_1/5 = kierunki

        // unikaj duplikatów
        bool exists = false;
        for (int j = 0; j < beamCount; j++)
            if (beamOut[j] == oi) exists = true;
        if (exists) continue;

        int ch = PWM_CH_BASE + beamCount;
        ledcSetup(ch, 5000, 8);
        ledcAttachPin(OUT_PINS[oi], ch);
        ledcWrite(ch, 0);

        beamOut[beamCount] = oi;
        beamCh[beamCount] = ch;
        beamLevel[beamCount] = 0;
        beamTarget[beamCount] = 0;
        beamDir[beamCount] = 0;
        beamLast[beamCount] = millis();
        beamCount++;
        Serial.printf("Beam: OUT_%d ch=%d name=%s\n", oi + 1, ch, inputCfg[i].name);
    }
}

void requestBeamLevel(int outIndex, uint8_t target) {
    for (int i = 0; i < beamCount; i++) {
        if (beamOut[i] != outIndex) continue;

        if (!cfg.beamFade) {
            // bez fade – od razu
            beamLevel[i] = target ? 255 : 0;
            beamTarget[i] = beamLevel[i];
            beamDir[i] = 0;
            ledcWrite(beamCh[i], beamLevel[i]);
            setOutLevel(beamOut[i], (uint8_t)beamLevel[i]);
            return;
        }

        beamTarget[i] = target ? 255 : 0;
        beamDir[i] = (beamTarget[i] > beamLevel[i]) ? 1 : -1;
        return;
    }
}

void updateBeams(bool& stateChanged) {
    unsigned long step = cfg.fadeSpeed;
    if (step < 4) step = 4;
    const int delta = 8;

    for (int i = 0; i < beamCount; i++) {
        if (beamDir[i] == 0) continue;
        if (millis() - beamLast[i] < step) continue;
        beamLast[i] = millis();

        beamLevel[i] += beamDir[i] * delta;

        if (beamDir[i] > 0 && beamLevel[i] >= beamTarget[i]) {
            beamLevel[i] = beamTarget[i];
            beamDir[i] = 0;
            stateChanged = true;
        } else if (beamDir[i] < 0 && beamLevel[i] <= beamTarget[i]) {
            beamLevel[i] = beamTarget[i];
            beamDir[i] = 0;
            stateChanged = true;
        }

        float t = beamLevel[i] / 255.0f;
        int out = (int)(curveFactor(t) * 255.0f);
        if (out < 0) out = 0;
        if (out > 255) out = 255;

        ledcWrite(beamCh[i], out);
        setOutLevel(beamOut[i], (uint8_t)out);
    }
}