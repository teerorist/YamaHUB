# Naprawa kolorów ikon i ujednolicenie rozmiaru powiadomień

Użytkownik zgłosił, że powiadomienia mają niewłaściwe kolory (tło zamiast samej ikony) oraz ikona "rozłączono" ma inny rozmiar niż "połączono". Celem jest, aby tylko ikona w pasku stanu (status bar) zmieniała kolor, a tło powiadomienia pozostało domyślne.

## Proponowane zmiany

### Ikony (Drawables)

#### [MODYFIKACJA] [ic_ble_connected.xml](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/res/drawable/ic_ble_connected.xml) i [ic_ble_disconnected.xml](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/res/drawable/ic_ble_disconnected.xml)
- Usunięcie dodatkowego skalowania z `ic_ble_connected.xml` (`scaleX="1.2266667"`), aby obie ikony miały identyczny rozmiar i pozycjonowanie.
- Usunięcie atrybutu `android:tint` z plików XML, aby pozwolić systemowi na poprawne nakładanie koloru zdefiniowanego w kodzie lub zachowanie koloru zdefiniowanego w ścieżkach (zależnie od wersji Androida).

### Powiadomienia

#### [MODYFIKACJA] [HubNotification.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/HubNotification.kt)
- Usunięcie wywołania `.setColorized(true)`. Ta funkcja powoduje kolorowanie całego tła powiadomienia, czego użytkownik chce uniknąć.
- Pozostawienie `.setColor(color)`. Ta funkcja odpowiada za kolorowanie małej ikony (small icon) w pasku stanu oraz w szufladzie powiadomień (zależnie od wersji systemu).
- Upewnienie się, że `deleteIntent` (mechanizm przywracania po swipe) działa poprawnie.

## Plan weryfikacji

### Testy automatyczne
- Kompilacja projektu: `./gradlew :app:assembleDebug`.

### Weryfikacja ręczna
- **Rozmiar ikon**: Porównanie wizualne ikony połączonej i rozłączonej w pasku stanu – powinny mieć ten sam rozmiar.
- **Kolor ikon**:
    - Sprawdzenie, czy ikona w pasku stanu jest czerwona przy rozłączeniu i biała przy połączeniu.
    - Sprawdzenie, czy tło powiadomienia (po rozwinięciu panelu) jest standardowe (ciemne/systemowe), a nie kolorowe.
- **Swipe-out**: Potwierdzenie, że powiadomienie wraca po usunięciu palcem.

---

### Uwaga techniczna
Na nowszych wersjach Androida (8.0+), system często wymusza biały/monochromatyczny kolor ikon w samym pasku stanu (status bar), a kolor zadany przez `setColor` jest używany tylko w rozwiniętym panelu powiadomień. Jeśli usunięcie `setColorized(true)` nie sprawi, że ikona na górze będzie czerwona, może to być ograniczenie samego systemu operacyjnego. Spróbuję jednak zoptymalizować to tak, aby było to jak najbliższe oczekiwaniom.
