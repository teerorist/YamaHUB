# Precyzyjne przywrócenie "Złotej Wersji" (v21.0)

Bazując na plikach `/kontekst/` oraz Twoich uwagach, przygotowuję plan wycofania błędnych zmian i stabilizacji funkcji, które powinny działać. Nie będziemy robić "hurtowego" przywracania plików, lecz poprawimy logikę krok po kroku.

## User Review Required

> [!IMPORTANT]
> **Kluczowe decyzje:**
> - **Światła**: Wracamy do logiki **"One-Button Mode" (v4.6)** opisanej w `part3.md`. Rezygnujemy z dzisiejszej "Zasady I i II" na rzecz jednego przycisku z obsługą mignięcia (Pass), zatrzasku (Latch) i Killswitcha.
> - **Animacja**: Dopracujemy sekwencję powitalną, aby dokładnie odpowiadała wersji v4.8 (Sweep 0-12k, kolejność ikon: Neutral -> Low -> Oil -> Hi -> Fuel -> Hazard).
> - **Zasoby**: Zachowujemy **Smiths 3D**, **Jitter** (drżenie) oraz **Interlock** startera, ponieważ potwierdziłeś, że te elementy były częścią stabilnej wersji.
> - **CAN**: Zostawiamy szkielet logiki CAN (nawet jeśli nie działa idealnie), aby nie cofać się w rozwoju sprzętowym.

## Proponowane Zmiany

### 1. ESP32 - Logika Świateł (`beams.cpp`)
- **[REVERT]** Usunięcie "Zasady I i II" (rozróżnianie układów pinów).
- **[RESTORE]** Implementacja **One-Button Mode**:
    - Pierwsze wciśnięcie: LOW ON.
    - Krótkie (<400ms): HI PASS (świeci tylko gdy trzymasz).
    - Długie (400ms-2s): HI LATCH (przełącza stałe drogowe).
    - Bardzo długie (>5s): ALL OFF (Killswitch świateł).

### 2. ESP32 - Animacja Powitalna (`startup_anim.cpp`)
- **[REFINE]** Synchronizacja triggerów RPM z ikonami:
    - 1k: Neutral
    - 3k: Low Beam
    - 6k: Oil
    - 9k: Hi Beam
    - 11k: Fuel (Reserve)
    - 12k: Hazard Flash

### 3. ESP32 - Core (`main.cpp`, `starter.cpp`, `input_cfg.cpp`)
- **[KEEP]** Układ 20 kropek na LCD.
- **[KEEP]** Blokada startera (Interlock) wymagająca Neutral/Clutch.
- **[CLEANUP]** Usunięcie dzisiejszych "eksperymentów" z automatycznym zapalaniem LOW przy braku drugiego wejścia.

### 4. Android - UI (`DashboardScreen.kt`, `InputSettingsTab.kt`)
- **[KEEP]** Geometria 3D i efekt drżenia kapsla (Jitter).
- **[KEEP]** Loader "Pobieranie ustawień z HUBa..." (potwierdziłeś, że to pożądana wersja).
- **[FIX]** Upewnienie się, że przyciski w Dashboard/Test poprawnie symulują wejścia dla logiki One-Button.

## Plan Weryfikacji
1. **Test Świateł**: Sprawdzenie na fizycznym przycisku (lub teście), czy krótkie kliknięcie "miga" drogowymi, a długie zapala je na stałe.
2. **Test Animacji**: Re-connect BLE i weryfikacja płynności ruchu wskazówki i zapalania ikon.
3. **Test Interlock**: Próba startu bez aktywnego Neutrala.

**Czy taka interpretacja "Złotej Wersji" (v4.9) jest poprawna i mogę zacząć wprowadzać te poprawki?**
