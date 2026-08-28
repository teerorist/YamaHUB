# Walkthrough: Ciągłe drżenie kapsla (v19.1)

Zaktualizowałem efekt wizualny kapsla obrotomierza, aby bardziej realistycznie oddawał drgania mechaniczne podczas pracy zegara.

## Zmiany

### 1. Detekcja aktywnego ruchu (`DashboardScreen.kt`)
- Wprowadziłem stan `isMoving`, który jest aktywowany przy każdej zmianie kąta wskazówki.
- Zastosowałem mechanizm "timeout" (100ms) – jeśli przez ten czas nie spłynie nowa wartość RPM, uznajemy, że wskazówka się zatrzymała i wyłączamy drżenie.

### 2. Pętla Jittera (Drgań)
- Zaimplementowałem pętlę o wysokiej częstotliwości (30ms), która generuje losowe wychylenia kapsla w zakresie **-1.5 do 1.5 stopnia**.
- Drżenie jest aktywne **wyłącznie podczas ruchu** wskazówki.
- Po zatrzymaniu wskazówki, kapsel płynnie wraca do pozycji centralnej (zero) w ciągu 100ms.

## Weryfikacja
- [x] Płynne przesuwanie suwaka RPM powoduje ciągłą, drobną wibrację srebrnego kapsla.
- [x] Puszczenie suwaka lub jego zatrzymanie natychmiast wycisza wibracje.
- [x] Efekt jest znacznie bardziej dynamiczny i lepiej oddaje charakterystykę analogowego wskaźnika.

render_diffs(file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)
