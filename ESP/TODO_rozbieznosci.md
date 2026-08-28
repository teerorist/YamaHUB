# TODO — rozbieżności ESP ↔ Andro

Lista z przeglądu firmware vs apki. Odhaczone = zrobione.

---

## Konfiguracja IN/OUT

- [ ] ESP ma twarde defaulty: IN_1 = kierunek L, IN_5 = kierunek P, IN_10 = STARTER → OUT_10; czysty firmware nie powinien nic przypisywać
- [ ] Andro `defaultSlots()` startuje z L/P, LIGHTS (OUT 3+7), BRAKE, NEUTRAL, STARTER i BUTTON-ami
- [ ] `normalizeSlots()` dopisuje brakujące L / P / LIGHTS / BRAKE / NEUTRAL / STARTER
- [ ] `validate()` blokuje zapis bez dokładnie tych funkcji
- [ ] `isFixed()` nie pozwala zdjąć L / P / NEUTRAL / STARTER (ani pierwszego LIGHTS / BRAKE)
- [ ] ControlScreen i Dashboard: brak IN_LEFT/IN_RIGHT → fallback `leftOut=1`, `rightOut=5` (animacja i „hazard” na OUT_01/05)

## Protokół BLE

- [x] `CFG:` = 4 pola (`fade,N,curve,acSpeed`); światła używają tego samego fade/curve co kierunki (bez `beamFade`)
- [x] N (mrugnięcia): ESP i Andro 2–6
- [x] `curve`: Andro nie pokazuje chipa / nie wysyła SET_CFG zanim przyjdzie `CFG:` z huba; parser fallback = 1 (jak ESP)
- [x] `acSpeed` 5–30 km/h + checkboxy: autowyłączenie kierunkowskazu, autowłączenie świateł (`CFG` 6 pól)
- [ ] ESP umie `LOG:` — Andro ignoruje
- [ ] Andro nasłuchuje `RPM:` — ESP nigdy nie wysyła
- [ ] `SHUTDOWN_NOW` z apki woła na ESP `requestShutdown()` = odliczanie **10 s**; `requestShutdownNow()` w ESP nieużywane; UI mówi „wyłącz natychmiast”
- [ ] HAZARD: ESP ma `HAZARD:0/1`; Control zgaduje hazard jako „lewy i prawy bit ON”
- [ ] `IN10:` to wirtualny starter (działa tylko gdy jakiś IN = STARTER); Andro zawsze `IN10:`, komentarz „jak fizyczny IN_10”

## LIGHTS / HI BEAM

- [x] LIGHTS: logika na ESP (short LOW, long HI, LOW off → HI off); Andro tylko `IN:n:1/0`; HI z `LIGHTS_H{n}` też PWM

## Starter i Neutral

- [ ] Fizyczny starter = IN z trybem STARTER; apka zawsze `IN10:`
- [ ] Bez STARTER w INCFG `handleStarter()` nie wchodzi → START na Dashboard nic nie robi na hubie
- [ ] Start w apce zależy od `DashboardTestState.neutral` (przełącznik testowy), nie od prawdziwego OUT Neutral (`neutralOn`)
- [ ] Na luzie z motocykla START nadal pokazuje `N?`
- [ ] `ControlStarter.canStart()` to ten sam testowy flag, nie stan BLE

## SENSOR / MOMENT

- [ ] ESP SENSOR = IN steruje swoim `outIndex`
- [ ] Andro SENSOR nie ma pickera OUT (`outNum = 0`); przy zapisie `coerceIn(1,10)` → zawsze OUT_01
- [ ] LCD ESP: SENSOR nie liczy się jako użyte wyjście
- [ ] `IN_MOMENT` i `IN_SENSOR` na ESP robią to samo (pressed=ON, released=OFF)

## Uprawnienia / BLE scan / GPS

- [ ] Manifest: BLE + lokalizacja; runtime Andro 12+ prosi tylko o powiadomienia
- [ ] Skan BLE (`MissingPermission`) i GPS mogą milczeć bez `BLUETOOTH_SCAN` / `CONNECT` / `ACCESS_FINE_LOCATION`
- [ ] Skaner nie filtruje nazwy `YamaHub` ani UUID `FFE0` — lista to wszystkie BLE w okolicy

## Gesty kierunków

- [ ] Apka: od razu `LEFT:1`, po 400 ms `LEFT:2`
- [ ] ESP fizycznie: N od press, promocja NS po 400 ms
- [ ] Przy HAZARD i obu trzymanych apka strzela shortami + `setHazard`, nie tabelą stanów ESP
- [ ] Próg 400 ms hardcoded w apce i ESP; `Prefs.shortPressThresholdMs` nieużywane

## UI / martwy kod Andro

- [ ] Żywa ścieżka: Dashboard + Settings (BLE / Kierunki / Wejścia / Sterowanie)
- [ ] `YamaHubApp.kt` (dolny pasek) nieużywany
- [ ] `HazardRow.kt` nieużywany
- [ ] `Prefs.controlOrder` zapisane, nigdzie nie czytane
- [ ] `ui.theme.YamaHUBTheme` nieużywany; MainActivity składa goły `darkColorScheme()`
- [ ] Po utracie BLE Main jest pusty ~2,5 s, dopiero potem Settings
- [ ] Callbacki BLE (`onConnectionChanged` / `onInputCfg` / …) łańcuchem Service + Root + ekrany — łatwo urwać handler

## Drobniejsze

- [ ] Neutral: Andro zielony, LCD ESP cyjan (zielony = STARTER)
- [ ] ESP `nameIsBeam` (`ight` / `beam` / `hi` / `low`) — BUTTON o nazwie np. `Shift` stanie się PWM
- [ ] Zakładka Kierunki nie mówi, że fade/krzywa dotyczą też świateł
- [ ] ESP `connectionBlink`, `MODE_FADING_OUT`, `armFromApp()` — martwe
- [ ] `IN_MOMENT` vs `IN_SENSOR` — duplikat logiki na ESP

---

Najpierw (jeśli wracać do czystego huba): Input Settings bez wymuszania zestawu, LIGHTS HI jako PWM, znieść fallback L=1/P=5, `IN10` vs konfigurowalny starter.
