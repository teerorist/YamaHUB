# Zadania: Rozszerzona symulacja i nowe warstwy (v16.0)

- [x] Aktualizacja `DashboardTestState.kt`:
    - [x] Dodanie pól `actualScreenBrightness` i `externalLightIntensity`.
- [x] Aktualizacja `DashboardScreen.kt`:
    - [x] Dodanie `background(Color.Black)` do głównego kontenera.
    - [x] Ładowanie zasobów `needle_bkg_0` i `needle_bkg_1`.
    - [x] Implementacja zanikania cienia wskazówki (`alpha = ambientBrightness * 0.3f`).
    - [x] Zastosowanie alfy dla `needle_bkg_0` (ambient) i `needle_bkg_1` (podświetlenie).
    - [x] Przebudowa panelu testowego (nowy wiersz dla jasności i statusy %).
- [x] Weryfikacja wizualna.
