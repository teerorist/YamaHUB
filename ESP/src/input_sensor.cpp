#include "input_sensor.h"
#include "input_cfg.h"

void handleSensorInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    if (inputCfg[inIndex].mode != IN_SENSOR) return;

    static bool last[10] = {false};
    bool pressed = isInputActive(inIndex, btn.isPressed());
    if (pressed == last[inIndex]) return;
    last[inIndex] = pressed;

    if (!inputCfg[inIndex].outputEnabled) return;
    uint8_t oi = inputCfg[inIndex].outputIndex;
    if (oi > 9) return;

    if (pressed) outputs[oi].on();
    else         outputs[oi].off();
    stateChanged = true;
}
