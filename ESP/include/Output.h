#pragma once
#include <Arduino.h>
#include "pins.h"

class Output {
public:
    Output(uint8_t pin) : _pin(pin) {}
    uint8_t getPin() const { return _pin; }

    void begin() {
        if (pinValid(_pin)) {
            pinMode(_pin, OUTPUT);
            digitalWrite(_pin, LOW);
        }
        _state = false;
    }

    void on() {
        if (pinValid(_pin)) digitalWrite(_pin, HIGH);
        _state = true;
    }

    void off() {
        if (pinValid(_pin)) digitalWrite(_pin, LOW);
        _state = false;
    }

    void toggle() {
        if (_state) {
            off();
        } else {
            on();
        }
    }

    bool isOn() const {
        return _state;
    }

private:
    uint8_t _pin;
    bool _state = false;
};