#include "input_button.h"
#include "input_cfg.h"
#include "beams.h"

void handleButtonInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    if (inputCfg[inIndex].mode != IN_TOGGLE) return;

    if (!inputCfg[inIndex].outputEnabled) return;
    uint8_t oi = inputCfg[inIndex].outputIndex;
    if (oi > 9) return;

    static bool bleWas[INPUT_COUNT] = {false};
    bool ble = isBleInputPressed(inIndex);
    bool bleEdge = ble && !bleWas[inIndex];
    bleWas[inIndex] = ble;
    if (!btn.wasPressed() && !bleEdge) return;

    if (isBeamOutput(oi)) {
        static uint8_t beamOn[10] = {0};
        beamOn[oi] = beamOn[oi] ? 0 : 255;
        requestBeamLevel(oi, beamOn[oi]);
    } else {
        outputs[oi].toggle();
    }
    stateChanged = true;
}
