#include "display_hub.h"
#include "inputs.h"
#include "blinkers.h"
#include "pins.h"
#include <LovyanGFX.hpp>

class LGFX : public lgfx::LGFX_Device {
    lgfx::Panel_ST7789 _panel;
    lgfx::Bus_SPI _bus;
    lgfx::Light_PWM _light;
public:
    LGFX(void) {
        {
            auto cfg = _bus.config();
            cfg.spi_host = SPI2_HOST;
            cfg.spi_mode = 0;
            cfg.freq_write = 80000000;
            cfg.freq_read = 16000000;
            cfg.spi_3wire = true;
            cfg.use_lock = true;
            cfg.dma_channel = SPI_DMA_CH_AUTO;
            cfg.pin_sclk = LCD_SCLK;
            cfg.pin_mosi = LCD_MOSI;
            cfg.pin_miso = -1;
            cfg.pin_dc = LCD_DC;
            _bus.config(cfg);
            _panel.setBus(&_bus);
        }
        {
            auto cfg = _panel.config();
            cfg.pin_cs = LCD_CS;
            cfg.pin_rst = LCD_RST;
            cfg.pin_busy = -1;
            cfg.panel_width = 172;
            cfg.panel_height = 320;
            cfg.offset_x = 34;
            cfg.offset_y = 0;
            cfg.offset_rotation = 0;
            cfg.dummy_read_pixel = 8;
            cfg.dummy_read_bits = 1;
            cfg.readable = false;
            cfg.invert = true;
            cfg.rgb_order = false;
            cfg.dlen_16bit = false;
            cfg.bus_shared = false;
            _panel.config(cfg);
        }
        {
            auto cfg = _light.config();
            cfg.pin_bl = LCD_BL;
            cfg.invert = false;
            cfg.freq = 44100;
            cfg.pwm_channel = 7;
            _light.config(cfg);
            _panel.setLight(&_light);
        }
        setPanel(&_panel);
    }
};

static LGFX tft;
uint8_t outLevel[10] = {0};
extern int currentRpm;
extern int canFuelPct;
static uint8_t prevLevel[20] = {255};
static bool prevUsed[20] = {false};
static int prevSpeedShown = -1;

static const uint16_t COL_OFF = 0x18C3;
static const uint16_t COL_ORANGE = 0xFD20;
static const uint16_t COL_GREEN = 0x07E0;
static const uint16_t COL_WHITE = 0xFFFF;
static const uint16_t COL_BLUE = 0x05BF;
static const uint16_t COL_RED = 0xF800;
static const uint16_t COL_CYAN = 0x07FF;
static const uint16_t COL_DARK = 0x4208;
static const uint16_t COL_X = 0x8410;
static const uint16_t COL_NUM = 0xC618;

static void drawPortCircle(int x, int y, int portNo, bool used,
                           uint16_t fillColor, uint16_t textColor,
                           bool crossed) {
    const int r = 13;
    tft.fillCircle(x, y, r, fillColor);
    tft.drawCircle(x, y, r, COL_DARK);
    tft.setTextDatum(MC_DATUM);
    tft.setTextColor(textColor);
    tft.setFont(&fonts::Font2);
    tft.drawNumber(portNo + 1, x, y - 1);
    if (crossed) {
        tft.drawLine(x - 8, y - 8, x + 8, y + 8, COL_WHITE);
        tft.drawLine(x + 8, y - 8, x - 8, y + 8, COL_WHITE);
    }
}

static bool outIsUsed(int oi) {
    for (int j = 0; j < INPUT_COUNT; j++) {
        if (inputCfg[j].functionId == FN_NONE) continue;
        if (outAssigned(inputCfg[j].outPrimary) && (int)inputCfg[j].outPrimary == oi)
            return true;
        if (outAssigned(inputCfg[j].outSecondary) && (int)inputCfg[j].outSecondary == oi)
            return true;
    }
    return false;
}

static uint16_t colorForOut(int oi) {
    if (oi == blinkerLeftOutIndex() || oi == blinkerRightOutIndex())
        return COL_ORANGE;

    for (int j = 0; j < INPUT_COUNT; j++) {
        if ((int)inputCfg[j].outPrimary != oi &&
            (int)inputCfg[j].outSecondary != oi) continue;
        uint8_t f = inputCfg[j].functionId;
        if (f == FN_NEUTRAL || f == FN_STARTER) return COL_GREEN;
        if (f == FN_BRAKE_1 || f == FN_BRAKE_2) return COL_RED;
        if (f == FN_LIGHTS_1 && (int)inputCfg[j].outPrimary == oi) return COL_BLUE;
        if (f == FN_LIGHTS_1 && (int)inputCfg[j].outSecondary == oi) return COL_WHITE;
        if (f == FN_LIGHTS_2) return COL_WHITE;
        if (f == FN_OIL || f == FN_FUEL) return COL_RED;
        return COL_CYAN;
    }
    return COL_CYAN;
}

static uint16_t dimColor(uint16_t c, uint8_t level) {
    if (level == 0) return COL_OFF;
    if (level >= 250) return c;
    uint8_t r = ((c >> 11) & 0x1F) * level / 255;
    uint8_t g = ((c >> 5) & 0x3F) * level / 255;
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
    const int inputX = 16;
    const int outputX = 172 - 3 - 13;
    const int startY = 15;
    const int stepY = 26;

    for (int i = 0; i < 10; i++) {
        const int y = startY + i * stepY;

        // Pokaż wszystkie wejścia (nie skreślaj ich, jeśli są DISABLED w configu, dla lepszej diagnostyki)
        bool inputActive = getEffectiveInputState(i);
        bool inputChanged = force || (inputActive ? 1 : 0) != prevLevel[i];

        if (inputChanged) {
            prevLevel[i] = inputActive ? 1 : 0;
            drawPortCircle(inputX, y, i, true,
                           inputActive ? COL_NUM : COL_OFF,
                           inputActive ? 0x0000 : COL_WHITE,
                           false);
        }

        bool used = outIsUsed(i);
        uint8_t level = outLevel[i];
        int neutralIndex = findNeutralInIndex();
        if (neutralIndex >= 0 && (int)inputCfg[neutralIndex].outPrimary == i) {
            bool neutralOn = isNeutralSimulation() || outLevel[i] > 20;
            level = neutralOn ? 255 : 0;
        }
        if (!force && level == prevLevel[i + 10] && used == prevUsed[i + 10]) continue;
        prevLevel[i + 10] = level;
        prevUsed[i + 10] = used;

        if (!used) {
            drawPortCircle(outputX, y, i, false, COL_OFF, COL_WHITE, true);
            continue;
        }

        uint16_t c = dimColor(colorForOut(i), level);
        drawPortCircle(outputX, y, i, true, c, COL_WHITE, false);
    }

    // prędkość między OUT 9 i 10 (ostatni rząd)
    int sp = (int)(currentSpeedKmh() + 0.5f);
    if (sp < 0) sp = 0;
    if (force || sp != prevSpeedShown) {
        prevSpeedShown = sp;
        const int x9 = 28;
        const int x10 = 144;
        const int y = 20 + 4 * 58;
        const int cx = (x9 + x10) / 2;
        tft.fillRect(cx - 34, y - 16, 68, 36, 0x0000);
        tft.setTextDatum(MC_DATUM);
        tft.setTextColor(COL_WHITE);
        tft.setFont(&fonts::Font4);
        tft.drawNumber(sp, cx, y - 4);
        tft.setTextColor(COL_NUM);
        tft.setFont(&fonts::Font2);
        tft.drawString("km/h", cx, y + 14);
    }
}
