#include "startup_anim.h"
#include "display_hub.h"
#include "ble_protocol.h"
#include "ble_hub.h"
#include "input_cfg.h"
#include "blinkers.h"
#include "beams.h"
#include "config.h"

bool isAnimationRunning = false;
static unsigned long animStart = 0;
static const unsigned long ANIM_UP_MS = 2500;
static const unsigned long ANIM_DOWN_MS = 2500;
static const unsigned long ANIM_TOTAL_MS = ANIM_UP_MS + ANIM_DOWN_MS;

static bool hazardFired = false;
static unsigned long hazardAt = 0;

static bool prevN = false, prevOil = false, prevFuel = false;
static bool prevLow = false, prevHi = false;
static int lastLogRpm = -1;

static unsigned long oneBlinkMs() {
    return (unsigned long)((255 / 8) * (unsigned long)cfg.fadeSpeed * 2 + 200);
}

static void logIn(const char* name, int inIndex, bool on) {
    Serial.printf("ANIM IN:(%s):%d  [IN_%02d]\n", name, on ? 1 : 0, inIndex + 1);
}

static void logOut(const char* name, int outIndex, bool on) {
    Serial.printf("ANIM OUT:(%s):%s  [OUT_%02d]\n",
                  name, on ? "FADE in" : "FADE out", outIndex + 1);
}

static void animEnd() {
    isAnimationRunning = false;
    currentRpm = 0;
    canFuelPct = 100;
    clearAllAnimInputOverrides();
    int low = lowBeamOutIndex();
    int hi = hiBeamOutIndex();
    if (low >= 0) requestBeamLevel(low, 0);
    if (hi >= 0) requestBeamLevel(hi, 0);
    forceMode(MODE_OFF);
    if (gOutputs) sendState(gOutputs);
    sendRpm(0);
    sendFuel(100);
    sendOil(0);
    Serial.println("ANIM: End");
}

void startStartupAnimation() {
    isAnimationRunning = true;
    animStart = millis();
    hazardFired = false;
    hazardAt = 0;
    prevN = prevOil = prevFuel = prevLow = prevHi = false;
    lastLogRpm = -1;
    currentRpm = 0;
    canFuelPct = 100;
    clearAllAnimInputOverrides();
    int nIn = findNeutralInIndex();
    int oilIn = findOilInIndex();
    int low = lowBeamOutIndex();
    int hi = hiBeamOutIndex();
    if (low >= 0) requestBeamLevel(low, 0);
    if (hi >= 0) requestBeamLevel(hi, 0);
    forceMode(MODE_OFF);
    Serial.println("ANIM: Start (IN/OUT)");
    Serial.printf("ANIM map  NEUTRAL=IN_%d  OIL=IN_%d  LOW=OUT_%d  HI=OUT_%d\n",
                  nIn >= 0 ? nIn + 1 : -1,
                  oilIn >= 0 ? oilIn + 1 : -1,
                  low >= 0 ? low + 1 : -1,
                  hi >= 0 ? hi + 1 : -1);
}

void updateStartupAnimation() {
    if (!isAnimationRunning) return;

    unsigned long now = millis();
    unsigned long elapsed = now - animStart;

    if (elapsed >= ANIM_TOTAL_MS) {
        animEnd();
        return;
    }

    const bool goingUp = elapsed < ANIM_UP_MS;
    float t;
    if (goingUp) t = (float)elapsed / (float)ANIM_UP_MS;
    else t = 1.0f - (float)(elapsed - ANIM_UP_MS) / (float)ANIM_DOWN_MS;
    if (t < 0) t = 0;
    if (t > 1) t = 1;
    currentRpm = (int)(t * 12000.0f);

    const int nIn = findNeutralInIndex();
    const int oilIn = findOilInIndex();
    const int lowOut = lowBeamOutIndex();
    const int hiOut = hiBeamOutIndex();

    bool nOn = false, oilOn = false, fuelOn = false, lowOn = false, hiOn = false;

    if (goingUp) {
        nOn = currentRpm >= 1000;
        lowOn = currentRpm >= 3000;
        oilOn = currentRpm >= 6000;
        hiOn = currentRpm >= 9000;
        fuelOn = currentRpm >= 11000;
    } else {
        fuelOn = currentRpm >= 11000;
        hiOn = currentRpm >= 10000;
        oilOn = currentRpm >= 6000;
        lowOn = currentRpm >= 4500;
        nOn = currentRpm >= 1000;
    }

    if (nIn >= 0) {
        if (goingUp || currentRpm >= 1000) {
            setAnimInputOverride(nIn, nOn);
            if (nOn != prevN) logIn("NEUTRAL", nIn, nOn);
            prevN = nOn;
        } else {
            clearAnimInputOverride(nIn);
            if (prevN) {
                Serial.printf("ANIM IN:(NEUTRAL):effective  [IN_%02d]\n", nIn + 1);
                prevN = false;
            }
        }
    }
    if (oilOn != prevOil) {
        if (oilIn >= 0) {
            setAnimInputOverride(oilIn, oilOn);
            logIn("OIL", oilIn, oilOn);
            const InputCfgItem& oil = inputCfg[oilIn];
            if (oil.outputEnabled && outAssigned(oil.outPrimary) && gOutputs) {
                if (oilOn) gOutputs[oil.outPrimary].on();
                else       gOutputs[oil.outPrimary].off();
                setOutLevel(oil.outPrimary, oilOn ? 255 : 0);
                Serial.printf("ANIM OUT:(OIL):%d  [OUT_%02d]\n",
                              oilOn ? 1 : 0, oil.outPrimary + 1);
            }
        } else {
            Serial.println("ANIM IN:(OIL): no slot — OIL: BLE");
        }
        sendOil(oilOn ? 1 : 0);
        prevOil = oilOn;
    } else if (oilIn >= 0) {
        setAnimInputOverride(oilIn, oilOn);
    }
    if (lowOut >= 0) {
        requestBeamLevel(lowOut, lowOn ? 255 : 0);
        if (lowOn != prevLow) logOut("LOW", lowOut, lowOn);
        prevLow = lowOn;
    }
    if (hiOut >= 0) {
        requestBeamLevel(hiOut, hiOn ? 255 : 0);
        if (hiOn != prevHi) logOut("HI", hiOut, hiOn);
        prevHi = hiOn;
    }

    canFuelPct = fuelOn ? 10 : 100;
    if (fuelOn != prevFuel) {
        Serial.printf("ANIM LowFuel:%d\n", fuelOn ? 1 : 0);
        prevFuel = fuelOn;
    }

    if (elapsed >= ANIM_UP_MS && !hazardFired) {
        hazardFired = true;
        hazardAt = now;
        forceMode(MODE_HAZARD);
        Serial.println("ANIM HAZARD:1");
    }
    if (hazardFired && hazardAt && (now - hazardAt >= oneBlinkMs())) {
        forceMode(MODE_OFF);
        hazardAt = 0;
        Serial.println("ANIM HAZARD:0");
    }

    if (currentRpm / 1000 != lastLogRpm / 1000) {
        lastLogRpm = currentRpm;
        Serial.printf("ANIM RPM:%d %s\n", currentRpm, goingUp ? "up" : "down");
    }

    static unsigned long lastBleSync = 0;
    if (now - lastBleSync >= 50) {
        sendRpm(currentRpm);
        sendFuel(canFuelPct);
        lastBleSync = now;
    }
}
