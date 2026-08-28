# Plan: Naprawa rzeczywistych wskaźników jasności (v18.2)

Zintegrujemy dane z fizycznych czujników telefonu (światło, jasność ekranu) z wyświetlanymi wskaźnikami w panelu testowym, upewniając się, że wartości odświeżają się w czasie rzeczywistym.

## Proposed Changes

### UI Component (`DashboardScreen.kt`)

#### [MODIFY] [DashboardScreen.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)
- **Problem**: Pola tekstowe "Ekran" i "Zewn" wyświetlają wartości z `DashboardTestState`, ale te pola nie były dotąd aktualizowane przez logikę czujników wewnątrz `DashboardScreen`.
- **Rozwiązanie**:
    - Wewnątrz istniejącego `SensorEventListener` (dla `Sensor.TYPE_LIGHT`):
        - Bezpośrednia aktualizacja `DashboardTestState.externalLightIntensity`.
    - Wewnątrz istniejącej pętli `LaunchedEffect` (monitorującej jasność ekranu):
        - Bezpośrednia aktualizacja `DashboardTestState.actualScreenBrightness`.
- **Uwaga**: Logika sterowania wizualną alfą dashboardu pozostaje powiązana z `actualScreenBrightness`, co jest poprawne.

## Verification Plan

### Manual Verification
1. **Test Czujnika**: Zasłoń ręką czujnik światła w telefonie -> wskaźnik "Zewn: XX%" w panelu testowym musi natychmiast pokazać spadek wartości.
2. **Test Jasności**: Zmień jasność ekranu suwakiem systemowym Androida (lub suwakiem SYM. JASNOŚĆ w aplikacji) -> wskaźnik "Ekran: XX%" musi odzwierciedlać nową, rzeczywistą wartość podświetlenia.
