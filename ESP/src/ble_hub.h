#pragma once
#include "Output.h"
#include "ble_protocol.h"

extern bool deviceConnected;
extern Output* gOutputs;
extern Button* gButtons;

void setupBLE(Output* outputs, Button* buttons);
void processBle();
bool isBleConnected();
