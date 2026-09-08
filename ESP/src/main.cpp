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
#include "startup_anim.h"
// #include "can_bus.h"
#include "engine_sim.h"

Button buttons[10] = {
    Button(IN_1), Button(IN_2), Button(IN_3), Button(IN_4), Button(IN_5),
    Button(IN_6), Button(IN_7), Button(IN_8), Button(IN_9), Button(IN_10)
};

Output outputs[10] = {
    Output(OUT_1), Output(OUT_2), Output(OUT_3), Output(OUT_4), Output(OUT_5),
    Output(OUT_6), Output(OUT_7), Output(OUT_8), Output(OUT_9), Output(OUT_10)
};

void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.println();
    Serial.println("=== YamaHub ESP32-S3-DevKitC-1 N16R8 ===");

    loadConfig();
    loadInputModes();

    for (int i = 0; i < 10; i++) {
        // buttons[i].begin() woła pinMode. Robimy to tylko dla rzeczywistych pinów.
        // Jeśli pin to 255, Button i tak będzie zwracać LOW (nieaktywny).
        if (pinValid(buttons[i].getPin())) buttons[i].begin();
        if (pinValid(outputs[i].getPin())) outputs[i].begin();
    }

    setupBlinkers();
    Serial.println("Blinkers OK");
    setupBeams();
    Serial.println("Beams OK");
#ifdef YAMAHUB_SKIP_LCD
    Serial.println("LCD skipped (DevKit, no panel)");
#else
    setupDisplay();
    Serial.println("Display OK");
#endif
    // setupCAN(); // wyłączone — obiekt MCP2515 nie startuje przy boot

    setupBLE(outputs, buttons);
    Serial.println("System Ready");

    if (esp_sleep_get_wakeup_cause() == ESP_SLEEP_WAKEUP_TIMER) {
        Serial.println("Wake from timer: BLE window 1s");
    }
}

void loop() {
    bool stateChanged = false;
    processBle();

    static bool wasArmed = false;
    static unsigned long armAnimAt = 0;
    if (hubArmed && !wasArmed) {
        armAnimAt = millis() + 800;
        Serial.println("ANIM: ARM -> start in 800ms");
    }
    if (!hubArmed && wasArmed) {
        armAnimAt = 0;
        Serial.println("ANIM: DISARM");
        startStartupAnimation();
    }
    wasArmed = hubArmed;
    if (armAnimAt && hubArmed && millis() >= armAnimAt) {
        armAnimAt = 0;
        startStartupAnimation();
    }

    static bool bleWindowActive = false;
    static unsigned long bleWindowEnd = 0;
    static bool wakeWindowInited = false;
    if (!wakeWindowInited) {
        wakeWindowInited = true;
        if (esp_sleep_get_wakeup_cause() == ESP_SLEEP_WAKEUP_TIMER) {
            bleWindowActive = true;
            bleWindowEnd = millis() + 1000;
        }
    }
    if (hubArmed) bleWindowActive = false;
    if (bleWindowActive && !hubArmed && !isAnimationRunning && millis() >= bleWindowEnd) {
        Serial.println("No ARM in 1s -> sleep 5s");
        delay(50);
        Serial.flush();
        esp_sleep_disable_wakeup_source(ESP_SLEEP_WAKEUP_ALL);
        esp_sleep_enable_timer_wakeup(5ULL * 1000000ULL);
        esp_deep_sleep_start();
    }

    if (isAnimationRunning) {
        updateStartupAnimation();
        updateBeams(stateChanged);
        updateBlinkers(stateChanged);
        updateAllEffectiveInputStates(buttons);
        sendInputStatesIfChanged(buttons);
        static unsigned long lastAnimSync = 0;
        if (millis() - lastAnimSync >= 50) {
            lastAnimSync = millis();
            stateChanged = true;
        }
    } else {
        // updateCAN(); // Wyłączone

        // Powrót do samej symulacji RPM
        updateEngineSim(currentSpeedKmh());

        updateBeams(stateChanged);
        updateShutdown();

        updateAllEffectiveInputStates(buttons);
        handleBlinkerButtons(buttons, stateChanged);
        handleConfigurableInputs(buttons, outputs, stateChanged);
        updateStarterInterlock(outputs, stateChanged);
        handleKillSwitch(buttons, outputs, stateChanged);
        sendInputStatesIfChanged(buttons);

        int si = findStarterInIndex();
        if (si >= 0 && si < 10) handleStarter(buttons[si], outputs, stateChanged);

        updateBlinkers(stateChanged);
    }

    syncStarterKillOutput(outputs, stateChanged);

    if (stateChanged) sendState(outputs);

    static unsigned long lastRpmSend = 0;
    if (millis() - lastRpmSend >= 100 || (isAnimationRunning && millis() - lastRpmSend >= 50)) {
        lastRpmSend = millis();
        // sendRpm(currentRpm); // Wyłączone wysyłanie RPM do aplikacji
        // if (!isAnimationRunning) sendFuel(canFuelPct); // Wyłączone
    }

#ifndef YAMAHUB_SKIP_LCD
    static unsigned long lastDraw = 0;
    if (millis() - lastDraw >= 40) {
        lastDraw = millis();
        int li = blinkerLeftOutIndex(), ri = blinkerRightOutIndex();
        for (int i = 0; i < 10; i++) {
            if (i == li || i == ri || isBeamOutput(i)) continue;
            setOutLevel(i, outputs[i].isOn() ? 255 : 0);
        }
        drawOutputs();
    }
#endif
    delay(2);
}
