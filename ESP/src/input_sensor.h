#pragma once
#include "Button.h"
#include "Output.h"

/** GR I.5 SENSOR – stan wejścia = stan wyjścia (jeśli ma OUT) */
void handleSensorInput(int inIndex, Button& btn, Output* outputs, bool& stateChanged);
