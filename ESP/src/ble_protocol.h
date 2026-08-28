#pragma once
#include "Output.h"
#include "Button.h"

void sendState(Output* outputs);
void sendInputStatesIfChanged(Button* buttons);
void resetInputStatePush();
void sendConfig();
void sendInputCfg();
void handleBleCommand(const char* value);
void bleLog(const char* msg);
