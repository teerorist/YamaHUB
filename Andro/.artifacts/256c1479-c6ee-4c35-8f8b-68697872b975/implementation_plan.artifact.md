# Refinement of Smiths Gauge 3D Geometry

Udoskonalenie geometrii i głębi 3D licznika Smiths, aby wierniej oddać strukturę fizycznego urządzenia (wielopoziomowa tarcza, precyzyjne rozmieszczenie elementów).

## User Review Required

> [!IMPORTANT]
> Zmienię sposób rysowania wskaźnika paliwa z prostokątnego okienka na wycinek pierścienia kołowego (sectoral recessed window), którego krawędzie boczne będą biec wzdłuż promieni koła.

## Proposed Changes

### Dashboard UI Components

#### [MODIFY] [DashboardScreen.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)

1.  **RPM Scale Adjustment**:
    *   Zwiększę promień rysowania cyfr (z `radius - 45.dp` na około `radius - 28.dp`), aby "dosunąć" je do podziałki.
2.  **Inner Recessed Dial**:
    *   Dodam rysowanie mniejszego, centralnego koła (promień ok. 60-70% głównej tarczy).
    *   Zastosuję gradient radialny i wewnętrzny cień (`setShadowLayer`), aby uzyskać efekt zagłębienia.
    *   Przeniosę ikony kierunkowskazów i świateł do wnętrza tego koła.
3.  **New Fuel Gauge Geometry**:
    *   Zamiast `fuelRect`, użyję `Path` budowanej z dwóch łuków (`arcTo`) i linii łączących ich końce na promieniach.
    *   Okienko będzie miało ten sam styl cieniowania co centralny "talerz".
    *   Krawędzie boczne będą celować dokładnie w środek osi licznika.

## Verification Plan

### Automated Tests
- Build projektu: `./gradlew :app:compileDebugKotlin`.

### Manual Verification
- Wizualna ocena "wypchnięcia" skali na zewnątrz (cyfry blisko kresek).
- Sprawdzenie spójności głębi 3D między środkowym kołem a wnęką paliwa.
- Potwierdzenie, że kontrolki są zgrupowane w centralnym zagłębieniu.
