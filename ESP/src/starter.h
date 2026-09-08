#pragma once
#include "Button.h"
#include "Output.h"

void handleStarter(Button& btn, Output* outputs, bool& stateChanged);
void starterSet(bool on, Output* outputs, bool& stateChanged);
void updateShutdown();
void requestShutdown(Output* outputs);
void requestShutdownNow(Output* outputs);
void setBleStarterPressed(bool pressed);
bool isStarterEnabled(Output* outputs);
void updateStarterInterlock(Output* outputs, bool& stateChanged);
void syncStarterKillOutput(Output* outputs, bool& stateChanged);
void handleKillSwitch(Button* buttons, Output* outputs, bool& stateChanged);