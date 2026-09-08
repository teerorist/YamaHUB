#include "input_sensor.h"
#include "input_cfg.h"

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
    if (out < 0) return;

    static bool lastOn[10] = {false};
    if (on == lastOn[out]) return;
    lastOn[out] = on;

    if (on) outputs[out].on();
    else    outputs[out].off();
    stateChanged = true;
}
