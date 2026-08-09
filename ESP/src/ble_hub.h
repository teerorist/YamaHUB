#pragma once
#include "Output.h"
#include "ble_protocol.h"

extern bool deviceConnected;
extern Output* gOutputs;

void setupBLE(Output* outputs);
void processBle();
bool isBleConnected();
