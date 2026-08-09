#include <Arduino.h>
#include <esp_sleep.h>
#include "pins.h"
#include "Button.h"
#include "Output.h"
#include "config.h"
#include "blinkers.h"
#include "ble_hub.h"
#include "starter.h"
#include "arming.h"
#include "inputs.h"
#include "display_hub.h"
#include "beams.h"

Button buttons[10] = {
    Button(IN_1), Button(IN_2), Button(IN_3), Button(IN_4), Button(IN_5),
    Button(IN_6), Button(IN_7), Button(IN_8), Button(IN_9), Button(IN_10)
};

Output outputs[10] = {
    Output(OUT_1), Output(OUT_2), Output(OUT_3), Output(OUT_4), Output(OUT_5),
    Output(OUT_6), Output(OUT_7), Output(OUT_8), Output(OUT_9), Output(OUT_10)
};

static bool bootCheckDone = false;
static unsigned long bootCheckAt = 0;

void setup() {
    Serial.begin(115200);
    delay(400);

    loadConfig();
    loadInputModes();

    Serial.println("=== YamaHub v1.6 clean outs ===");
    Serial.printf("Config: fade=%d N=%d curve=%d acSpeed=%d\n",
                  cfg.fadeSpeed, cfg.blinkCount, cfg.curve, cfg.autoCancelSpeed);

    for (int i = 0; i < 10; i++) {
        buttons[i].begin();
        outputs[i].begin();
    }

    // najpierw kierunki (PWM), potem beams (nie wolno zająć pinów L/R)
    setupBlinkers();
    setupBeams();
    setupDisplay();

    // log mapowania
    Serial.printf("Map: LEFT→OUT_%d RIGHT→OUT_%d\n",
                  blinkerLeftOutIndex() + 1, blinkerRightOutIndex() + 1);

    esp_sleep_wakeup_cause_t cause = esp_sleep_get_wakeup_cause();
    if (cause == ESP_SLEEP_WAKEUP_TIMER) {
        Serial.println("Wake: TIMER → okno BLE 2s");
        setupBLE(outputs);
        unsigned long t0 = millis();
        while (millis() - t0 < 2000) {
            processBle();
            if (isBleConnected()) {
                Serial.println("Apka w oknie → normalna praca");
                bootCheckDone = true;
                Serial.println("Gotowy");
                return;
            }
            delay(20);
        }
        Serial.println("Brak apki → sleep 8s");
        delay(30);
        Serial.flush();
        esp_sleep_disable_wakeup_source(ESP_SLEEP_WAKEUP_ALL);
        esp_sleep_enable_timer_wakeup(8ULL * 1000000ULL);
        esp_deep_sleep_start();
        return;
    }

    Serial.println("Wake: power-on / reset");
    setupBLE(outputs);
    Serial.println("Gotowy");
    bootCheckDone = false;
    bootCheckAt = millis() + 60000UL;
}

void loop() {
    processBle();

    if (!bootCheckDone && millis() >= bootCheckAt) {
        bootCheckDone = true;
        if (hubArmed) {
            Serial.println("Check: HUB ARMED – OK");
        } else {
            Serial.println("Check: brak ARMED → sleep 8/2");
            delay(50);
            Serial.flush();
            esp_sleep_disable_wakeup_source(ESP_SLEEP_WAKEUP_ALL);
            esp_sleep_enable_timer_wakeup(8ULL * 1000000ULL);
            esp_deep_sleep_start();
        }
    }

    updateShutdown();

    bool stateChanged = false;
    handleBlinkerButtons(buttons, stateChanged);
    handleConfigurableInputs(buttons, outputs, stateChanged);

    int si = findStarterInIndex();
    if (si >= 0 && si < 10)
        handleStarter(buttons[si], outputs, stateChanged);

    updateBeams(stateChanged);
    updateBlinkers(stateChanged);
    if (stateChanged) sendState(outputs);

    static unsigned long lastDraw = 0;
    if (millis() - lastDraw >= 40) {
        lastDraw = millis();
        for (int i = 0; i < 10; i++) {
            if (isBlinkerOut(i)) continue;          // poziom z PWM
            if (isBeamOutput(i)) continue;           // poziom z beams
            setOutLevel(i, outputs[i].isOn() ? 255 : 0);
        }
        drawOutputs();
    }

    delay(2);
}
