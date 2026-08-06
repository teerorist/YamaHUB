#include "display_hub.h"
#include "inputs.h"
#include "blinkers.h"
#include <LovyanGFX.hpp>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

class LGFX : public lgfx::LGFX_Device {
    lgfx::Panel_ST7789 _panel;
    lgfx::Bus_SPI _bus;
    lgfx::Light_PWM _light;

public:
    LGFX(void) {
        {
            auto cfg = _bus.config();
            cfg.spi_host      = SPI2_HOST;
            cfg.spi_mode      = 0;
            cfg.freq_write    = 80000000;
            cfg.freq_read     = 16000000;
            cfg.spi_3wire    = true;
            cfg.use_lock      = true;
            cfg.dma_channel   = SPI_DMA_CH_AUTO;
            cfg.pin_sclk      = 40;
            cfg.pin_mosi      = 45;
            cfg.pin_miso      = -1;
            cfg.pin_dc        = 41;
            _bus.config(cfg);
            _panel.setBus(&_bus);
        }
        {
            auto cfg = _panel.config();
            cfg.pin_cs           = 42;
            cfg.pin_rst          = 39;
            cfg.pin_busy         = -1;
            cfg.panel_width      = 172;
            cfg.panel_height     = 320;
            cfg.offset_x         = 34;
            cfg.offset_y         = 0;
            cfg.offset_rotation  = 0;
            cfg.dummy_read_pixel = 8;
            cfg.dummy_read_bits  = 1;
            cfg.readable         = false;
            cfg.invert           = true;
            cfg.rgb_order        = false;
            cfg.dlen_16bit       = false;
            cfg.bus_shared       = false;
            _panel.config(cfg);
        }
        {
            auto cfg = _light.config();
            cfg.pin_bl      = 46;
            cfg.invert      = false;
            cfg.freq        = 44100;
            cfg.pwm_channel = 7;
            _light.config(cfg);
            _panel.setLight(&_light);
        }
        setPanel(&_panel);
    }
};

static LGFX tft;
uint8_t outLevel[10] = {0};
static uint8_t prevLevel[10] = {255};
static bool prevUsed[10] = {false};

static const uint16_t COL_OFF    = 0x18C3;
static const uint16_t COL_ORANGE = 0xFD20;
static const uint16_t COL_GREEN  = 0x07E0;
static const uint16_t COL_WHITE  = 0xFFFF;
static const uint16_t COL_BLUE   = 0x05BF;
static const uint16_t COL_RED    = 0xF800;
static const uint16_t COL_CYAN   = 0x07FF;
static const uint16_t COL_DARK   = 0x4208;
static const uint16_t COL_X      = 0x8410;
static const uint16_t COL_NUM    = 0xC618;

static bool nameHas(const char* n, const char* key) {
    if (!n || !key) return false;
    for (const char* p = n; *p; ++p) {
        const char* a = p;
        const char* b = key;
        while (*a && *b && (tolower((unsigned char)*a) == tolower((unsigned char)*b))) {
            ++a; ++b;
        }
        if (!*b) return true;
    }
    return false;
}

static int parseLightsHi(const char* n) {
    if (!n) return -1;
    const char* p = strstr(n, "LIGHTS_H");
    if (!p) p = strstr(n, "lights_h");
    if (!p) return -1;
    p += 8;
    int v = atoi(p);
    if (v >= 1 && v <= 10) return v - 1;
    return -1;
}

static bool outIsUsed(int oi) {
    for (int j = 0; j < INPUT_COUNT; j++) {
        uint8_t m = inputCfg[j].mode;
        if (m == IN_DISABLED || m == IN_SENSOR) continue;
        if (m == IN_MOMENT && nameHas(inputCfg[j].name, "neutral")) continue;
        if ((int)inputCfg[j].outIndex == oi) return true;
        int hi = parseLightsHi(inputCfg[j].name);
        if (hi == oi) return true;
    }
    return false;
}

/** Kolor wg funkcji przypisanej do OUT oi (0..9). */
static uint16_t colorForOut(int oi) {
    // kierunki – zawsze z aktualnej mapy blinkers
    if (oi == blinkerLeftOutIndex() || oi == blinkerRightOutIndex())
        return COL_ORANGE;

    for (int j = 0; j < INPUT_COUNT; j++) {
        uint8_t m = inputCfg[j].mode;
        const char* n = inputCfg[j].name;
        int out = (int)inputCfg[j].outIndex;
        int hi = parseLightsHi(n);

        if (m == IN_STARTER && out == oi) return COL_GREEN;
        if (out != oi && hi != oi) continue;

        if (nameHas(n, "brake")) return COL_RED;
        if (hi == oi) return COL_BLUE;                       // HI BEAM (drugi OUT)
        if (nameHas(n, "hi_beam") || nameHas(n, "hibeam")) return COL_BLUE;
        if (nameHas(n, "low_beam") || nameHas(n, "lowbeam")) return COL_WHITE;
        if (nameHas(n, "lights") || nameHas(n, "light")) {
            // 2× LIGHTS: druga pozycja = HI
            // prosto: lights z out==oi i jest druga lights z wyższym in → może być LOW
            return COL_WHITE;
        }
        if (m == IN_TOGGLE || m == IN_MOMENT) return COL_CYAN;
    }
    return COL_CYAN;
}

static uint16_t dimColor(uint16_t c, uint8_t level) {
    if (level == 0) return COL_OFF;
    if (level >= 250) return c;
    uint8_t r = ((c >> 11) & 0x1F) * level / 255;
    uint8_t g = ((c >> 5)  & 0x3F) * level / 255;
    uint8_t b = (c & 0x1F) * level / 255;
    return (r << 11) | (g << 5) | b;
}

void setupDisplay() {
    tft.init();
    tft.setRotation(0);
    tft.setBrightness(200);
    tft.fillScreen(0x0000);
    drawOutputs(true);
    Serial.println("Display: OK");
}

void setOutLevel(int index, uint8_t level) {
    if (index < 0 || index > 9) return;
    outLevel[index] = level;
}

void drawOutputs(bool force) {
    // siatka 2×5: kolumna0 = OUT 1,3,5,7,9  kolumna1 = 2,4,6,8,10
    // albo kolejno 0..9 wierszami: (0,0)=OUT1 (1,0)=OUT2 ...
    const int cols = 2, r = 22;
    const int marginX = 28, marginY = 20;
    const int stepX = 116, stepY = 58;

    for (int i = 0; i < 10; i++) {
        bool used = outIsUsed(i);
        if (!force && outLevel[i] == prevLevel[i] && used == prevUsed[i]) continue;
        prevLevel[i] = outLevel[i];
        prevUsed[i] = used;

        int x = marginX + (i % cols) * stepX;
        int y = marginY + (i / cols) * stepY;

        if (!used) {
            tft.fillCircle(x, y, r, COL_OFF);
            tft.drawCircle(x, y, r, COL_DARK);
            // numer
            tft.setTextDatum(MC_DATUM);
            tft.setTextColor(COL_X);
            tft.setFont(&fonts::Font2);
            tft.drawNumber(i + 1, x, y - 2);
            // X
            tft.drawLine(x - 8, y - 8, x + 8, y + 8, COL_X);
            tft.drawLine(x + 8, y - 8, x - 8, y + 8, COL_X);
            continue;
        }

        uint16_t c = dimColor(colorForOut(i), outLevel[i]);
        tft.fillCircle(x, y, r, c);
        tft.drawCircle(x, y, r, COL_DARK);
        // numer w środku
        tft.setTextDatum(MC_DATUM);
        // kontrast: jasne tło → ciemny tekst
        uint16_t tc = (outLevel[i] > 180 && (c == COL_WHITE || c == COL_CYAN || c == COL_ORANGE))
                          ? 0x0000 : COL_NUM;
        if (outLevel[i] > 180) tc = 0x0000;
        else tc = COL_NUM;
        tft.setTextColor(tc);
        tft.setFont(&fonts::Font2);
        tft.drawNumber(i + 1, x, y);
    }
}
