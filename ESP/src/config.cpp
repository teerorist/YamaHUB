#include "config.h"
#include <Preferences.h>

BlinkerConfig cfg = {
    .fadeSpeed = 12,
    .blinkCount = 3,
    .curve = 1,
    .autoCancelSpeed = 20,
    .autoCancel = 1,
    .autoLights = 0
};

void loadConfig() {
    Preferences p;
    if (!p.begin("yh_cfg", false)) {
        Serial.println("Config: NVS open fail → defaults");
        return;
    }
    cfg.fadeSpeed       = p.getUChar("fade", 12);
    cfg.blinkCount      = p.getUChar("blinks", 3);
    cfg.curve           = p.getUChar("curve", 1);
    uint8_t ac          = p.getUChar("acSpeed", 20);
    cfg.autoCancel      = p.getUChar("acOn", ac == 0 ? 0 : 1);
    cfg.autoLights      = p.getUChar("acLights", 0);

    if (cfg.fadeSpeed < 4) cfg.fadeSpeed = 4;
    if (cfg.fadeSpeed > 40) cfg.fadeSpeed = 40;
    if (cfg.blinkCount < 2 || cfg.blinkCount > 6) cfg.blinkCount = 3;
    if (cfg.curve > 2) cfg.curve = 1;
    if (ac < 5 || ac > 30) cfg.autoCancelSpeed = 20;
    else cfg.autoCancelSpeed = ac;
    if (cfg.autoCancel > 1) cfg.autoCancel = 1;
    if (cfg.autoLights > 1) cfg.autoLights = 0;

    p.end();
    Serial.println("Config: loaded");
}

void saveConfig() {
    Preferences p;
    if (!p.begin("yh_cfg", false)) return;
    p.putUChar("fade", cfg.fadeSpeed);
    p.putUChar("blinks", cfg.blinkCount);
    p.putUChar("curve", cfg.curve);
    p.putUChar("acSpeed", cfg.autoCancelSpeed);
    p.putUChar("acOn", cfg.autoCancel);
    p.putUChar("acLights", cfg.autoLights);
    p.end();
    Serial.println("Config: saved");
}