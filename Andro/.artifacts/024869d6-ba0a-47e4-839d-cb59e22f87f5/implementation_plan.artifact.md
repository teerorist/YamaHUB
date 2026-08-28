# Nowa logika kierunkowskazów i HAZARD

Implementacja 8 reguł sterowania zgodnie z wytycznymi użytkownika.

## Proponowane zmiany

### Logika biznesowa

#### [MODIFY] [ControlBlinkers.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/ControlBlinkers.kt)

- **Zmiana stałej czasu**: Ustawienie progu długiego kliknięcia na 400ms.
- **Implementacja reguł 1-4**:
    - onDown: Natychmiastowe wysłanie komendy N (:1). Jeśli przeciwna strona jest aktywna, wysłanie OFF (:0) dla niej.
    - Timer (400ms): Przełączenie na NS (:2) po przekroczeniu czasu trzymania.
- **Implementacja reguł 5-6 (HAZARD start)**:
    - Wykrywanie wciśnięcia drugiego przycisku w momencie, gdy pierwszy jest w stanie "long" (trzymany > 400ms).
- **Implementacja reguł 7-8 (HAZARD stop)**:
    - Wyłączanie awaryjnych przy dowolnym kliknięciu w trakcie ich działania.
    - Dodatkowe włączenie NS przy długim kliknięciu wyłączającym awaryjne.

## Plan weryfikacji

### Weryfikacja ręczna
1. Sprawdzenie reguł 1-2: Krótkie tapnięcie (N) vs Przytrzymanie (NS).
2. Sprawdzenie reguł 3-4: Zmiana strony w trakcie mrugania.
3. Sprawdzenie reguł 5-6: Uruchomienie awaryjnych przez kombinację Long + Down.
4. Sprawdzenie reguł 7-8: Wyłączanie awaryjnych krótkim i długim kliknięciem.
