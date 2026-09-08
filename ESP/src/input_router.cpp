#include "input_router.h"
#include "input_cfg.h"
#include "input_button.h"
#include "input_sensor.h"
#include "beams.h"

void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged) {
    for (int i = 0; i < INPUT_COUNT; i++) {
        const InputCfgItem& cfg = inputCfg[i];

        switch (cfg.functionId) {
            case FN_LEFT:
            case FN_RIGHT:
            case FN_STARTER:
            case FN_KILL_SWITCH:
            case FN_BRAKE_1:
            case FN_BRAKE_2:
            case FN_NONE:
                break;
            case FN_LIGHTS_1:
            case FN_LIGHTS_2:
                handleLightsInput(i, buttons[i], outputs, stateChanged);
                break;
            default:
                if (cfg.category == CAT_BUTTON)
                    handleButtonInput(i, buttons[i], outputs, stateChanged);
                else if (cfg.category == CAT_SENSOR)
                    handleSensorInput(i, buttons[i], outputs, stateChanged);
                break;
        }
    }
    updateBrakeOutput(outputs, stateChanged);
}
