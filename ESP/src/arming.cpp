#include "arming.h"
#include "ble_hub.h"

bool hubArmed = false;

bool tryArm() {
    if (hubArmed) return true;
    if (deviceConnected) {
        hubArmed = true;
        Serial.println("HUB ARMED");
        return true;
    }
    Serial.println("Zablokowane – połącz apkę");
    return false;
}

void disarmHub() {
    hubArmed = false;
    Serial.println("HUB DISARMED");
}

void armFromApp() {
    if (!hubArmed) {
        hubArmed = true;
        Serial.println("HUB ARMED (apka)");
    }
}