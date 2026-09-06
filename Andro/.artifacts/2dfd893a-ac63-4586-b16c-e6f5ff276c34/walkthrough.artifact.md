# Walkthrough - Przywrócenie "Złotej Wersji" (v4.9)

Pomyślnie przywrócono stabilną logikę systemu YamaHUB, łącząc najlepsze cechy wizualne (Dashboard Smiths 3D, Jitter) z dopracowaną logiką sterowania (One-Button Mode, Interlock startera).

## Zrealizowane poprawki

### 1. Logika Świateł (One-Button / Scenario I & II)
- **Implementacja w `beams.cpp`**:
    - **Scenario I (2 wejścia)**: LOW ma dedykowany przycisk (toggle). HI obsługuje krótkie mignięcie (Pass) oraz długie zatrzaśnięcie (Latch), ale Latch działa tylko gdy LOW jest włączone.
    - **Scenario II (1 wejście)**: Przycisk steruje głównie światłami HI (Pass/Latch).
    - **Killswitch**: Bardzo długie przytrzymanie (>5s) gasi wszystkie światła.
- **Hierarchia**: Zgodnie z zasadą v4.9, pierwszy napotkany port LIGHTS w konfiguracji to **HI**, a drugi (lub tagowany `_L`) to **LOW**.

### 2. Animacja Powitalna (v4.8)
- **Przebieg**: Płynny sweep obrotomierza (0-12k RPM) zsynchronizowany z kontrolkami:
    - **1k**: Neutral
    - **3k**: Low Beam
    - **6k**: Oil
    - **9k**: Hi Beam
    - **11k**: Fuel
    - **12k**: Hazard (flash na szczycie).
- **Stabilność**: Dodano 2-sekundowe opóźnienie po połączeniu BLE przed startem animacji.

### 3. Zachowane Funkcje
- **Smiths 3D**: Pełna geometria i cieniowanie tarczy licznika.
- **Jitter (Drżenie)**: Wskazówka i kapsel drżą podczas aktywnego ruchu (zmiany obrotów).
- **Interlock startera**: Starter wyłącza się automatycznie, jeśli Neutral lub Clutch zostaną puszczone.
- **LCD 20 Kropek**: Wyświetlacz ESP32 pokazuje pełny stan wejść (lewa) i wyjść (prawa).

## Rezultaty weryfikacji
- [x] Kompilacja ESP32: **SUCCESS**
- [x] Wdrożenie Android: **SUCCESS**
- [x] Logika świateł: **Przywrócona do v4.6/4.9**
- [x] Animacja startowa: **Zsynchronizowana wg v4.8**
