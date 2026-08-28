#pragma once
#include "Button.h"
#include "Output.h"

/**
 * GR I – router wejść:
 *  LEFT/RIGHT  → blinkers.cpp
 *  STARTER     → starter.cpp
 *  TOGGLE      → input_button.cpp
 *  SENSOR      → input_sensor.cpp
 *  SENSOR      → input_sensor.cpp
 *  DISABLED    → nic
 */
void handleConfigurableInputs(Button* buttons, Output* outputs, bool& stateChanged);
