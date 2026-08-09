#include "blinkers.h"
#include "config.h"
#include "pins.h"
#include "arming.h"
#include "ble_hub.h"
#include "inputs.h"
#include "display_hub.h"
#include "driver/gpio.h"
#include <math.h>

enum class RunMode { OFF, LEFT_N, LEFT_NS, RIGHT_N, RIGHT_NS, HAZARD };

static RunMode runMode = RunMode::OFF;
BlinkerMode currentMode = MODE_OFF;
int blinksRemaining = -1;
bool connectionBlink = false;
unsigned long connectionBlinkStart = 0;

static unsigned long lastFadeTime = 0;
static int fadeValue = 0;
static int fadeDirection = 1;
static int blinkTarget = 0;
static int blinkDone = 0;
static float currentSpeed = 0.0f;

enum class Pending { NONE, OFF, LEFT_N, RIGHT_N, LEFT_NS, RIGHT_NS, HAZARD };
static Pending pending = Pending::NONE;

static const int PWM_CH_LEFT  = 2;
static const int PWM_CH_RIGHT = 3;
static const unsigned long LONG_MS = 400;  // <400 short=N, >=400 long=NS
static const unsigned long IGNORE_MS = 350;
static unsigned long ignoreInputUntil = 0;

static RunMode suspendedMode = RunMode::OFF;
static int suspendedTarget = 0;
static int suspendedDone = 0;
static bool suspended = false;

static const uint8_t OUT_PINS[10] = {
    OUT_1, OUT_2, OUT_3, OUT_4, OUT_5,
    OUT_6, OUT_7, OUT_8, OUT_9, OUT_10
};

// -1 = nieprzypisany (brak PWM)
static int leftOutIdx  = -1;
static int rightOutIdx = -1;
static int leftGpio    = -1;
static int rightGpio   = -1;
static bool pinsReady  = false;

int blinkerLeftOutIndex() {
    for (int i = 0; i < INPUT_COUNT; i++)
        if (inputCfg[i].mode == IN_LEFT)
            return (int)inputCfg[i].outIndex;
    return -1;
}

int blinkerRightOutIndex() {
    for (int i = 0; i < INPUT_COUNT; i++)
        if (inputCfg[i].mode == IN_RIGHT)
            return (int)inputCfg[i].outIndex;
    return -1;
}

bool isBlinkerOut(int outIndex0) {
    if (outIndex0 < 0 || outIndex0 > 9) return false;
    int li = blinkerLeftOutIndex();
    int ri = blinkerRightOutIndex();
    return outIndex0 == li || outIndex0 == ri;
}

static void hardReleaseGpio(int gpio) {
    if (gpio < 0) return;
    ledcDetachPin((uint8_t)gpio);
    gpio_reset_pin((gpio_num_t)gpio);
    pinMode((uint8_t)gpio, OUTPUT);
    digitalWrite((uint8_t)gpio, LOW);
}

void refreshBlinkerPins() {
    int nl = blinkerLeftOutIndex();
    int nr = blinkerRightOutIndex();

    if (pinsReady && nl == leftOutIdx && nr == rightOutIdx) return;

    // Zawsze zeruj poziomy starych slotów na LCD
    if (leftOutIdx >= 0)  setOutLevel(leftOutIdx, 0);
    if (rightOutIdx >= 0) setOutLevel(rightOutIdx, 0);

    if (pinsReady) {
        hardReleaseGpio(leftGpio);
        if (rightGpio != leftGpio) hardReleaseGpio(rightGpio);
    }

    leftOutIdx  = nl;
    rightOutIdx = nr;
    leftGpio  = (nl >= 0 && nl <= 9) ? (int)OUT_PINS[nl] : -1;
    rightGpio = (nr >= 0 && nr <= 9) ? (int)OUT_PINS[nr] : -1;

    if (leftGpio >= 0)  ledcAttachPin((uint8_t)leftGpio, PWM_CH_LEFT);
    if (rightGpio >= 0) ledcAttachPin((uint8_t)rightGpio, PWM_CH_RIGHT);

    ledcWrite(PWM_CH_LEFT, 0);
    ledcWrite(PWM_CH_RIGHT, 0);
    pinsReady = true;

    Serial.printf("PWM kierunków: L=OUT_%02d(gpio%d) R=OUT_%02d(gpio%d)\n",
                  nl >= 0 ? nl + 1 : -1, leftGpio,
                  nr >= 0 ? nr + 1 : -1, rightGpio);
}

void setLeft(int v) {
    v = constrain(v, 0, 255);
    if (!pinsReady) refreshBlinkerPins();
    if (leftGpio >= 0) ledcWrite(PWM_CH_LEFT, v);
    if (leftOutIdx >= 0) setOutLevel(leftOutIdx, (uint8_t)v);
    // NIGDY nie ruszaj innych indeksów (np. OUT_01 / low beam)
}

void setRight(int v) {
    v = constrain(v, 0, 255);
    if (!pinsReady) refreshBlinkerPins();
    if (rightGpio >= 0) ledcWrite(PWM_CH_RIGHT, v);
    if (rightOutIdx >= 0) setOutLevel(rightOutIdx, (uint8_t)v);
}

void setupBlinkers() {
    ledcSetup(PWM_CH_LEFT, 5000, 8);
    ledcSetup(PWM_CH_RIGHT, 5000, 8);
    pinsReady = false;
    leftOutIdx = rightOutIdx = -1;
    leftGpio = rightGpio = -1;
    refreshBlinkerPins();
    setLeft(0);
    setRight(0);
    Serial.println("PWM kierunków: OK");
}

static int shortN() {
    int n = (int)cfg.blinkCount;
    return (n <= 0) ? 3 : n;
}

static void syncCurrentMode() {
    switch (runMode) {
        case RunMode::OFF:      currentMode = MODE_OFF; break;
        case RunMode::LEFT_N:
        case RunMode::LEFT_NS:  currentMode = MODE_LEFT; break;
        case RunMode::RIGHT_N:
        case RunMode::RIGHT_NS: currentMode = MODE_RIGHT; break;
        case RunMode::HAZARD:   currentMode = MODE_HAZARD; break;
    }
}

static void enter(RunMode m) {
    runMode = m;
    fadeValue = 0;
    fadeDirection = 1;
    lastFadeTime = millis();
    blinkDone = 0;

    if (m == RunMode::LEFT_N || m == RunMode::RIGHT_N) {
        blinkTarget = shortN();
        blinksRemaining = blinkTarget;
    } else if (m == RunMode::LEFT_NS || m == RunMode::RIGHT_NS || m == RunMode::HAZARD) {
        blinkTarget = 0;
        blinksRemaining = -1;
    } else {
        blinkTarget = 0;
        blinksRemaining = 0;
        setLeft(0);
        setRight(0);
    }
    syncCurrentMode();
    Serial.printf("ENTER %d target=%d\n", (int)m, blinkTarget);
}

static void queuePend(Pending p) {
    pending = p;
    Serial.printf("PEND %d\n", (int)p);
}

void forceMode(BlinkerMode mode) {
    pending = Pending::NONE;
    ignoreInputUntil = millis() + IGNORE_MS;
    if (mode == MODE_OFF)         enter(RunMode::OFF);
    else if (mode == MODE_LEFT)   enter(RunMode::LEFT_N);   // short / N – nie NS
    else if (mode == MODE_RIGHT)  enter(RunMode::RIGHT_N);
    else if (mode == MODE_HAZARD) enter(RunMode::HAZARD);
}

/*
 * Tabela (ustalona, gdy działało „jak trzeba”):
 *
 * stan \ akcja     L short      L long       R short      R long
 * OFF              LEFT N       LEFT NS      RIGHT N      RIGHT NS
 * LEFT N           → LEFT N     → OFF        → RIGHT N    → RIGHT NS
 * RIGHT N          → LEFT N     → LEFT NS    → RIGHT N    → OFF
 * LEFT NS          → OFF        → OFF        → RIGHT N    → RIGHT NS
 * RIGHT NS         → LEFT N     → LEFT NS    → OFF        → OFF
 * HAZARD           → OFF        → LEFT NS    → OFF        → RIGHT NS
 *
 * „→” = dokończ bieżący fade (pending), potem nowy stan.
 * N = cfg.blinkCount mrugnięć; NS = non-stop.
 */
void applyLeftShort() {
    ignoreInputUntil = millis() + IGNORE_MS;
    if (runMode == RunMode::OFF) {
        enter(RunMode::LEFT_N);
    } else if (runMode == RunMode::LEFT_N) {
        queuePend(Pending::LEFT_N);
    } else if (runMode == RunMode::LEFT_NS) {
        queuePend(Pending::OFF);
    } else if (runMode == RunMode::RIGHT_N || runMode == RunMode::RIGHT_NS) {
        queuePend(Pending::LEFT_N);
    } else if (runMode == RunMode::HAZARD) {
        queuePend(Pending::OFF);
    }
}


/** Promocja N → NS w trakcie tego samego wciśnięcia (po LONG_MS). */
static void promoteLeftToNS() {
    if (runMode == RunMode::LEFT_N) {
        runMode = RunMode::LEFT_NS;
        blinkTarget = -1;
        blinksRemaining = -1;
        syncCurrentMode();
        Serial.println("PROMOTE LEFT N → NS");
    } else if (runMode == RunMode::OFF) {
        enter(RunMode::LEFT_NS);
    }
}

static void promoteRightToNS() {
    if (runMode == RunMode::RIGHT_N) {
        runMode = RunMode::RIGHT_NS;
        blinkTarget = -1;
        blinksRemaining = -1;
        syncCurrentMode();
        Serial.println("PROMOTE RIGHT N → NS");
    } else if (runMode == RunMode::OFF) {
        enter(RunMode::RIGHT_NS);
    }
}

void applyLeftLong() {
    ignoreInputUntil = millis() + IGNORE_MS;
    if (runMode == RunMode::OFF) {
        enter(RunMode::LEFT_NS);
    } else if (runMode == RunMode::LEFT_N || runMode == RunMode::LEFT_NS) {
        queuePend(Pending::OFF);
    } else if (runMode == RunMode::RIGHT_N || runMode == RunMode::RIGHT_NS) {
        queuePend(Pending::LEFT_NS);
    } else if (runMode == RunMode::HAZARD) {
        queuePend(Pending::LEFT_NS);
    }
}

void applyRightShort() {
    ignoreInputUntil = millis() + IGNORE_MS;
    if (runMode == RunMode::OFF) {
        enter(RunMode::RIGHT_N);
    } else if (runMode == RunMode::RIGHT_N) {
        queuePend(Pending::RIGHT_N);
    } else if (runMode == RunMode::RIGHT_NS) {
        queuePend(Pending::OFF);
    } else if (runMode == RunMode::LEFT_N || runMode == RunMode::LEFT_NS) {
        queuePend(Pending::RIGHT_N);
    } else if (runMode == RunMode::HAZARD) {
        queuePend(Pending::OFF);
    }
}

void applyRightLong() {
    ignoreInputUntil = millis() + IGNORE_MS;
    if (runMode == RunMode::OFF) {
        enter(RunMode::RIGHT_NS);
    } else if (runMode == RunMode::RIGHT_N || runMode == RunMode::RIGHT_NS) {
        queuePend(Pending::OFF);
    } else if (runMode == RunMode::LEFT_N || runMode == RunMode::LEFT_NS) {
        queuePend(Pending::RIGHT_NS);
    } else if (runMode == RunMode::HAZARD) {
        queuePend(Pending::RIGHT_NS);
    }
}

void applyLeftHoldLong() {
    ignoreInputUntil = millis() + IGNORE_MS;
    if (runMode == RunMode::OFF || runMode == RunMode::LEFT_N) {
        promoteLeftToNS();
    } else {
        applyLeftLong();
    }
}

void applyRightHoldLong() {
    ignoreInputUntil = millis() + IGNORE_MS;
    if (runMode == RunMode::OFF || runMode == RunMode::RIGHT_N) {
        promoteRightToNS();
    } else {
        applyRightLong();
    }
}

void suspendBlinkers() {
    if (suspended) return;
    suspended = true;
    suspendedMode = runMode;
    suspendedTarget = blinkTarget;
    suspendedDone = blinkDone;
    setLeft(0);
    setRight(0);
    runMode = RunMode::OFF;
    syncCurrentMode();
    Serial.println("Blinkers SUSPEND");
}

void resumeBlinkers() {
    if (!suspended) return;
    suspended = false;
    if (suspendedMode != RunMode::OFF) {
        enter(suspendedMode);
        blinkTarget = suspendedTarget;
        blinkDone = suspendedDone;
    }
    Serial.println("Blinkers RESUME");
}

void requestBlinkersGracefulOff() {
    if (runMode == RunMode::OFF && pending == Pending::NONE) {
        setLeft(0);
        setRight(0);
        return;
    }
    queuePend(Pending::OFF);
}

bool blinkersAreOff() {
    return runMode == RunMode::OFF && pending == Pending::NONE && fadeValue == 0;
}

void setCurrentSpeed(float kmh) { currentSpeed = kmh; }

static void checkAutoCancel() {
    if (cfg.autoCancelSpeed == 0) return;
    if (runMode != RunMode::LEFT_NS && runMode != RunMode::RIGHT_NS) return;
    if (currentSpeed >= (float)cfg.autoCancelSpeed) {
        // NS → N (dokończ serię short)
        if (runMode == RunMode::LEFT_NS) {
            runMode = RunMode::LEFT_N;
            blinkTarget = shortN();
            blinkDone = 0;
            blinksRemaining = blinkTarget;
        } else {
            runMode = RunMode::RIGHT_N;
            blinkTarget = shortN();
            blinkDone = 0;
            blinksRemaining = blinkTarget;
        }
        syncCurrentMode();
        Serial.println("AutoCancel NS→N");
    }
}

static float curveFactor(float t) {
    if (cfg.curve == 1) return t * t * (3.0f - 2.0f * t);
    if (cfg.curve == 2) return (t < 0.5f) ? 0.0f : 1.0f;
    return t;
}

void updateBlinkers(bool& stateChanged) {
    if (suspended) return;
    checkAutoCancel();

    if (pending != Pending::NONE && fadeValue == 0 && fadeDirection == 1) {
        Pending p = pending;
        pending = Pending::NONE;
        switch (p) {
            case Pending::OFF:      enter(RunMode::OFF); break;
            case Pending::LEFT_N:   enter(RunMode::LEFT_N); break;
            case Pending::RIGHT_N:  enter(RunMode::RIGHT_N); break;
            case Pending::LEFT_NS:  enter(RunMode::LEFT_NS); break;
            case Pending::RIGHT_NS: enter(RunMode::RIGHT_NS); break;
            case Pending::HAZARD:   enter(RunMode::HAZARD); break;
            default: break;
        }
        stateChanged = true;
    }

    if (runMode == RunMode::OFF) {
        setLeft(0);
        setRight(0);
        return;
    }

    unsigned long now = millis();
    unsigned long step = (unsigned long)cfg.fadeSpeed;
    if (step < 4) step = 4;

    if (now - lastFadeTime >= step) {
        lastFadeTime = now;
        int delta = 8;
        if (fadeDirection > 0) {
            fadeValue += delta;
            if (fadeValue >= 255) { fadeValue = 255; fadeDirection = -1; }
        } else {
            fadeValue -= delta;
            if (fadeValue <= 0) {
                fadeValue = 0;
                fadeDirection = 1;
                blinkDone++;
                Serial.printf("BLINK %d/%d\n", blinkDone, blinkTarget);
                if (blinkTarget > 0 && blinkDone >= blinkTarget) {
                    enter(RunMode::OFF);
                    stateChanged = true;
                    return;
                }
            }
        }
    }

    float t = fadeValue / 255.0f;
    int out = (int)(curveFactor(t) * 255.0f);
    if (out < 0) out = 0;
    if (out > 255) out = 255;

    if (runMode == RunMode::LEFT_N || runMode == RunMode::LEFT_NS) {
        setLeft(out); setRight(0);
    } else if (runMode == RunMode::RIGHT_N || runMode == RunMode::RIGHT_NS) {
        setRight(out); setLeft(0);
    } else if (runMode == RunMode::HAZARD) {
        setLeft(out); setRight(out);
    }

    if (connectionBlink && millis() - connectionBlinkStart > 800) {
        connectionBlink = false;
        if (runMode == RunMode::HAZARD) queuePend(Pending::OFF);
    }
}

static int findLeftBtn() {
    for (int i = 0; i < INPUT_COUNT; i++)
        if (inputCfg[i].mode == IN_LEFT) return i;
    return -1;
}
static int findRightBtn() {
    for (int i = 0; i < INPUT_COUNT; i++)
        if (inputCfg[i].mode == IN_RIGHT) return i;
    return -1;
}

void handleBlinkerButtons(Button* buttons, bool& stateChanged) {
    if (millis() < ignoreInputUntil) return;
    if (!tryArm()) return;

    int li = findLeftBtn();
    int ri = findRightBtn();
    if (li < 0 && ri < 0) return;

    static bool leftWas = false, rightWas = false;
    static unsigned long leftDown = 0, rightDown = 0;
    static bool leftLong = false, rightLong = false;
    // gest z OFF: press = od razu N; po LONG_MS = promocja NS; release < LONG_MS = zostaje N
    static bool leftProvisional = false, rightProvisional = false;

    bool leftNow  = (li >= 0) && buttons[li].isPressed();
    bool rightNow = (ri >= 0) && buttons[ri].isPressed();

    // oba naraz long → HAZARD
    if (leftNow && rightNow) {
        unsigned long ld = leftDown ? (millis() - leftDown) : 0;
        unsigned long rd = rightDown ? (millis() - rightDown) : 0;
        if (!leftLong && !rightLong && leftDown && rightDown && ld > LONG_MS && rd > LONG_MS) {
            leftLong = rightLong = true;
            leftProvisional = rightProvisional = false;
            if (runMode == RunMode::HAZARD) queuePend(Pending::OFF);
            else enter(RunMode::HAZARD);
            stateChanged = true;
        }
        leftWas = leftNow;
        rightWas = rightNow;
        return;
    }

    // ----- LEWY -----
    if (leftNow && !leftWas) {
        leftDown = millis();
        leftLong = false;
        leftProvisional = false;
        if (runMode == RunMode::OFF) {
            enter(RunMode::LEFT_N);          // od razu włącz
            leftProvisional = true;
            stateChanged = true;
            Serial.println("LEFT press → N");
        }
    }
    if (leftNow && !leftLong && leftDown > 0 && (millis() - leftDown) > LONG_MS) {
        leftLong = true;
        if (leftProvisional) {
            promoteLeftToNS();               // N → NS w tym samym geście
            stateChanged = true;
        } else {
            applyLeftLong();                 // tabela: aktywny + long → off / switch
            stateChanged = true;
        }
    }
    if (!leftNow && leftWas) {
        unsigned long held = millis() - leftDown;
        if (leftProvisional) {
            // short: LEFT_N już jedzie z target=N; long: już NS
            leftProvisional = false;
            Serial.println(leftLong ? "LEFT release → NS" : "LEFT release → N");
            stateChanged = true;
        } else if (!leftLong && held > 30) {
            applyLeftShort();
            stateChanged = true;
        }
        leftDown = 0;
        leftLong = false;
    }
    leftWas = leftNow;

    // ----- PRAWY -----
    if (rightNow && !rightWas) {
        rightDown = millis();
        rightLong = false;
        rightProvisional = false;
        if (runMode == RunMode::OFF) {
            enter(RunMode::RIGHT_N);
            rightProvisional = true;
            stateChanged = true;
            Serial.println("RIGHT press → N");
        }
    }
    if (rightNow && !rightLong && rightDown > 0 && (millis() - rightDown) > LONG_MS) {
        rightLong = true;
        if (rightProvisional) {
            promoteRightToNS();
            stateChanged = true;
        } else {
            applyRightLong();
            stateChanged = true;
        }
    }
    if (!rightNow && rightWas) {
        unsigned long held = millis() - rightDown;
        if (rightProvisional) {
            rightProvisional = false;
            Serial.println(rightLong ? "RIGHT release → NS" : "RIGHT release → N");
            stateChanged = true;
        } else if (!rightLong && held > 30) {
            applyRightShort();
            stateChanged = true;
        }
        rightDown = 0;
        rightLong = false;
    }
    rightWas = rightNow;
}
