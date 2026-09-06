#include "can_bus.h"
#include "pins.h"
#include "ble_protocol.h"
#include <mcp2515.h>
#include <SPI.h>

MCP2515 mcp2515(CAN_CS);

float canSpeedKmh = 0.0f;
int canRpm = 0;
int canFuelPct = 0;

static char canStatusBuf[64] = "CAN: Init...";

void setupCAN() {
    Serial.println("CAN: SPI Hard Probe on TX/RX...");

    pinMode(CAN_CS, OUTPUT);
    digitalWrite(CAN_CS, HIGH);
    delay(100);

    // SPI na bezpiecznych pinach TX/RX dla Waveshare S3-LCD
    SPI.begin(CAN_SCK, CAN_MISO, CAN_MOSI, -1);
    delay(50);

    // RESET i odczyt rejestru CANSTAT
    mcp2515.reset();
    delay(50);

    digitalWrite(CAN_CS, LOW);
    SPI.transfer(0x03); SPI.transfer(0x0E); // READ CANSTAT
    uint8_t stat = SPI.transfer(0x00);
    digitalWrite(CAN_CS, HIGH);

    if (stat == 0x00 || stat == 0xFF) {
        snprintf(canStatusBuf, sizeof(canStatusBuf), "CAN: NO CHIP (0x%02X)", stat);
    } else {
        MCP2515::ERROR err = mcp2515.setBitrate(CAN_500KBPS, MCP_8MHZ);
        if (err == MCP2515::ERROR_OK) {
            snprintf(canStatusBuf, sizeof(canStatusBuf), "CAN: READY (0x%02X)", stat);
            mcp2515.setNormalMode();
        } else {
            snprintf(canStatusBuf, sizeof(canStatusBuf), "CAN: Config Fail %d (0x%02X)", (int)err, stat);
        }
    }
    Serial.println(canStatusBuf);
}

void reportCANStatus() {
    static unsigned long lastReport = 0;
    if (millis() - lastReport > 2000) {
        bleLog(canStatusBuf);
        lastReport = millis();
    }
}

void runCANLoopbackTest() {
    bleLog("CAN: Loopback start");
    mcp2515.setLoopbackMode();
    struct can_frame frame;
    frame.can_id = 0x201;
    frame.can_dlc = 8;
    frame.data[0] = 0x45; frame.data[1] = 0x70; // 4444 RPM
    frame.data[4] = 0x13; frame.data[5] = 0x88; // 50 SPD

    if (mcp2515.sendMessage(&frame) == MCP2515::ERROR_OK) {
        delay(20);
        struct can_frame rx;
        if (mcp2515.readMessage(&rx) == MCP2515::ERROR_OK) {
            bleLog("CAN: LOOPBACK SUCCESS!");
        } else {
            bleLog("CAN: Loopback RX Fail");
        }
    } else {
        bleLog("CAN: Send failed");
    }
    mcp2515.setNormalMode();
}

void updateCAN() {
    struct can_frame frame;
    if (mcp2515.readMessage(&frame) == MCP2515::ERROR_OK) {
        if (frame.can_id == 0x201) {
            uint16_t rpmRaw = (frame.data[0] << 8) | frame.data[1];
            canRpm = rpmRaw / 4;
            uint16_t spdRaw = (frame.data[4] << 8) | frame.data[5];
            canSpeedKmh = (float)spdRaw / 100.0f;
        } else if (frame.can_id == 0x44C) {
            canFuelPct = frame.data[0];
        }
    }
}
