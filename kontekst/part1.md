# YamaHUB - Kontekst Projektu (Część 1)

## Wstęp
Zapis rozmowy dotyczącej pierwotnych założeń, ewolucji systemu oraz naprawy błędów w module InputCfg projektu YamaHUB.

---

### CZĘŚĆ I: Pierwotne Założenia (Specyfikacja Produktowa)

#### 1. Dwie osie: IN i OUT
- **IN 01 … IN 10**: Stałe fizyczne GPIO po lewej stronie. Nie zmienia się nazwy portu ani kolejności.
- **Funkcja (slot)**: Przeciągalna (DnD) przypisana do konkretnego IN.
- **OUT 01 … OUT 10**: Przekaźniki / MOSFETy. Wybór wyjścia odbywa się wewnątrz slotu funkcji.
- **Zapis w ESP**: Tablica 10 rekordów: `{ mode, outIndex 0..9, name[15] }`.

#### 2. Rodzaje funkcji (UI) → mode (ESP)
- **KIERUNEK L**: Dokładnie 1, stały rodzaj, 1 OUT obowiązkowy. (Mode 2)
- **KIERUNEK P**: Dokładnie 1, stały rodzaj, 1 OUT obowiązkowy. (Mode 3)
- **LIGHTS**: 1 lub 2 sloty. Mode 0 (Toggle). Obsługa LOW/HI beam.
- **BRAKE**: 1 lub 2 sloty. Wspólny 1 OUT dla obu (Logic OR). Mode 1 (zmienione później na 4 - SENSOR).
- **NEUTRAL**: Dokładnie 1, stały rodzaj. Mode 1 (zmienione na 4 - SENSOR). 1 OUT (indicator).
- **STARTER**: Dokładnie 1, stały rodzaj. Mode 6.
- **BUTTON**: Wielokrotnego użytku, Mode 0 (Toggle). 1 OUT.
- **SENSOR**: Wielokrotnego użytku, Mode 4. Bez OUT (unbound).
- **DISABLED**: Martwy slot, Mode 5.

#### 3. Logika OUT i Konfliktów
- **LIGHTS**: 1 slot = 2 OUT (HI/LOW). 2 sloty = po 1 OUT na każdy.
- **BRAKE**: Zawsze ten sam OUT dla front i rear. To nie jest konflikt.
- **Konflikty**: Zajęte wyjścia są przyciemnione w pickerze. Wybranie zajętego OUT powoduje czerwony komunikat "konflikt". Blokada zapisu przy konflikcie (poza BRAKE).

#### 4. Zachowanie Runtime (ESP jako źródło prawdy)
- **Starter**: Short press (≤ 400ms) = Killswitch. Long press = Kręcenie (odcina inne wyjścia na czas rozruchu). Działa tylko gdy Neutral/Clutch = ON.
- **Kierunki**: Obsługa gestów N (krótkie), NS (długie), HAZARD (oba).
- **Auto-Lights**: Automatyczne mijania powyżej progu prędkości.

---

### CZĘŚĆ II: Ewolucja i Rozmowa z Agentem AI

#### Kluczowe zmiany wprowadzone w trakcie prac:
1. **Migracja MOMENT → SENSOR**: Usunięcie trybu MOMENT. NEUTRAL, BRAKE i inne sensory trafiają do kategorii SENSOR (Mode 4).
2. **Rozszerzony format INCFG**: ESP wysyła i odbiera teraz 5 pól: `mode, outNum, outputEnabled, outputNum, name`.
3. **Interlock Startera**: Twarde zabezpieczenie na ESP - starter ruszy tylko przy aktywnym sensorze o nazwie "neutral" lub "clutch" (lub symulacji z apki).
4. **Model Event-Driven**: Rezygnacja z pollingu (ciągłego odpytywania o stan). ESP wysyła `INSTATE` oraz `STATE` tylko przy zmianie.
5. **Dashboard/Test jako Nadajnik**: Sekcja testowa na dashboardzie wysyła komendy symulacji do ESP, ale nie słucha odpowiedzi (nie aktualizuje switchy stanem zwrotnym, aby uniknąć pętli).

---

### Problemy i Naprawy (Debugging Log)

- **Resetowanie się ESP**: Wykryto rekurencję w `sendStarterStatus()`. Naprawiono poprzez usunięcie zapętlonego wywołania.
- **Problem "OUT 01 · konflikt"**: UI błędnie wyświetlało techniczny indeks wejścia zamiast przypisanego wyjścia. Naprawiono poprzez ujednolicenie logiki w `InputSettingsTab.kt` i synchronizację pól `outNum` / `outputNum`.
- **Precyzja Prędkości**: Zwiększono częstotliwość wysyłania prędkości do 100ms i przeniesiono GPS do `Foreground Service` (obsługa w tle).
- **Pasywność BRAKE 2**: Zmieniono model tak, aby drugi slot hamulca nie uczestniczył w walidacji konfliktów (pobiera OUT z BRAKE 1).
- **Wygląd Edytora**: Ustalono standardową wysokość ramek na 56dp i układ "Checkbox z lewej + Picker wypełniający wiersz".

---

### Status na dzień 2026-09-04
Rozmowa utrwalona do celów dalszej implementacji i weryfikacji blokady edycji przy konflikcie.
KONIEC EKSPORTU.
