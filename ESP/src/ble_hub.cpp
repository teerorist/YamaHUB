#include "ble_hub.h"
#include "ble_protocol.h"
#include "arming.h"
#include "starter.h"
#include <NimBLEDevice.h>
#include <cstring>

bool deviceConnected = false;
Output* gOutputs = nullptr;
NimBLECharacteristic* pCharacteristic = nullptr;

static NimBLEServer* pServer = nullptr;

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

void processBle() {
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
        tryArm();
        Serial.println("BLE: Połączono");
        Serial.println("HUB ARMED (apka)");
    }
    void onDisconnect(NimBLEServer* s) {
        deviceConnected = false;
        Serial.println("BLE: Rozłączono");
        NimBLEDevice::startAdvertising();
    }
};

class CharacteristicCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c) {
        std::string value = c->getValue();
        if (!value.empty()) enqueueCmd(value);
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

void bleLog(const char* msg) {
    Serial.println(msg);
    if (!deviceConnected || !pCharacteristic) return;
    char buf[128];
    snprintf(buf, sizeof(buf), "LOG:%s", msg);
    pCharacteristic->setValue(buf);
    pCharacteristic->notify();
}

bool isBleConnected() { return deviceConnected; }

