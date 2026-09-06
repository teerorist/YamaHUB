#include "ble_hub.h"
#include "ble_protocol.h"
#include "arming.h"
#include "starter.h"
#include "input_cfg.h"
#include "blinkers.h"
#include "beams.h"
#include <NimBLEDevice.h>
#include <cstring>

bool deviceConnected = false;
Output* gOutputs = nullptr;
Button* gButtons = nullptr;
NimBLECharacteristic* pCharacteristic = nullptr;

static NimBLEServer* pServer = nullptr;
static volatile bool connectionSyncPending = false;

#define SERVICE_UUID        "FFE0"
#define CHARACTERISTIC_UUID "FFE1"

// kolejka komend z callbacka BLE (krótka, bez alokacji)
static char cmdQueue[8][96];
static volatile uint8_t cmdHead = 0, cmdTail = 0;

static void enqueueCmd(const char* v) {
    if (!v || !v[0]) return;
    uint8_t next = (cmdHead + 1) % 8;
    if (next == cmdTail) return; // full drop
    strncpy(cmdQueue[cmdHead], v, 95);
    cmdQueue[cmdHead][95] = 0;
    cmdHead = next;
}

static void finishInCfgApply(InCfgApply r) {
    if (r == INCFG_IDLE || r == INCFG_STAGED) return;
    if (r == INCFG_COMMITTED) {
        refreshBlinkerPins();
        setupBeams();
        if (gOutputs) sendState(gOutputs);
        return;
    }
    bleLog(inputCfgRejectReason());
    sendInputCfgV4();
    if (gOutputs) sendState(gOutputs);
}

void processBle() {
    if (connectionSyncPending && deviceConnected) {
        connectionSyncPending = false;
        resetInputStatePush();
        sendInputStatesIfChanged(gButtons);
        if (gOutputs) sendState(gOutputs);
        sendConfig();
        // Aplikacja sama zapyta o MODES i INCFG przy starcie
        Serial.println("BLE: fast sync sent");
    }

    finishInCfgApply(pollInputCfgTxn());

    while (cmdTail != cmdHead) {
        char local[96];
        strncpy(local, cmdQueue[cmdTail], 95);
        local[95] = '\0';
        cmdTail = (cmdTail + 1) % 8;
        handleBleCommand(local);
    }
}

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* s) {
        deviceConnected = true;
        connectionSyncPending = true;
        tryArm();
        Serial.println("BLE: Połączono");
        Serial.println("HUB ARMED (apka)");
    }
    void onDisconnect(NimBLEServer* s) {
        deviceConnected = false;
        disarmHub();
        Serial.println("BLE: Rozłączono");
        NimBLEDevice::startAdvertising();
    }
};

class CharacteristicCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c) {
        std::string value = c->getValue();
        if (!value.empty()) enqueueCmd(value.c_str());
    }
};

void setupBLE(Output* outputs, Button* buttons) {
    gOutputs = outputs;
    gButtons = buttons;
    resetInputStatePush();
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

void bleLog(const char* msg) {
    Serial.println(msg);
    if (!deviceConnected || !pCharacteristic) return;
    char buf[128];
    snprintf(buf, sizeof(buf), "LOG:%s", msg);
    pCharacteristic->setValue(buf);
    pCharacteristic->notify();
}

bool isBleConnected() { return deviceConnected; }