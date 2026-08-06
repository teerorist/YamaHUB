#include "ble_hub.h"
#include "config.h"
#include "blinkers.h"
#include "display_hub.h"
#include "starter.h"
#include "arming.h"
#include "inputs.h"
#include <NimBLEDevice.h>
#include <cstdio>
#include <cstring>

bool deviceConnected = false;

bool isBleConnected() { return deviceConnected; }

static NimBLEServer* pServer = nullptr;
static NimBLECharacteristic* pCharacteristic = nullptr;
static Output* gOutputs = nullptr;

#define SERVICE_UUID        "FFE0"
#define CHARACTERISTIC_UUID "FFE1"

// ---------- kolejka komend z callbacka (żeby nie blokować nimble host) ----------
static const int CMD_Q = 8;
static char cmdQueue[CMD_Q][96];
static volatile int cmdHead = 0;
static volatile int cmdTail = 0;

static void enqueueCmd(const std::string& value) {
    int next = (cmdHead + 1) % CMD_Q;
    if (next == cmdTail) return; // pełna – drop
    strncpy(cmdQueue[cmdHead], value.c_str(), 95);
    cmdQueue[cmdHead][95] = '\0';
    cmdHead = next;
}

void sendState(Output* outputs) {
    if (!deviceConnected || !pCharacteristic || !outputs) return;

    int li = blinkerLeftOutIndex();
    int ri = blinkerRightOutIndex();
    bool leftOn  = (currentMode == MODE_LEFT  || currentMode == MODE_HAZARD);
    bool rightOn = (currentMode == MODE_RIGHT || currentMode == MODE_HAZARD);

    // bit i = OUT_(i+1): digital LUB aktywny kierunek na tym OUT
    char bits[11];
    for (int i = 0; i < 10; i++) {
        bool on = outputs[i].isOn();
        if (i == li && leftOn)  on = true;
        if (i == ri && rightOn) on = true;
        // wyjścia kierunków nie bierz z digital (PWM)
        if (i == li || i == ri) {
            on = (i == li && leftOn) || (i == ri && rightOn);
        }
        bits[i] = on ? '1' : '0';
    }
    bits[10] = 0;

    char stateMsg[24];
    snprintf(stateMsg, sizeof(stateMsg), "STATE:%s", bits);
    pCharacteristic->setValue(stateMsg);
    pCharacteristic->notify();
}

void sendConfig() {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[40];
    snprintf(msg, sizeof(msg), "CFG:%d,%d,%d,%d,%d",
             cfg.fadeSpeed, cfg.blinkCount, cfg.curve,
             cfg.autoCancelSpeed, cfg.beamFade);
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.printf("CFG: %s\n", msg);
}

void sendInputCfg() {
    if (!deviceConnected || !pCharacteristic) return;
    char msg[400];
    int pos = snprintf(msg, sizeof(msg), "INCFG:");
    for (int i = 0; i < INPUT_COUNT; i++) {
        pos += snprintf(msg + pos, sizeof(msg) - pos, "%s%d,%d,%s",
                        (i ? ";" : ""),
                        inputCfg[i].mode,
                        inputCfg[i].outIndex + 1,
                        inputCfg[i].name);
        if (pos >= (int)sizeof(msg) - 4) break;
    }
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.println(msg);
}

void bleLog(const char* msg) {
    Serial.println(msg);
    if (!deviceConnected || !pCharacteristic) return;
    char buf[128];
    snprintf(buf, sizeof(buf), "LOG:%s", msg);
    pCharacteristic->setValue(buf);
    pCharacteristic->notify();
}

// ---------- obsługa komend (wywoływana z processBle w loop) ----------
static void handleCommand(const char* value) {
    Serial.printf("Otrzymano: %s\n", value);

    if (strcmp(value, "GET") == 0) {
        if (gOutputs) sendState(gOutputs);
        return;
    }

    if (strcmp(value, "GET_CFG") == 0) {
        sendConfig();
        return;
    }

    if (strcmp(value, "GET_INCFG") == 0) {
        sendInputCfg();
        return;
    }

    // SET_CFG:fade,blinks,curve,acSpeed[,beamFade]
    if (strncmp(value, "SET_CFG:", 8) == 0) {
        int fade = 12, blinks = 3, curve = 1, ac = 20, beamFade = (int)cfg.beamFade;
        int n = sscanf(value + 8, "%d,%d,%d,%d,%d",
                       &fade, &blinks, &curve, &ac, &beamFade);
        if (n >= 4) {
            cfg.fadeSpeed = (uint8_t)constrain(fade, 4, 40);
            cfg.blinkCount = (uint8_t)constrain(blinks, 1, 20);
            cfg.curve = (uint8_t)constrain(curve, 0, 2);
            cfg.autoCancelSpeed = (uint8_t)constrain(ac, 0, 200);
            if (n >= 5) cfg.beamFade = beamFade ? 1 : 0;
            saveConfig();
            sendConfig();
            Serial.printf("SET_CFG OK beamFade=%d\n", cfg.beamFade);
        }
        return;
    }

        // SET_INCFG:in1-10,mode,out1-10,name
    if (strncmp(value, "SET_INCFG:", 10) == 0) {
        int inNum = 0, mode = 0, outNum = 0;
        char name[16] = {0};
        int n = sscanf(value + 10, "%d,%d,%d,%15s",
                       &inNum, &mode, &outNum, name);
        if (n >= 3 && inNum >= 1 && inNum <= 10 && outNum >= 1 && outNum <= 10) {
            bool ok = setInputCfg(inNum - 1, (uint8_t)mode,
                                  (uint8_t)(outNum - 1),
                                  n >= 4 ? name : nullptr);
            Serial.println(ok ? "SET_INCFG OK" : "SET_INCFG FAIL");
            sendInputCfg();
            refreshBlinkerPins();
        } else {
            Serial.println("SET_INCFG FAIL (range)");
        }
        return;
    }

    // OUT:n:0/1  (1..10)
    if (strncmp(value, "OUT:", 4) == 0) {
        int num = 0, state = 0;
        if (sscanf(value + 4, "%d:%d", &num, &state) == 2 &&
            num >= 1 && num <= 10 && gOutputs) {
            int oi = num - 1;
            bool isLeft = false, isRight = false;
            for (int i = 0; i < INPUT_COUNT; i++) {
                if ((int)inputCfg[i].outIndex == oi) {
                    if (inputCfg[i].mode == IN_LEFT)  isLeft = true;
                    if (inputCfg[i].mode == IN_RIGHT) isRight = true;
                }
            }
            // fallback: klasyczne 1/5 gdy brak cfg
            if (!isLeft && !isRight) {
                if (num == 1) isLeft = true;
                if (num == 5) isRight = true;
            }

            if (isLeft && !isRight) {
                if (state) {
                    connectionBlink = false;
                    forceMode(MODE_LEFT);
                } else if (currentMode == MODE_LEFT || currentMode == MODE_HAZARD) {
                    forceMode(MODE_OFF);
                }
                Serial.println(state ? "LEFT ON" : "LEFT OFF");
            } else if (isRight && !isLeft) {
                if (state) {
                    connectionBlink = false;
                    forceMode(MODE_RIGHT);
                } else if (currentMode == MODE_RIGHT || currentMode == MODE_HAZARD) {
                    forceMode(MODE_OFF);
                }
                Serial.println(state ? "RIGHT ON" : "RIGHT OFF");
            } else if (num == 10 || (oi == starterOutIndex())) {
                setBleStarterPressed(state != 0);
            } else {
                if (state) gOutputs[oi].on();
                else gOutputs[oi].off();
                Serial.printf("Wyjście %d → %s\n", num, state ? "ON" : "OFF");
            }
            sendState(gOutputs);
        }
        return;
    }

    // HAZARD:0/1
    if (strncmp(value, "HAZARD:", 7) == 0) {
        int state = 0;
        if (sscanf(value + 7, "%d", &state) == 1) {
            connectionBlink = false;
            forceMode(state ? MODE_HAZARD : MODE_OFF);
            if (gOutputs) sendState(gOutputs);
        }
        return;
    }

    // IN10:0/1 – jak fizyczny przycisk starter/kill
    if (strncmp(value, "IN10:", 5) == 0) {
        int state = 0;
        if (sscanf(value + 5, "%d", &state) == 1) {
            setBleStarterPressed(state != 0);
        }
        return;
    }

    // SPEED:xx.x
    if (strncmp(value, "SPEED:", 6) == 0) {
        float kmh = 0;
        if (sscanf(value + 6, "%f", &kmh) == 1) {
            setCurrentSpeed(kmh);
        }
        return;
    }

    // SHUTDOWN_NOW – hard kill z apki
    if (strcmp(value, "SHUTDOWN_NOW") == 0) {
        if (gOutputs) requestShutdown(gOutputs);
        return;
    }
}

void processBle() {
    while (cmdTail != cmdHead) {
        char local[96];
        strncpy(local, cmdQueue[cmdTail], 95);
        local[95] = '\0';
        cmdTail = (cmdTail + 1) % CMD_Q;
        handleCommand(local);
    }
}

// ---------- callbacks ----------
class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) {
        deviceConnected = true;
        tryArm();
        Serial.println("BLE: Połączono");
        Serial.println("HUB ARMED (apka)");
    }

    void onDisconnect(NimBLEServer* pServer) {
        deviceConnected = false;
        Serial.println("BLE: Rozłączono");
        NimBLEDevice::startAdvertising();
    }
};

class CharacteristicCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string value = pCharacteristic->getValue();
        if (value.empty()) return;
        enqueueCmd(value);
    }
};

void setupBLE(Output* outputs) {
    gOutputs = outputs;
    Serial.println("Uruchamiam BLE...");
    NimBLEDevice::init("YamaHub");
    NimBLEDevice::setMTU(256);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);

    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    NimBLEService* pService = pServer->createService(SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE |
        NIMBLE_PROPERTY::WRITE_NR | NIMBLE_PROPERTY::NOTIFY
    );
    pCharacteristic->setCallbacks(new CharacteristicCallbacks());
    pCharacteristic->setValue("YamaHub ready");
    pService->start();

    NimBLEAdvertising* pAdv = NimBLEDevice::getAdvertising();
    pAdv->addServiceUUID(SERVICE_UUID);
    pAdv->setName("YamaHub");
    pAdv->start();
    Serial.println("BLE gotowe");
}