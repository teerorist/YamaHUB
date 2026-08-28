# Zadania: Graficzna wskazówka paliwa (v20.0)

- [ ] Aktualizacja `DashboardScreen.kt`:
    - [ ] Ładowanie zasobów `fuel_needle` i `fuel_needle_bkg`.
    - [ ] Dodanie warstw wskazówki paliwa do kontenera `Box` w `SmithsGauge`.
    - [ ] Implementacja rotacji `(fuelLevel - 0.5f) * 52f` z punktem obrotu na **73.5%** wysokości.
    - [ ] Powiązanie alfy `fuel_needle` z `ambientBrightness`.
    - [ ] Usunięcie starej funkcji `drawFuelSubGauge`.
- [ ] Weryfikacja ruchu wskazówki suwakiem "PALIWO".
