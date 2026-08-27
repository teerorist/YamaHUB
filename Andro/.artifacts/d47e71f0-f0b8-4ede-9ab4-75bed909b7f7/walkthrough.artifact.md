# Walkthrough: Rozszerzona symulacja i nowe warstwy (v16.0)

Udoskonaliłem system symulacji jasności otoczenia oraz zaktualizowałem strukturę warstw wskazówki RPM, aby zapewnić maksymalny realizm w każdych warunkach oświetleniowych.

## Kluczowe Zmiany

### 1. Prawdziwa Czerń i Tło
- Główne tło dashboardu ma teraz pod spodem warstwę stałej czerni (`Color.Black`). Przy ustawieniu JASNOŚĆ = 0%, tło `bkg` całkowicie znika, pozostawiając idealnie czarny ekran.

### 2. Nowe Warstwy Wskazówki RPM
- Zastąpiłem pojedyncze tło wskazówki dwoma dedykowanymi plikami:
    - **`needle_bkg_0.png`**: Reaguje na jasność otoczenia (ambient).
    - **`needle_bkg_1.png`**: Reaguje na włączenie świateł mijania (backlight) – zapala się płynnie wraz z tarczami.
- Sama igła (`needle.png`) oraz jej cień również zanikają wraz ze spadkiem jasności otoczenia.

### 3. Profesjonalna Symulacja w Panelu Testowym
- Przebudowałem panel testowy, aby był bardziej czytelny:
    - Suwak **JASNOŚĆ** (Ambient) otrzymał własny, dedykowany wiersz na dole panelu.
    - Dodałem dwa nowe wskaźniki informacyjne wyświetlane obok suwaka:
        - **Ekran: XX%**: Symulacja aktualnej jasności wyświetlacza.
        - **Zewn: XX%**: Symulacja natężenia światła zewnętrznego.

## Weryfikacja
- [x] JASNOŚĆ = 0% + Światła OFF -> Ekran jest całkowicie czarny.
- [x] JASNOŚĆ = 0% + Światła LOW -> Widoczne są tylko podświetlone cyfry oraz igła (jeśli ma podświetlenie w `_1`).
- [x] Cień wskazówki znika płynnie podczas przesuwania suwaka jasności.

> [!CAUTION]
> Upewnij się, że w folderze `res/drawable/` znajdują się pliki: `needle_bkg_0.png` oraz `needle_bkg_1.png`. Poprzedni plik `needle_bkg.png` nie jest już używany.

render_diffs(file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)
render_diffs(file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardTestState.kt)
