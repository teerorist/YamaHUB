#include "input_moment.h"
#include "input_cfg.h"

void handleMomentInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    if (inputCfg[inIndex].mode != IN_MOMENT) return;

    uint8_t oi = inputCfg[inIndex].outIndex;
    if (oi > 9) return;

    static bool last[10] = {false};
    bool pressed = btn.isPressed();
    if (pressed == last[inIndex]) return;
    last[inIndex] = pressed;

    if (pressed) outputs[oi].on();
    else         outputs[oi].off();
    stateChanged = true;
}
