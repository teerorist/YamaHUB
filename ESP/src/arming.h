#pragma once

extern volatile bool hubArmed;

bool tryArm();
void disarmHub();
void armFromApp();