#pragma once
#include <Arduino.h>

class Button {
public:
    Button(uint8_t pin) : _pin(pin) {}

    void begin() {
        pinMode(_pin, INPUT_PULLUP);
        _lastState = digitalRead(_pin);
        _lastDebounceTime = 0;
        _stableState = HIGH;
    }

    // Zwraca true tylko w momencie naciśnięcia
    bool wasPressed() {
        bool reading = digitalRead(_pin);

        if (reading != _lastState) {
            _lastDebounceTime = millis();
        }

        if ((millis() - _lastDebounceTime) > 30) {
            if (reading != _stableState) {
                _stableState = reading;
                if (_stableState == LOW) {   // Active LOW
                    _lastState = reading;
                    return true;
                }
            }
        }

        _lastState = reading;
        return false;
    }

    bool isPressed() {
        return digitalRead(_pin) == LOW;
    }

private:
    uint8_t _pin;
    bool _lastState = HIGH;
    bool _stableState = HIGH;
    unsigned long _lastDebounceTime = 0;
};