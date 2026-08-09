# YamaHUB

Firmware ESP32-S3 (`ESP/`) + aplikacja Android (`Andro/`) – Yamaha XJ650 hub.

## ESP (PlatformIO)

```bash
cd ESP
pio run -t upload
pio device monitor
```

Moduły w `ESP/src/`:
- **INPUTS:** `input_cfg`, `input_router`, `input_button`, `input_moment`, `input_sensor`, `blinkers`, `beams`, `starter`
- **BLE:** `ble_hub`, `ble_protocol`
- **UI LCD:** `display_hub`
- **CORE:** `main`, `config`, `arming`

Stary monolityczny `inputs.cpp` usunięty – zastąpiony powyższymi. `inputs.h` to facade.

## Android (Android Studio)

Otwórz folder `Andro/`. Pakiet `com.yamahub.app` / `.ui` – pliki podzielone, te same nazwy pakietów co wcześniej.

## Podmiana repo

1. Scommit / backup obecnego stanu.
2. Zastąp zawartość `ESP/` i `Andro/` tym archiwum (albo cały root).
3. `pio run` + Sync Gradle.
