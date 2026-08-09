#pragma once
#include "Button.h"
#include "Output.h"

/** GR I.6 BUTTON (IN_TOGGLE) – wasPressed → toggle OUT / beam */
void handleButtonInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged);
