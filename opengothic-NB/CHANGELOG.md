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
- **Zmienne NB postaci (`npc.cpp`):** `RX_GETNPCVAR`/`RX_SETNPCVAR`, zapisywane też w save'ach.

### 0a. AI potworów – potwory nie atakowały – ✅
- **Plik:** `common/world/objects/npc.cpp`
- **Objaw:** wilki, gobliny i inne potwory nie reagowały na bohatera i nie atakowały.
- **Przyczyna:** skrypt NB `ZS_MM_AllScheduler` robi `AI_RemoveWeapon` + `AI_StartState`. Potwory nie mają animacji chowania broni (wilk w trybie pięści, goblin z pałką), więc `AI_RemoveWeapon` wisiał w kolejce AI w nieskończoność i blokował wszystko po nim. Potwór nie dostawał stanu ani percepcji (`percEnemy=0`), więc `PERC_ASSESSENEMY` → `B_MM_AssessEnemy` nigdy się nie uruchamiało.
- **Poprawka:** u potworów `AI_RemoveWeapon` chowa broń od razu, bez animacji (tak jak `drawWeaponFist` wyciąga ją bez animacji). Kolejka się odblokowuje, a atak dalej idzie przez skrypty NB. Dzięki temu działają wyjątki z NB: przywołańcy i drużyna nie atakują bohatera, potwory z questów czekają na swój etap, wilki najpierw warczą, owce uciekają.
- **Diagnostyka:** w logu są linie `NB ai: ...` (stan, kolejka, percepcje potwora przy bohaterze). Można je wyłączyć przez `NB_AI_TRACE = false`.

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

### 3. Biały ekran wczytywania – ✅ (12.11.2026, poprawione 2x)
- **Pliki:** `common/gothic.cpp`, `common/mainwindow.cpp`
- **Objaw:** przy wczytywaniu białe tło, na nim tylko pasek postępu.
- **Przyczyna:** `LOADING.TGA` w NB to tylko malutka biała zaślepka. Plugin Union losuje prawdziwe obrazki z `/_work/data/textures/_compiled/` według wzoru `LOADING_<ŚWIAT>_<numer>` (sprawdzone w kodzie pluginu). Pierwsza wersja poprawki szukała złego wzoru (`LOADING_<numer>`), dlatego nadal było białe tło.
- **Poprawka:** losowany jest obrazek `LOADING_<ŚWIAT>_<numer>`. Przy zmianie świata brany jest obrazek tego świata, przy wczytywaniu save'a dowolny. Zaślepki mniejsze niż 64×64 są pomijane, a gdy nie ma żadnego obrazka, tło jest czarne zamiast białego.

### 3a. Ekran wczytywania pokazywał obrazek innego świata – 🧪 (12.11.2026)
- **Pliki:** `common/gothic.cpp`, `common/mainwindow.cpp`
- **Objaw:** przy wczytywaniu save'a albo teleporcie do Khorinis pokazywał się Jarkendar, Pustynia Adanosa itd.
- **Przyczyna:** przy wczytywaniu save'a OpenGothic zawsze prosi o `LOADING.TGA`, nie podając świata, więc losowany był obrazek z dowolnego świata. Gdy dla danego świata nie było obrazka, poprawka 3 też brała losowy z innego świata.
- **Poprawka:** przy wczytywaniu save'a świat jest odczytywany z nagłówka save'a i prosi się o `LOADING_<ŚWIAT>.TGA`, tak jak przy zmianie świata. Kolejność wyboru: losowy obrazek tego świata → obrazek tego świata bez numeru → losowy z dowolnego świata. W logu jest linia `loading screen: ...` z nazwą świata i wybranym obrazkiem.

### 4. Opcje moda z Gothic.ini nie działały – ✅ (12.11.2026, sprawdzone na bSkipLogo=1)
- **Plik:** `common/game/gamescript.cpp` (`MENU_READINT`, `MENU_WRITEINT` w bloku NB)
- **Objaw:** skrypty NB czytają swoje opcje z `Gothic.ini`, sekcja `[AST]` (np. `bSkipLogo`, `bCanSaveGame`, `bOptionHealth`, `bOptionMonsterPower`, `bShowKarma`, ok. 70 opcji). Każda zwracała 0, a zapisy przepadały.
- **Przyczyna:** `Menu_ReadInt` / `Menu_WriteInt` dostarcza plugin Union, a OpenGothic ich nie miał.
- **Poprawka:** obie funkcje czytają i zapisują ustawienia OpenGothic (`Gothic.ini`). Opcje, które sprawdzają same skrypty NB, działają po wpisaniu ich do `[AST]`, np. `bSkipLogo=1` pomija intro.

### 5. Puste menu „Ustawienia gry” – 🧪 (12.11.2026)
- **Pliki:** `common/ui/gamemenu.cpp`, `common/game/nboptions.h` (nowy), `common/game/gamescript.cpp`
- **Objaw:** pierwsza kategoria w ustawieniach („Ustawienia gry”) była pusta.
- **Przyczyna:** w `MENU.DAT` moda ta strona to pusta rama (`MENU_OPT_CONTROLS_NEWOPTIONS`), wypełnia ją plugin Union. Z kodu pluginu odczytałem pełną listę: ok. 140 opcji, ich klucze w `Gothic.ini`, typy i wartości domyślne.
- **Poprawka:**
  - strona pokazuje 21 przełączników, które czytają same skrypty NB. Wśród nich: pomijanie prologu, zapis w dowolnym miejscu, trudniejsi bossowie, okrzyki bohatera, karma bogów, sakiewka i torba na bohaterze, ekran śmierci oraz opcje Gniewu Beliara. Nazwy i „tak/nie” są brane ze skryptów gry, więc są po polsku. Reszty opcji pluginu nie pokazuję, bo sterują funkcjami, które ma tylko Union.
  - wartości domyślne opcji NB (sekcje `[AST]` i `[BELIARS_RAGE]`) są dopisywane do `Gothic.ini`, jeśli ich tam nie ma, tak jak robi to silnik z Unionem. Przykład: `bCanSaveLeg=1` (zapis w dowolnym miejscu) i `bToggleHardBosses=1` są domyślnie włączone, a wcześniej skrypty widziały 0.

---

## Do zrobienia
- [ ] (na razie nic – dopisuję nowe zgłoszenia)
