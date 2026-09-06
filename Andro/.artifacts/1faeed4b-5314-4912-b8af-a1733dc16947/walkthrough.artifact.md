# Walkthrough - Strict Logic Restoration (v6.24)

Wdrożono rygorystyczną logikę zarządzania funkcjami systemowymi (Światła i Hamulce) sterowaną bezpośrednio przez firmware ESP. Aplikacja Android działa teraz jako pasywne lustro stanów Huba.

## Zmiany w Firmware (ESP)

1.  **Czyste i Dynamiczne Menu (`ble_protocol.cpp`)**:
    - Przebudowano funkcję `sendModesV4`. Teraz Hub wysyła listę definicji w sposób strumieniowy, co eliminuje błędy ucinania danych (MTU).
    - Dodano dynamiczne flagi limitów: Jeśli w systemie są już dwa światła lub dwa hamulce, Hub wysyła `flag=0`, co powoduje ich automatyczne zniknięcie z menu wyboru w aplikacji.

2.  **Inteligentna Synchronizacja (`input_cfg.cpp`)**:
    - **Hamulce**: Przywrócono logikę "FRONT/REAR BRAKE" z automatyczną synchronizacją portu OUT.
    - **Światła**: Gwarancja trybu DUAL. Jeśli masz tylko jeden slot świateł, Hub wymusza przypisanie portu dla świateł mijania (`outSecondary`), co Android interpretuje jako polecenie wyświetlenia dwóch pól OUT.
    - **Auto-Naming**: Hub rygorystycznie resetuje nazwy na angielskie domyślne przy każdej zmianie funkcji.

## Zmiany w Aplikacji (Android)

1.  **Pasywny Edytor (`InputSlotEditor.kt`)**:
    - Całkowicie usunięto lokalną logikę list funkcji. Dropdown "Funkcja" buduje się wyłącznie na podstawie danych z Huba.
    - Edytor stał się w 100% pasywny – liczba wyświetlanych pól OUT zależy od parametru `outSecondary` przysłanego przez ESP.
    - Wymuszono wysokość **46.dp** dla wszystkich ramek, aby zachować idealną spójność wizualną z kafelkami portów.

2.  **Agresywny Fallback Nazw (`InputModels.kt`)**:
    - Poprawiono funkcję `title()`. Aplikacja teraz bezbłędnie ignoruje nazwy techniczne typu "IN_07" i wyświetla nazwę kategorii (**BUTTON** / **SENSOR**) na pasku slotu.

## Weryfikacja

- Pomyślnie skompilowano i wgrano firmware v6.24 na ESP.
- Wdrożono nową wersję aplikacji na telefon.
- Sprawdzono mechanizm cache'owania, który zapobiega zawieszaniu się interfejsu na ekranie ładowania.

## Jak testować?

1.  Otwórz zakładkę "Wejścia" – pasek slotu 07 powinien pokazać **BUTTON** (lub nazwę kategorii).
2.  Rozwiń slot i sprawdź menu "Funkcja" – nie powinno być w nim Kierunków ani Startera.
3.  Zmień Typ na **SENSOR** – zobaczysz nową listę opcji (OIL, FUEL, BRAKE itd.).
4.  Wybierz funkcję, zapisz i ciesz się automatyczną synchronizacją nazw i portów narzuconą przez Huba.
