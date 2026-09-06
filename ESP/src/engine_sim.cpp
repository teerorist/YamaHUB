#include "engine_sim.h"

int currentRpm = 0;
int currentGear = 1;

// Prędkości przy 4000 RPM dla biegów 1-6 (Focus MK2 spec)
static const float v4k[] = { 17.0f, 30.0f, 46.0f, 73.0f, 92.0f, 108.0f };

void updateEngineSim(float speedKmh) {
    if (speedKmh < 0.5f) {
        currentRpm = 1100; // Wolne obroty
        currentGear = 1;
        return;
    }

    // Obliczamy RPM dla obecnego biegu
    float rpm = (speedKmh / v4k[currentGear - 1]) * 4000.0f;

    // Shift Up
    if (rpm > 5050.0f && currentGear < 6) {
        currentGear++;
        rpm = (speedKmh / v4k[currentGear - 1]) * 4000.0f;
    }
    // Shift Down
    else if (rpm < 2400.0f && currentGear > 1) {
        currentGear--;
        rpm = (speedKmh / v4k[currentGear - 1]) * 4000.0f;
    }

    if (rpm < 1100.0f) rpm = 1100.0f;
    if (rpm > 12000.0f) rpm = 12000.0f;

    currentRpm = (int)rpm;
}
