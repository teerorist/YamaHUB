#pragma once
#include "Button.h"
#include "Output.h"

/** GR I.3 BRAKE / NEUTRAL (IN_MOMENT) – pressed=ON, released=OFF */
void handleMomentInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged);
