# YamaHUB ESP – mapa modułów

## GR I – INPUTS
| Plik | Odpowiedzialność |
|------|------------------|
| `input_cfg.h/cpp` | NVS, inputCfg[10], setInputCfg, starterOutIndex |
| `input_router.h/cpp` | Rozdział trybów → handlery |
| `input_button.h/cpp` | IN_TOGGLE (BUTTON / LIGHTS toggle) |
| `input_moment.h/cpp` | IN_MOMENT (BRAKE, NEUTRAL) |
| `input_sensor.h/cpp` | IN_SENSOR |
| `blinkers.h/cpp` | KIERUNEK L/P, HAZARD, fade, N/NS |
| `beams.h/cpp` | LOW/HI BEAM + fade |
| `starter.h/cpp` | STARTER long/short, killswitch, deep sleep |

## GR II – REMOTE
Sterowanie zdalne idzie wyłącznie przez BLE komendy (`ble_protocol`).

## GR III – DASHBOARD
| Plik | Odpowiedzialność |
|------|------------------|
| `display_hub.h/cpp` | LCD kropki OUT_01..10 |

## GR IV – BLE
| Plik | Odpowiedzialność |
|------|------------------|
| `ble_hub.h/cpp` | NimBLE setup, kolejka, connect/disconnect |
| `ble_protocol.h/cpp` | sendState/CFG/INCFG, handleBleCommand |

## CORE
| Plik | Odpowiedzialność |
|------|------------------|
| `main.cpp` | setup/loop – tylko orkiestracja |
| `config.h/cpp` | fade, blinkCount, curve, acSpeed |
| `arming.h/cpp` | hubArmed / tryArm |
| `include/pins.h`, `Button.h`, `Output.h` | hardware |

## Zasada
Zmiana logiki **kierunków** → tylko `blinkers.*`.  
Zmiana **startera** → tylko `starter.*`.  
Zmiana protokołu BLE → tylko `ble_protocol.*`.  
`main.cpp` nie zawiera logiki domenowej.
