#pragma once
#include "Output.h"

extern bool deviceConnected;

void setupBLE(Output* outputs);
void processBle();
void sendState(Output* outputs);
void sendConfig();
void sendInputCfg();
void bleLog(const char* msg);
bool isBleConnected();