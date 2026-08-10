#include "starter.h"
#include "arming.h"
#include "blinkers.h"
#include "beams.h"
#include "config.h"
#include "pins.h"
#include "inputs.h"
#include "display_hub.h"
#include <esp_sleep.h>

static const unsigned long LONG_MS = 400;
static const unsigned long DEB = 30;
static const unsigned long COOLDOWN_MS = 200;
static const unsigned long MIN_SHORT_MS = 40;
static const unsigned long SHUTDOWN_DELAY_MS = 10000;

static bool starterActive = false;
static bool savedOn[10] = {false};       // digital Output
static uint8_t savedBeam[10] = {0};      // poziom beamów 0/255
static bool shutdownPending = false;
static unsigned long shutdownAt = 0;
static bool blePressed = false;
static Output* sdOutputs = nullptr;

void setBleStarterPressed(bool pressed) {
    blePressed = pressed;
}

static void allBeamsOff() {
    for (int i = 0; i < 10; i++) {
        if (isBeamOutput(i)) {
            requestBeamLevel(i, 0);
            setOutLevel(i, 0);
        }
    }
}

static void beginShutdown(Output* outputs, bool& stateChanged) {
    if (shutdownPending) return;

    sdOutputs = outputs;

    if (starterActive) {
        starterSet(false, outputs, stateChanged);
    }

    // tylko odliczanie – OUT zostają do końca 10s
    shutdownPending = true;
    shutdownAt = millis() + SHUTDOWN_DELAY_MS;
    Serial.println("KILL → odliczanie 10s");
}

void requestShutdown(Output* outputs) {
    bool sc = false;
    beginShutdown(outputs, sc);
}

void requestShutdownNow(Output* outputs) {
    if (shutdownPending) return;

    sdOutputs = outputs;

    bool sc = false;
    if (starterActive) {
        starterSet(false, outputs, sc);
    }

    shutdownPending = true;
    shutdownAt = millis();
    Serial.println("KILL NOW → off + HAZARD + sleep");
}

void starterSet(bool on, Output* outputs, bool& stateChanged) {
    const int soi = starterOutIndex();  // 0..9 z konfiguracji IN

    if (on) {
        if (shutdownPending) {
            shutdownPending = false;
            Serial.println("STARTER ON → anulowano shutdown");
        }
        if (!starterActive) {
            starterActive = true;
            suspendBlinkers();

            for (int i = 0; i < 10; i++) {
                if (i == soi) continue;

                if (isBeamOutput(i)) {
                    // zapamiętaj jasność beamu i zgaś
                    savedBeam[i] = (outLevel[i] > 20) ? 255 : 0;
                    savedOn[i] = false;
                    requestBeamLevel(i, 0);
                    setOutLevel(i, 0);
                } else {
                    savedOn[i] = outputs[i].isOn();
                    savedBeam[i] = 0;
                    outputs[i].off();
                    setOutLevel(i, 0);
                }
            }

            outputs[soi].on();
            setOutLevel(soi, 255);
            stateChanged = true;
            Serial.printf("STARTER ON (OUT_%d) – saved others (incl. beams)\n", soi + 1);
        }
    } else if (!on && starterActive) {
        starterActive = false;
        outputs[soi].off();
        setOutLevel(soi, 0);

        for (int i = 0; i < 10; i++) {
            if (i == soi) continue;

            if (isBeamOutput(i)) {
                requestBeamLevel(i, savedBeam[i]);
                if (!cfg.beamFade) setOutLevel(i, savedBeam[i]);
            } else {
                if (savedOn[i]) {
                    outputs[i].on();
                    setOutLevel(i, 255);
                } else {
                    outputs[i].off();
                    setOutLevel(i, 0);
                }
            }
        }

        resumeBlinkers();
        stateChanged = true;
        Serial.printf("STARTER OFF (OUT_%d) – restored (incl. beams)\n", soi + 1);
    }
}

void handleStarter(Button& btn, Output* outputs, bool& stateChanged) {
    static unsigned long downAt = 0;
    static bool longDone = false;
    static unsigned long ignoreUntil = 0;

    static bool raw = false, deb = false;
    static unsigned long ch = 0;

    bool nowPhys = btn.isPressed();
    if (nowPhys != raw) {
        raw = nowPhys;
        ch = millis();
    }
    if (millis() - ch >= DEB && deb != raw) {
        deb = raw;
    }

    bool active = deb || blePressed;

    static bool prev = false;
    bool pressedEdge  = (active && !prev);
    bool releasedEdge = (!active && prev);
    prev = active;

    if (millis() < ignoreUntil) return;

    if (pressedEdge) {
        downAt = millis();
        longDone = false;
    }

    if (active && !longDone && downAt > 0 && (millis() - downAt) > LONG_MS) {
        longDone = true;
        if (shutdownPending) {
            shutdownPending = false;
            Serial.println("LONG → anulowano shutdown");
        }
        if (tryArm()) {
            starterSet(true, outputs, stateChanged);
        }
    }

    if (releasedEdge) {
        unsigned long held = millis() - downAt;

        if (longDone) {
            if (starterActive) {
                starterSet(false, outputs, stateChanged);
            }
            ignoreUntil = millis() + COOLDOWN_MS;
            Serial.println("LONG done – cooldown");
        } else if (held >= MIN_SHORT_MS && held <= LONG_MS) {
            Serial.printf("SHORT %lums → KILL\n", held);
            beginShutdown(outputs, stateChanged);
        }

        downAt = 0;
        longDone = false;
        blePressed = false;
    }
}

void updateShutdown() {
    if (!shutdownPending) return;

    static bool outsOffDone = false;
    static bool hazardDone = false;
    static unsigned long hazardAt = 0;

    // 1) odliczanie 10 s (albo 0 przy KILL NOW)
    if (millis() < shutdownAt) {
        outsOffDone = false;
        hazardDone = false;
        return;
    }

    // 2) wszystkie OUT off (digital + beamy)
    if (!outsOffDone) {
        suspendBlinkers();
        if (sdOutputs) {
            for (int i = 0; i < 10; i++) {
                sdOutputs[i].off();
                setOutLevel(i, 0);
            }
        }
        allBeamsOff();
        outsOffDone = true;
        Serial.println("KILL → wszystkie OUT off (incl. beams)");
        return;
    }

    // 3) final HAZARD
    if (!hazardDone) {
        forceMode(MODE_OFF);
        forceMode(MODE_HAZARD);
        hazardDone = true;
        hazardAt = millis();
        Serial.println("KILL → final HAZARD");
        return;
    }

    unsigned long oneBlink =
        (unsigned long)((255 / 8) * (unsigned long)cfg.fadeSpeed * 2 + 200);
    if (millis() - hazardAt < oneBlink) return;

    forceMode(MODE_OFF);
    outsOffDone = false;
    hazardDone = false;
    shutdownPending = false;
    sdOutputs = nullptr;

    Serial.println("deep sleep 8s (po wake: BLE 2s)");
    delay(50);
    Serial.flush();

    esp_sleep_disable_wakeup_source(ESP_SLEEP_WAKEUP_ALL);
    esp_sleep_enable_timer_wakeup(8ULL * 1000000ULL);
    esp_deep_sleep_start();
}