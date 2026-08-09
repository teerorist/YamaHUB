#include "input_router.h"
#include "input_cfg.h"
#include "input_button.h"
#include "input_moment.h"
#include "input_sensor.h"

void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged) {
    for (int i = 0; i < INPUT_COUNT; i++) {
        switch (inputCfg[i].mode) {
            case IN_DISABLED:
            case IN_LEFT:      // blinkers
            case IN_RIGHT:     // blinkers
            case IN_STARTER:   // starter
                break;
            case IN_TOGGLE:
                handleButtonInput(i, buttons[i], outputs, stateChanged);
                break;
            case IN_MOMENT:
                handleMomentInput(i, buttons[i], outputs, stateChanged);
                break;
            case IN_SENSOR:
                handleSensorInput(i, buttons[i], outputs, stateChanged);
                break;
            default:
                break;
        }
    }
}
