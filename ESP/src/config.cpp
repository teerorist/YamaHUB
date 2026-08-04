#include "config.h"
#include <Preferences.h>

BlinkerConfig cfg = {
    .fadeSpeed = 12,
    .blinkCount = 3,
    .curve = 1,
    .autoCancelSpeed = 20,
    .beamFade = 1
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
    cfg.autoCancelSpeed = p.getUChar("acSpeed", 20);
    cfg.beamFade        = p.getUChar("beamFade", 1);

    if (cfg.fadeSpeed < 4) cfg.fadeSpeed = 4;
    if (cfg.fadeSpeed > 40) cfg.fadeSpeed = 40;
    if (cfg.blinkCount > 20) cfg.blinkCount = 3;
    if (cfg.curve > 2) cfg.curve = 1;
    if (cfg.beamFade > 1) cfg.beamFade = 1;

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
    p.putUChar("beamFade", cfg.beamFade);
    p.end();
    Serial.println("Config: saved");
}