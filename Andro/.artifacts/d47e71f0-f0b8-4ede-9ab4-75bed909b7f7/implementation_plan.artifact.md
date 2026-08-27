# Plan: Rozszerzona symulacja i nowe warstwy (v17.0)

Udoskonalimy system symulacji jasności otoczenia, wprowadzimy nowe warstwy dla tła wskazówki RPM oraz przebudujemy panel testowy dla lepszej czytelności i funkcjonalności.

## User Review Required

> [!IMPORTANT]
> - **Zasoby**: Kod będzie teraz oczekiwał plików `needle_bkg_0.png` oraz `needle_bkg_1.png`. Plik `needle_bkg_0` będzie widoczny zawsze na 100%.
> - **Cień**: Cień wskazówki będzie teraz zanikał wraz z jasnością otoczenia (`alpha * ambientBrightness`).

## Proposed Changes

### 1. Rozszerzenie Stanu Testowego (`DashboardTestState.kt`)

#### [MODIFY] [DashboardTestState.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardTestState.kt)
- Dodanie pól informacyjnych: `actualScreenBrightness` oraz `externalLightIntensity`.

### 2. Aktualizacja UI Dashboardu (`DashboardScreen.kt`)

#### [MODIFY] [DashboardScreen.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)
- **Tło Root**: Ustawienie `background(Color.Black)` pod warstwą powielanego tła `bkg`.
- **Ładowanie Zasobów**: Zmiana `needleBkgPainter` na `needleBkg0` i `needleBkg1`.
- **Logika SmithsGauge**:
    - `needlePainter` (igła): alpha = `ambientBrightness`.
    - `needleBkg0`: alpha = **1.0f** (nie zanika).
    - `needleBkg1`: alpha = `backlightAlpha` (płynne podświetlenie).
    - **Cień**: Alpha cienia mnożona przez `ambientBrightness`.
    - **Ikony**: Wszystkie wersje '0' (wyłączone) będą miały `alpha = ambientBrightness`.
- **Panel Testowy**:
    - Przeniesienie suwaka **JASNOŚĆ** do nowego, czwartego wiersza.
    - Dodanie obok suwaka pól tekstowych wyświetlających `actualScreenBrightness` oraz `externalLightIntensity`.

## Verification Plan

### Manual Verification
- Sprawdzenie, czy przy JASNOŚĆ = 0% tło `bkg` znika i zostaje czarny ekran.
- Weryfikacja, czy `needle_bkg_0` pozostaje widoczny przy JASNOŚĆ = 0%.
- Test zanikania wyłączonych ikon kontrolek.
- Sprawdzenie nowego układu panelu testowego.
