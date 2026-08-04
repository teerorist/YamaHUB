#pragma once
#include <Arduino.h>

class Output {
public:
    Output(uint8_t pin) : _pin(pin) {}

    void begin() {
        pinMode(_pin, OUTPUT);
        off();
    }

    void on() {
        digitalWrite(_pin, HIGH);
        _state = true;
    }

    void off() {
        digitalWrite(_pin, LOW);
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