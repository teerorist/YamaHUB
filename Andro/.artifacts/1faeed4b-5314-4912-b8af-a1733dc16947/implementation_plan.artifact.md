# Restoration Plan - Strict Lights & Brakes Logic (v6.24)

Celem jest naprawa błędów w edytorze świateł i hamulców oraz wyeliminowanie duplikacji w menu "Funkcja". Hub (ESP) staje się jedynym sędzią decydującym o tym, co jest widoczne i jak się nazywa.

## User Review Required

> [!IMPORTANT]
> **Zasady Menu (Firmware-Driven)**:
> - Jeśli w systemie są już **2 światła**, opcja "LIGHTS" (oraz "LOW BEAM") całkowicie znika z menu dla pozostałych slotów.
> - Jeśli w systemie są już **2 hamulce**, opcja "BRAKE" znika z menu.
> - **Logika 1x LIGHTS**: Slot nazywa się "LIGHTS" i ma dwa pola OUT (HI/LOW).
> - **Logika 2x LIGHTS**: Slot 1 = "HI BEAM" (1x OUT), Slot 2 = "LOW BEAM" (1x OUT). Drugi slot przejmuje port, który był portem LOW w trybie 1x.

## Proposed Changes

### [ESP] Firmware Updates (Mózg)

#### [MODIFY] [ble_protocol.cpp](file:///D:/###Users/teerorist/Desktop/YamaHUB/ESP/src/ble_protocol.cpp)
- **Czyste Menu (`sendModesV4`)**: Całkowite usunięcie duplikatów. Hub wyśle listę unikalnych funkcji.
- **Dynamiczne Flagi**: Implementacja rygorystycznego sprawdzania limitów (max 2 dla świateł i hamulców) bezpośrednio przed wysłaniem listy do telefonu.

#### [MODIFY] [input_cfg.cpp](file:///D:/###Users/teerorist/Desktop/YamaHUB/ESP/src/input_cfg.cpp)
- **Refinement `syncLightsSettings`**:
    - Gwarancja, że tryb 1x zawsze posiada przypisany port `outSecondary` (aby Android wiedział, że ma pokazać dwa suwaki).
    - Poprawne przełączanie nazw ("LIGHTS" vs "HI BEAM" / "LOW BEAM") w zależności od liczby slotów.
- **Refinement `syncBrakeSettings`**: Upewnienie się, że nazwy "FRONT/REAR BRAKE" są nadawane tylko gdy są 2 sloty, inaczej zostaje "BRAKES".

### [Android] UI Refinement (Lustro)

#### [MODIFY] [InputSlotEditor.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/InputSlotEditor.kt)
- **Fix Height**: Utrzymanie wysokości 46.dp dla wszystkich elementów (Typ, Funkcja, Nazwa, OUT).
- **Zasada Pasywności**: Edytor będzie polegał wyłącznie na polu `outSecondary` przesłanym z Huba, aby zdecydować o liczbie wyświetlanych pól OUT.

## Verification Plan

### Manual Verification
1.  **1x LIGHTS**: Ustaw jeden slot jako LIGHTS. Pasek = "LIGHTS", Edytor = 2x OUT.
2.  **2x LIGHTS**: Dodaj drugi slot jako LOW BEAM (LIGHTS 2). Pasek 1 = "HI BEAM", Pasek 2 = "LOW BEAM". Edytor dla każdego = 1x OUT.
3.  **Menu Limit**: Po dodaniu drugiego światła, opcja dodania kolejnego musi zniknąć z menu innych slotów.

**Czy ten poprawiony plan rygorystycznej logiki ESP jest gotowy do wdrożenia?**
