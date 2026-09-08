#include "input_sensor.h"
#include "input_cfg.h"
#include "config.h"
#include "pins.h"
#include "driver/gpio.h"

extern uint8_t outLevel[10];

static const int BRAKE_POSITION_PWM_CH = 0;
static bool brakePositionPwmConfigured = false;
static int brakePositionPwmOut = -1;
static int lastBrakePositionOut = -1;
static bool lastBrakePositionOn = false;

static int lowBeamOut() {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == FN_LIGHTS_1 &&
            outAssigned(inputCfg[i].outSecondary)) {
            return (int)inputCfg[i].outSecondary;
        }
    }
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == FN_LIGHTS_2 &&
            outAssigned(inputCfg[i].outPrimary)) {
            return (int)inputCfg[i].outPrimary;
        }
    }
    return -1;
}

static void releaseBrakePositionPwm(Output* outputs) {
    if (brakePositionPwmOut < 0) return;
    const int gpio = outputs[brakePositionPwmOut].getPin();
    if (pinValid(gpio)) {
        ledcDetachPin(gpio);
        gpio_reset_pin((gpio_num_t)gpio);
        pinMode(gpio, OUTPUT);
    }
    brakePositionPwmOut = -1;
}

void handleSensorInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    if (inputCfg[inIndex].category != CAT_SENSOR) return;
    if (inputCfg[inIndex].functionId == FN_BRAKE_1 ||
        inputCfg[inIndex].functionId == FN_BRAKE_2) return;

    static bool last[10] = {false};
    bool pressed = isInputActive(inIndex, btn.isPressed());
    if (pressed == last[inIndex]) return;
    last[inIndex] = pressed;

    if (!inputCfg[inIndex].outputEnabled) return;
    uint8_t oi = inputCfg[inIndex].outPrimary;
    if (!outAssigned(oi) || !outputs) return;

    if (pressed) outputs[oi].on();
    else         outputs[oi].off();
    stateChanged = true;
}

void updateBrakeOutput(Output* outputs, bool& stateChanged) {
    if (!outputs) return;
    int out = -1;
    bool on = false;
    for (int i = 0; i < INPUT_COUNT; i++) {
        uint8_t fn = inputCfg[i].functionId;
        if (fn != FN_BRAKE_1 && fn != FN_BRAKE_2) continue;
        if (!inputCfg[i].outputEnabled || !outAssigned(inputCfg[i].outPrimary)) continue;
        out = (int)inputCfg[i].outPrimary;
        if (getEffectiveInputState(i)) on = true;
    }
    if (out < 0) {
        releaseBrakePositionPwm(outputs);
        return;
    }

    const int lowOut = lowBeamOut();
    const bool positionOn = lowOut >= 0 && lowOut < 10 && outLevel[lowOut] > 20;
    if (cfg.commonBrakePositionWire) {
        const uint8_t level = on ? 255 : positionOn
            ? (uint8_t)((cfg.positionBrightness * 255U) / 100U) : 0;
        const int gpio = outputs[out].getPin();
        if (brakePositionPwmOut != out) {
            releaseBrakePositionPwm(outputs);
            if (!brakePositionPwmConfigured) {
                ledcSetup(BRAKE_POSITION_PWM_CH, 5000, 8);
                brakePositionPwmConfigured = true;
            }
            if (!pinValid(gpio)) return;
            ledcAttachPin(gpio, BRAKE_POSITION_PWM_CH);
            brakePositionPwmOut = out;
        }
        if (level > 0) outputs[out].on();
        else outputs[out].off();
        ledcWrite(BRAKE_POSITION_PWM_CH, level);
        const bool outputOn = level > 0;
        if (lastBrakePositionOut != out || lastBrakePositionOn != outputOn) {
            lastBrakePositionOut = out;
            lastBrakePositionOn = outputOn;
            stateChanged = true;
        }
        return;
    }

    const bool wasPwmActive = brakePositionPwmOut >= 0;
    releaseBrakePositionPwm(outputs);
    lastBrakePositionOut = -1;
    lastBrakePositionOn = false;

    static bool lastOn[10] = {false};
    if (!wasPwmActive && on == lastOn[out]) return;
    lastOn[out] = on;

    if (on) outputs[out].on();
    else    outputs[out].off();
    stateChanged = true;
}
