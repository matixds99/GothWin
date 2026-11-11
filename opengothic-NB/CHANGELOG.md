# Log poprawek – OpenGothic + Gothic II: New Balance

Bazą są pliki z repo `borsuczyna/opengothic-android` (wersja z 31.10.2026, commit `31b8115`).
Każda poprawka ma swój numer. Nowe dopisuję na końcu listy.

Status:
- ✅ sprawdzone w grze
- 🧪 czeka na test

---

### 0. Obsługa skryptów New Balance (Copilot, przed tym logiem) – ✅
- **Pliki:** `common/game/gamescript.cpp`, `common/world/objects/npc.cpp`
- **Co:** zastępniki funkcji `RX_*` z pluginu Union (zmienne NB postaci, maski, teksty, losowanie, wywołania zwrotne, cykl `RX_Main_CycleTrigger` co sekundę). Dzięki nim działają teksty i ticki.
- **AI potworów (`npc.cpp`):** zmienne NB postaci (`RX_GETNPCVAR`/`RX_SETNPCVAR`, zapisywane w save'ach) naprawiły AI potworów. Wilki, gobliny i inne potwory znów atakują bohatera.

### 1. Czarny ekran w scenie z Xardasem – ✅ (11.11.2026)
- **Pliki:** `common/world/triggers/cscamera.cpp`, `common/world/triggers/cscamera.h`
- **Objaw:** po pierwszym dialogu z Xardasem ekran robił się czarny i zostawały same napisy („Otrzymano parę zwojów…”).
- **Przyczyna:** kamera filmowa `EVT_START_CAM` miała błędne czasy klatek kluczowych (oryginalny silnik przelicza je sam, OpenGothic nie). Wychodziło dzielenie przez zero, kamera dostawała pozycję NaN i nie widziała nic.
- **Poprawka:** błędne czasy są rozkładane po trasie kamery, dodane zabezpieczenia przed dzieleniem przez zero. Jeśli kamera mimo to dostanie złą pozycję, scena jest przerywana i wraca zwykła kamera.

### 2. Stoły rzemieślnicze nic nie robiły – ✅ alchemia, 🧪 pozostałe (12.11.2026)
- **Plik:** `common/game/gamescript.cpp` (funkcja `RX_OPENCRAFT` w bloku NB)
- **Objaw:** stół alchemiczny, runiczny itd. nie otwierał żadnego menu.
- **Przyczyna:** NB otwiera stoły przez `RX_OpenCraft(...)`, czyli graficzne okno z pluginu Union, którego OpenGothic nie ma.
- **Poprawka:** `RX_OpenCraft` otwiera stare menu dialogowe, które nadal jest w skryptach NB (jak `AI_ProcessInfos(hero)` w Gothicu 2). Przepisy i składniki pochodzą ze skryptów moda.
- **Do sprawdzenia:** stół runiczny, zwoje, kowadło, piec. Kuchnia, łuki, biżuteria i runy demoniczne mogą nie mieć starego menu.

### 3. Biały ekran wczytywania – 🧪 (12.11.2026)
- **Pliki:** `common/gothic.cpp`, `common/mainwindow.cpp`
- **Objaw:** przy wczytywaniu białe tło, na nim tylko pasek postępu.
- **Przyczyna:** NB nie ma zwykłego `LOADING.TGA`, bo plugin Union losuje obrazki `LOADING_<numer>`. Pusta tekstura rysowała się jako biały prostokąt.
- **Poprawka:** przy wczytywaniu losowany jest obrazek `LOADING_<numer>` moda. Jeśli go nie ma, brany jest dowolny `LOADING_*`, a gdy nie ma żadnego obrazka, tło jest czarne.

---

## Do zrobienia
- [ ] Menu „Ustawienia gry” jest puste. Ta strona w NB jest budowana przez plugin Union (5 kategorii, ok. 130 opcji). Czeka na decyzję: ukryć ją, zostawić tylko działające opcje albo odbudować całe menu.
