# Naprawa koloru ikony na pasku stanu i ujednolicenie rozmiaru

Użytkownik zgłosił, że ikona na pasku stanu (status bar) pozostaje biała, a jej rozmiar zmienia się przy przełączaniu stanu. Celem jest wymuszenie czerwonego koloru na pasku stanu dla stanu "rozłączono" oraz zapewnienie identycznego rozmiaru obu ikon.

## Proponowane zmiany

### Ikony (Drawables)

#### [MODYFIKACJA] [ic_ble_connected.xml](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/res/drawable/ic_ble_connected.xml) i [ic_ble_disconnected.xml](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/res/drawable/ic_ble_disconnected.xml)
- Uproszczenie struktury XML – usunięcie grup skalujących (`group`), które mogą powodować błędy w zaokrągleniach pozycji i rozmiaru.
- Ustawienie `fillColor="#FFFFFF"` w obu plikach (standard dla ikon-szablonów).
- Upewnienie się, że `viewportWidth/Height` oraz parametry ścieżek są identyczne.

### Powiadomienia

#### [MODYFIKACJA] [HubNotification.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/HubNotification.kt)
- **Przywrócenie `setColor(color)`**: To kluczowe, aby system wiedział, na jaki kolor ma zafarbować ikonę w pasku stanu.
- **Zmiana `Importance`**: Zwiększenie ważności kanału na `IMPORTANCE_DEFAULT` przy jednoczesnym wyłączeniu dźwięku i wibracji. Wyższa ważność często odblokowuje kolorowanie ikony na pasku stanu w niektórych wersjach Androida.
- **Usunięcie `setColorized(true)`**: Pozostawienie domyślnego tła panelu powiadomień.

## Plan weryfikacji

### Testy automatyczne
- Kompilacja projektu: `./gradlew :app:assembleDebug`.

### Weryfikacja ręczna
- Sprawdzenie, czy ikona na pasku stanu zmienia kolor na czerwony przy rozłączeniu.
- Sprawdzenie, czy ikona nie zmienia rozmiaru ani pozycji (nie "skacze") przy zmianie stanu.
- Potwierdzenie, że tło powiadomienia w rozwiniętym panelu jest standardowe (ciemne), a nie kolorowe.
