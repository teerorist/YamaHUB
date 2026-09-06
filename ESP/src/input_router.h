#pragma once
#include "Button.h"
#include "Output.h"

/**
 * GR I – router wejść:
 *  LEFT/RIGHT  → blinkers.cpp
 *  STARTER     → starter.cpp
 *  LIGHTS      → beams.cpp
 *  BRAKE 1/2   → input_sensor.cpp (OR)
 *  BUTTON USER → input_button.cpp
 *  SENSOR      → input_sensor.cpp
 *  DISABLED    → nic
 */
void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged);
