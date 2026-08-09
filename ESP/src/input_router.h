#pragma once
#include "Button.h"
#include "Output.h"

/**
 * GR I – router wejść:
 *  LEFT/RIGHT  → blinkers.cpp
 *  STARTER     → starter.cpp
 *  TOGGLE      → input_button.cpp
 *  MOMENT      → input_moment.cpp (brake/neutral)
 *  SENSOR      → input_sensor.cpp
 *  DISABLED    → nic
 */
void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged);
