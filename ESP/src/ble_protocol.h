#pragma once
#include "Output.h"

void sendState(Output* outputs);
void sendConfig();
void sendInputCfg();
void handleBleCommand(const char* value);
void bleLog(const char* msg);
