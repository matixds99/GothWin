# Log poprawek – OpenGothic + Gothic II: New Balance

Bazą są pliki z repo `borsuczyna/opengothic-android` (wersja z 31.10.2026, commit `31b8115`).
Każda poprawka ma swój numer. Nowe dopisuję na końcu listy.

Status:
- ✅ sprawdzone w grze
- 🧪 czeka na test

Pliki: przy każdej poprawce jest lista plików. **🆕 NOWY PLIK** oznacza plik, którego nie ma w projekcie borsuczyny i który trzeba dodać. Pozostałe pliki się podmienia. Pełna lista jest w [README.md](README.md).

---

### ✅ Sprawdzone też w grze (12.11.2026): pulpity z księgami dają inteligencję (5 → 6), stół demonologiczny otwiera menu.

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

### 3a. Ekran wczytywania pokazywał obrazek innego świata – ✅ (12.11.2026)
- **Pliki:** `common/gothic.cpp`, `common/mainwindow.cpp`
- **Objaw:** przy wczytywaniu save'a albo teleporcie do Khorinis pokazywał się Jarkendar, Pustynia Adanosa itd.
- **Przyczyna:** przy wczytywaniu save'a OpenGothic zawsze prosi o `LOADING.TGA`, nie podając świata, więc losowany był obrazek z dowolnego świata. Gdy dla danego świata nie było obrazka, poprawka 3 też brała losowy z innego świata.
- **Poprawka:** przy wczytywaniu save'a świat jest odczytywany z nagłówka save'a i prosi się o `LOADING_<ŚWIAT>.TGA`, tak jak przy zmianie świata. Kolejność wyboru: losowy obrazek tego świata → obrazek tego świata bez numeru → losowy z dowolnego świata. W logu jest linia `loading screen: ...` z nazwą świata i wybranym obrazkiem.

### 4. Opcje moda z Gothic.ini nie działały – ✅ (12.11.2026, sprawdzone na bSkipLogo=1)
- **Plik:** `common/game/gamescript.cpp` (`MENU_READINT`, `MENU_WRITEINT` w bloku NB)
- **Objaw:** skrypty NB czytają swoje opcje z `Gothic.ini`, sekcja `[AST]` (np. `bSkipLogo`, `bCanSaveGame`, `bOptionHealth`, `bOptionMonsterPower`, `bShowKarma`, ok. 70 opcji). Każda zwracała 0, a zapisy przepadały.
- **Przyczyna:** `Menu_ReadInt` / `Menu_WriteInt` dostarcza plugin Union, a OpenGothic ich nie miał.
- **Poprawka:** obie funkcje czytają i zapisują ustawienia OpenGothic (`Gothic.ini`). Opcje, które sprawdzają same skrypty NB, działają po wpisaniu ich do `[AST]`, np. `bSkipLogo=1` pomija intro.

### 5. Puste menu „Ustawienia gry” – ✅ (12.11.2026)
- **Pliki:** `common/ui/gamemenu.cpp`, `common/game/gamescript.cpp`, `common/game/nboptions.h` 🆕 **NOWY PLIK**
- ⚠️ **`nboptions.h` to NOWY PLIK.** Trzeba go dodać do `common/game/`, obok `gamescript.cpp`. Bez niego build się nie uda.
- **Objaw:** pierwsza kategoria w ustawieniach („Ustawienia gry”) była pusta.
- **Przyczyna:** w `MENU.DAT` moda ta strona to pusta rama (`MENU_OPT_CONTROLS_NEWOPTIONS`), wypełnia ją plugin Union. Z kodu pluginu odczytałem pełną listę: ok. 140 opcji, ich klucze w `Gothic.ini`, typy i wartości domyślne.
- **Poprawka:**
  - strona pokazuje 19 przełączników, które czytają same skrypty NB. Wśród nich: pomijanie prologu, zapis w dowolnym miejscu, trudniejsi bossowie, okrzyki bohatera, karma bogów, sakiewka i torba na bohaterze, ekran śmierci oraz opcje Gniewu Beliara. Nazwy i „tak/nie” są brane ze skryptów gry, więc są po polsku. Reszty opcji pluginu nie pokazuję, bo sterują funkcjami, które ma tylko Union.
  - wartości domyślne opcji NB (sekcje `[AST]` i `[BELIARS_RAGE]`) są dopisywane do `Gothic.ini`, jeśli ich tam nie ma, tak jak robi to silnik z Unionem. Przykład: `bCanSaveLeg=1` (zapis w dowolnym miejscu) i `bToggleHardBosses=1` są domyślnie włączone, a wcześniej skrypty widziały 0.
- **Co robi każdy przełącznik** (sprawdzone w skryptach NB):
  | Opcja | Co robi skrypt | Działa w OpenGothic? |
  |---|---|---|
  | Pomiń ekran powitalny (`bSkipLogo`) | pomija prolog | ✅ sprawdzone |
  | Status karmy u bogów (`bShowKarma`) | wypisuje karmę Innosa/Beliara | ✅ powinno (zwykłe teksty) |
  | Głód/pragnienie/zmęczenie (`bHFTStatus`) | wypisuje potrzeby bohatera | ✅ powinno (zwykłe teksty) |
  | Nazwy lokacji (`bShowNotice`) | tablice z nazwami miejsc | ✅ powinno |
  | Skalowanie postaci od siły (`bScaleStr`) | `Mdl_SetModelScale` bohatera | ✅ powinno |
  | Ekran śmierci (`bShowDeadScreen`) | dźwięk i ekran po śmierci | ✅ powinno |
  | Efekt zatrucia (`bShowPoisonHero`) | efekt `SPELLFX_UNDEAD_SCREENBLEND` | ✅ powinno |
  | Efekt ataków Paladyna (`bShowEffectPalUndeadProc`) | efekt przy obrażeniach nieumarłym | ✅ powinno |
  | Trudniejsi bossowie (`bToggleHardBosses`) | umiejętności bossów w AI | ✅ powinno |
  | Okrzyki bohatera (`bToggleHeroVoice`) | dźwięki bohatera w walce | ✅ powinno |
  | Gniew Beliara (5 opcji) | zmienne trybu Gniewu Beliara | ✅ powinno (tylko w tym trybie) |
  | Zapis w dowolnym miejscu (`bCanSaveLeg`) | blokada zapisu w NB | ❓ OpenGothic chyba w ogóle nie blokuje zapisu |
  | Ukryj sakiewki NPC (`bHidePocketsNpc`) | sakiewki na postaciach | ❓ zależy od slotów, patrz niżej |
  | Mieszek / torba bohatera (`bShowPocket`, `bShowBag`) | `EXT_PutInSlot` / `ActivateZSSlot` | ❌ tych funkcji Uniona jeszcze nie ma |
  - Usunięte ze strony: `AutoAIM` (skrypty same ustawiają go z powrotem na 0) i „Wytrzymałość przy otwartym ekwipunku” (to funkcja ekwipunku Uniona).

### 6. Brak wpisów do dziennika i zdarzeń w świecie (np. kamień runiczny przy wieży Xardasa) – ✅ (wpis w dzienniku jest) (12.11.2026)
- **Plik:** `common/game/gamescript.cpp` (pętla NB w `GameScript::tick`)
- **Objaw:** przy kamieniu runicznym koło wieży Xardasa nie pojawia się wpis „Kamienie runiczne”.
- **Przyczyna:** ten wpis (i wiele innych zdarzeń w świecie) daje skrypt `B_GetTime`. W NB woła go plugin Union po nazwie, a nie skrypty, więc w OpenGothic nie wołał go nikt. Z kodu pluginu: timer co 1000 ms woła na zmianę `RX_Main_CycleTrigger` i w następnej sekundzie `b_gettime`, a `PerFrame` jest wołany co klatkę.
- **Poprawka:** pętla działa tak jak w pluginie:
  - co klatkę `PerFrame` (z `RX_FrameTime` = czas klatki w ms),
  - co sekundę na zmianę `RX_Main_CycleTrigger` / `B_GetTime`. Uwaga: wcześniej (wersja Copilota) `RX_Main_CycleTrigger` szedł co sekundę, teraz co 2 sekundy, tak jak w pluginie,
  - co 5 sekund `B_CheckMenuOption` (odczyt opcji moda).

### 7. Łóżko: po przespaniu bohater nagle stoi, a potem robi animację wychodzenia z łóżka – ✅ (12.11.2026)
- **Plik:** `common/world/objects/npc.cpp` (`Npc::resetPositionToTA`)
- **Przyczyna:** sen przewija czas przez `Wld_SetTime`. OpenGothic ustawia wtedy wszystkie postacie według ich planu dnia i przy okazji zatrzymywał animację bohatera, czyli stawiał go na nogi, mimo że ten dalej leżał w łóżku (w menu snu). Gdy zamykałeś menu, łóżko odtwarzało jeszcze animację wstawania.
- **Poprawka:** jeśli bohater używa obiektu (łóżka), jego animacja i kolejka dialogu zostają bez zmian. Wstaje dopiero normalną animacją łóżka.

### 8. Statystyki postaci (B): ciemny ekran bez niczego – ✅ (12.11.2026)
- **Pliki:** `common/ui/gamemenu.cpp`, `common/ui/gamemenu.h` (oba zmienione, nowych plików brak)
- **Przyczyna:** w NB statystyki to okno pluginu Union z 5 zakładkami. `MENU_STATUS` w `MENU.DAT` moda ma tylko pusty nagłówek, więc OpenGothic rysował tylko przyciemnienie.
- **Poprawka:** własny ekran statystyk w stylu menu ustawień, w dwóch kolumnach:
  - **Postać:** gildia, poziom, doświadczenie, następny poziom, punkty nauki,
  - **Atrybuty:** siła, zręczność, **inteligencja** (atrybut NB), mana, punkty życia, **wytrzymałość** (NB),
  - **Walka:** broń jednoręczna, dwuręczna, łuk, kusza (nazwy ze skryptów gry),
  - **Ochrona:** broń sieczna, obuchowa, pociski, ogień, magia,
  - **Czas:** dzień i godzina.
- **Poprawione po teście:** wytrzymałość pokazywała „99/10” (NB trzyma ją ×10, teraz jest „99/100”), sekcja „Czas” przeszła do lewej kolumny, bo przycisk wstecz zasłaniał dzień, a tytuł nie nachodzi już na przycisk pauzy.
- **Poprawione po teście 2:** ekran czasem był po angielsku („Character”), bo ustawienie języka gry nie zawsze wynosi 2. Teraz język jest rozpoznawany też po polskich literach w tekstach gry (nazwy talentów i gildii).
- **Jeszcze nie ma:** pozostałych zakładek Uniona (rzemiosło, trudność itd.), można je dodać później.

### 9. Brak komunikatu na ekranie przy nowym wpisie do dziennika – ✅ (12.11.2026)
- **Plik:** `common/game/gamescript.cpp` (blok NB)
- **Objaw:** wpis trafia do dziennika, ale na ekranie nie pojawia się „Zadanie uzupełnione!”.
- **Przyczyna:** `B_LogEntry` w NB pokazuje komunikat przez `AI_PrintQuest(tytuł, temat)`, czyli funkcję Uniona.
- **Poprawka:** `AI_PrintQuest`, `AI_PrintQuestComplete` i `AI_PrintQuestFail` wypisują na ekranie tytuł i nazwę tematu. Dodane są też inne komunikaty Uniona (`AI_Print`, `AI_PrintRed`, `AI_PrintBonus`, `AI_NoticePrint`, `AI_FastPrintSkillCraft`), ale tylko wtedy, gdy w skryptach są to funkcje zewnętrzne. Te, które już działają przez skrypty, zostają bez zmian.

### 10. Pełne okno statystyk NB – 5 zakładek – 🧪 (12.11.2026)
- **Pliki:** `common/ui/gamemenu.cpp`, `common/ui/gamemenu.h` (oba zmienione, nowych plików brak)
- **Co:** ekran statystyk (B) ma zakładki jak okno pluginu Union. Przy każdym otwarciu woła skrypt NB `RefreshStatusMenuData`, który wylicza wszystkie wartości (po polsku, ze skryptów: „mistrz”, „ekspert”, „uczony”, nazwy bossów…):
  1. **Postać:** gildia, rozdział, poziom, doświadczenie, punkty nauki, dzień, ranga, karma Innosa i Beliara, gildie złodziei, zabójców i kupców, zabici przeciwnicy, cały poziom trudności (PN i życie na poziom, życie, siła i ochrona wrogów, mikstury, pułapki, sen, głód),
  2. **Umiejętności:** rzemiosło, górnictwo i wytapianie, strzały, myślistwo (skóry, kły, pazury, rogi, łuski smoków…), gotowanie, złodziejstwo, magia, języki, retoryka, regeneracja, dary bogów,
  3. **Atrybuty:** siła, zręczność i mana (z wartością bazową), inteligencja, życie, wytrzymałość, władanie bronią w % ze stopniem (zielony/adept/ekspert/mistrz), tarcza, dwie bronie, styl orków,
  4. **Ochrona:** sieczna, obuchowa, pociski, ogień, magia, przewrócenie, odporność na trucizny, zimno i żar,
  5. **Osiągnięcia:** pokonani bossowie (39 możliwych).
- Listę przewija się palcem, a zakładkę zmienia się dotknięciem.
- **Uwaga:** opisów pozycji nie ma w plikach moda (rysuje je plugin), więc są napisane od nowa i mogą się trochę różnić od oryginału. Wartości są oryginalne, ze skryptów.

---

## Do zrobienia
- [ ] `EXT_PutInSlot` / `ActivateZSSlot`: mieszek i torba na bohaterze (opcje z poprawki 5)
