#pragma once
#include "Button.h"
#include "Output.h"

/** BUTTON USER – wasPressed → toggle OUT / beam */
void handleButtonInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged);
