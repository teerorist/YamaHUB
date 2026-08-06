#include "blinkers.h"
#include "config.h"
#include "pins.h"
#include "arming.h"
#include "ble_hub.h"
#include "inputs.h"
#include "display_hub.h"
#include <math.h>

enum class RunMode {
    OFF, LEFT_N, LEFT_NS, RIGHT_N, RIGHT_NS, HAZARD
};

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

enum class Pending {
    NONE, OFF, LEFT_N, RIGHT_N, LEFT_NS, RIGHT_NS, HAZARD
};
static Pending pending = Pending::NONE;

static const int PWM_CH_LEFT  = 2;
static const int PWM_CH_RIGHT = 3;
static const unsigned long LONG_MS = 500;
static const unsigned long IGNORE_MS = 350;
static unsigned long ignoreInputUntil = 0;

static RunMode suspendedMode = RunMode::OFF;
static int suspendedTarget = 0;
static int suspendedDone = 0;
static bool suspended = false;

// OUT_1..OUT_10 → GPIO (pins.h)
static const uint8_t OUT_PINS[10] = {
    OUT_1, OUT_2, OUT_3, OUT_4, OUT_5,
    OUT_6, OUT_7, OUT_8, OUT_9, OUT_10
};

static int leftOutIdx  = 0;  // 0..9
static int rightOutIdx = 4;
static uint8_t leftGpio  = OUT_1;
static uint8_t rightGpio = OUT_5;
static bool pinsReady = false;

/** Indeks OUT (0..9) dla trybu LEFT/RIGHT z inputCfg. */
int blinkerLeftOutIndex() {
    for (int i = 0; i < INPUT_COUNT; i++)
        if (inputCfg[i].mode == IN_LEFT) return (int)inputCfg[i].outIndex;
    return 0;
}
int blinkerRightOutIndex() {
    for (int i = 0; i < INPUT_COUNT; i++)
        if (inputCfg[i].mode == IN_RIGHT) return (int)inputCfg[i].outIndex;
    return 4;
}

/** Przepnij PWM na aktualne OUT z konfiguracji (po SET_INCFG / starcie). */
void refreshBlinkerPins() {
    int nl = blinkerLeftOutIndex();
    int nr = blinkerRightOutIndex();
    if (nl < 0 || nl > 9) nl = 0;
    if (nr < 0 || nr > 9) nr = 4;

    if (pinsReady && nl == leftOutIdx && nr == rightOutIdx) return;

    // odłącz stare piny
    if (pinsReady) {
        ledcDetachPin(leftGpio);
        if (rightGpio != leftGpio) ledcDetachPin(rightGpio);
        pinMode(leftGpio, OUTPUT);
        digitalWrite(leftGpio, LOW);
        if (rightGpio != leftGpio) {
            pinMode(rightGpio, OUTPUT);
            digitalWrite(rightGpio, LOW);
        }
        setOutLevel(leftOutIdx, 0);
        setOutLevel(rightOutIdx, 0);
    }

    leftOutIdx  = nl;
    rightOutIdx = nr;
    leftGpio  = OUT_PINS[leftOutIdx];
    rightGpio = OUT_PINS[rightOutIdx];

    ledcAttachPin(leftGpio, PWM_CH_LEFT);
    ledcAttachPin(rightGpio, PWM_CH_RIGHT);
    pinsReady = true;

    Serial.printf("PWM kierunków: L=OUT_%02d(gpio%d) R=OUT_%02d(gpio%d)\n",
                  leftOutIdx + 1, leftGpio, rightOutIdx + 1, rightGpio);
}

void setLeft(int v) {
    v = constrain(v, 0, 255);
    if (!pinsReady) refreshBlinkerPins();
    ledcWrite(PWM_CH_LEFT, v);
    setOutLevel(leftOutIdx, (uint8_t)v);
}

void setRight(int v) {
    v = constrain(v, 0, 255);
    if (!pinsReady) refreshBlinkerPins();
    ledcWrite(PWM_CH_RIGHT, v);
    setOutLevel(rightOutIdx, (uint8_t)v);
}

void setupBlinkers() {
    ledcSetup(PWM_CH_LEFT, 5000, 8);
    ledcSetup(PWM_CH_RIGHT, 5000, 8);
    pinsReady = false;
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

    if (mode == MODE_OFF) {
        enter(RunMode::OFF);
    } else if (mode == MODE_LEFT) {
        enter(RunMode::LEFT_NS);
    } else if (mode == MODE_RIGHT) {
        enter(RunMode::RIGHT_NS);
    } else if (mode == MODE_HAZARD) {
        enter(RunMode::HAZARD);
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

void setCurrentSpeed(float kmh) {
    currentSpeed = kmh;
}

static void checkAutoCancel() {
    if (cfg.autoCancelSpeed <= 0) return;
    if (currentSpeed < (float)cfg.autoCancelSpeed) return;

    if (runMode == RunMode::LEFT_NS) {
        enter(RunMode::LEFT_N);
    } else if (runMode == RunMode::RIGHT_NS) {
        enter(RunMode::RIGHT_N);
    }
}

static float curveFactor(float t) {
    if (cfg.curve == 1) return t * t * (3.0f - 2.0f * t);
    if (cfg.curve == 2) return t * t;
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
            if (fadeValue >= 255) {
                fadeValue = 255;
                fadeDirection = -1;
            }
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
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].mode == IN_LEFT) return i;
    }
    return -1;
}

static int findRightBtn() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].mode == IN_RIGHT) return i;
    }
    return -1;
}

void handleBlinkerButtons(Button* buttons, bool& stateChanged) {
    if (millis() < ignoreInputUntil) return;
    if (suspended) return;

    int li = findLeftBtn();
    int ri = findRightBtn();

    static unsigned long leftDown = 0, rightDown = 0;
    static bool leftLong = false, rightLong = false;
    static bool leftWas = false, rightWas = false;

    bool leftNow  = (li >= 0) ? buttons[li].isPressed() : false;
    bool rightNow = (ri >= 0) ? buttons[ri].isPressed() : false;

    // oba naraz → hazard
    if (leftNow && rightNow) {
        if (!leftWas || !rightWas) {
            if (runMode == RunMode::HAZARD) {
                queuePend(Pending::OFF);
            } else {
                queuePend(Pending::HAZARD);
            }
            ignoreInputUntil = millis() + IGNORE_MS;
            stateChanged = true;
        }
        leftWas = leftNow;
        rightWas = rightNow;
        return;
    }

    // LEWY
    if (leftNow && !leftWas) {
        leftDown = millis();
        leftLong = false;
    }
    if (leftNow && !leftLong && leftDown > 0 && (millis() - leftDown) > LONG_MS) {
        leftLong = true;
        if (runMode == RunMode::OFF) {
            enter(RunMode::LEFT_NS);
        } else if (runMode == RunMode::LEFT_N || runMode == RunMode::LEFT_NS) {
            queuePend(Pending::OFF);
        } else if (runMode == RunMode::RIGHT_N || runMode == RunMode::RIGHT_NS) {
            queuePend(Pending::LEFT_NS);
        } else if (runMode == RunMode::HAZARD) {
            queuePend(Pending::LEFT_NS);
        }
        stateChanged = true;
    }
    if (!leftNow && leftWas) {
        unsigned long held = millis() - leftDown;
        if (!leftLong && held > 30) {
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
            stateChanged = true;
        }
        leftDown = 0;
        leftLong = false;
    }
    leftWas = leftNow;

    // PRAWY
    if (rightNow && !rightWas) {
        rightDown = millis();
        rightLong = false;
    }
    if (rightNow && !rightLong && rightDown > 0 && (millis() - rightDown) > LONG_MS) {
        rightLong = true;
        if (runMode == RunMode::OFF) {
            enter(RunMode::RIGHT_NS);
        } else if (runMode == RunMode::RIGHT_N || runMode == RunMode::RIGHT_NS) {
            queuePend(Pending::OFF);
        } else if (runMode == RunMode::LEFT_N || runMode == RunMode::LEFT_NS) {
            queuePend(Pending::RIGHT_NS);
        } else if (runMode == RunMode::HAZARD) {
            queuePend(Pending::RIGHT_NS);
        }
        stateChanged = true;
    }
    if (!rightNow && rightWas) {
        unsigned long held = millis() - rightDown;
        if (!rightLong && held > 30) {
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
            stateChanged = true;
        }
        rightDown = 0;
        rightLong = false;
    }
    rightWas = rightNow;
}