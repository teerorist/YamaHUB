#pragma once
#include "Output.h"
#include "Button.h"

void sendState(Output* outputs);
void sendInputStatesIfChanged(Button* buttons);
void resetInputStatePush();
void sendConfig();
void sendInputCfg();
void sendModesV4();
void sendInputCfgV4();
void sendRpm(int rpm);
void sendFuel(int pct);
void sendOil(int on);
void handleBleCommand(const char* value);
void bleLog(const char* msg);
