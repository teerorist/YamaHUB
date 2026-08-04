#include "display_hub.h"
#include "inputs.h"
#include <LovyanGFX.hpp>
#include <string.h>

// ==================== KONFIGURACJA LCD (z działającego demo) ====================
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
            cfg.pin_bl      = 46;   // wersja B
            cfg.invert      = false;
            cfg.freq        = 44100;
            cfg.pwm_channel = 7;    // nie koliduje z kierunkami (2,3)
            _light.config(cfg);
            _panel.setLight(&_light);
        }
        setPanel(&_panel);
    }
};

static LGFX tft;
uint8_t outLevel[10] = {0};
static uint8_t prevLevel[10] = {255};

static const uint16_t COL_OFF    = 0x18C3;
static const uint16_t COL_ORANGE = 0xFD20;
static const uint16_t COL_GREEN  = 0x07E0;
static const uint16_t COL_BLUE   = 0x001F;
static const uint16_t COL_WHITE  = 0xFFFF;
static const uint16_t COL_RED    = 0xF800;
static const uint16_t COL_DARK   = 0x4208;

static uint16_t colorForOut(int i) {
    if (i == 0 || i == 4) return COL_ORANGE;
    if (i == 9) return COL_GREEN;
    for (int j = 0; j < 9; j++) {
        if ((int)inputCfg[j].outIndex == i) {
            const char* n = inputCfg[j].name;
            if (!n) continue;
            if (strstr(n, "hi") || strstr(n, "Hi") || strstr(n, "HI")) return COL_BLUE;
            if (strstr(n, "low") || strstr(n, "Low") || strstr(n, "mij")) return COL_WHITE;
        }
    }
    return COL_RED;
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
    tft.setRotation(0);          // pion 172×320 pod siatkę 2×5
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
    const int cols = 2, r = 22;
    const int marginX = 28, marginY = 28;
    const int stepX = 116, stepY = 66;

    for (int i = 0; i < 10; i++) {
        if (!force && outLevel[i] == prevLevel[i]) continue;
        prevLevel[i] = outLevel[i];

        int x = marginX + (i % cols) * stepX;
        int y = marginY + (i / cols) * stepY;
        uint16_t c = dimColor(colorForOut(i), outLevel[i]);
        tft.fillCircle(x, y, r, c);
        tft.drawCircle(x, y, r, COL_DARK);
    }
}