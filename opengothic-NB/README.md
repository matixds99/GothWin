# Poprawki OpenGothic dla Gothic II: New Balance – gałąź `test`

Baza: projekt [`borsuczyna/opengothic-android`](https://github.com/borsuczyna/opengothic-android), wersja z 31.10.2026 (commit `31b8115`).
Ścieżki plików są takie same jak w tamtym projekcie: plik `opengothic-NB/common/gothic.cpp` z tego repo trafia do `common/gothic.cpp` w projekcie.

Historia wszystkich poprawek: **[CHANGELOG.md](CHANGELOG.md)**

---

## ⚠️ Nowe pliki (nie ma ich w projekcie borsuczyny)

Te pliki trzeba **dodać**, nie podmieniać. Bez nich build się nie uda.

| Nowy plik | Gdzie go wrzucić | Kto go potrzebuje | Od poprawki |
|---|---|---|---|
| `common/game/nboptions.h` | do folderu `common/game/`, obok `gamescript.cpp` | `gamescript.cpp`, `ui/gamemenu.cpp` | 5 |
| `common/game/nbiteminfo.h` | do folderu `common/game/`, obok `gamescript.cpp` | `gamescript.cpp` | 12, 13 |

> To tylko pliki nagłówkowe (`.h`), więc nie trzeba niczego dopisywać w `CMakeLists.txt`.

---

## Zmienione pliki (podmień 1:1)

| Plik | Co jest zmienione | Poprawki |
|---|---|---|
| `common/world/triggers/cscamera.cpp` | kamera filmowa: czarny ekran w scenie z Xardasem | 1 |
| `common/world/triggers/cscamera.h` | j.w. | 1 |
| `common/game/gamescript.cpp` | obsługa skryptów NB (Copilot) + stoły rzemieślnicze, `Menu_ReadInt/WriteInt`, domyślne opcje, pętle pluginu (`PerFrame`, `B_GetTime`), komunikaty na ekranie (`AI_PrintQuest`…), opisy przedmiotów w ekwipunku, wagi przedmiotów (`RX_SetWeightValueExt`) | 0, 2, 4, 5, 6, 9, 12, 13 |
| `common/world/objects/npc.cpp` | zmienne NB postaci, AI potworów (Copilot) + bohater nie wstaje z łóżka przy śnie, diagnostyka zbroi | 0, 0a, 7, 16 |
| `common/gothic.cpp` | ekran wczytywania: obrazki moda `LOADING_<ŚWIAT>_<numer>` | 3, 3a |
| `common/mainwindow.cpp` | ekran wczytywania: świat z nagłówka save'a, czarne tło zamiast białego, pasek wczytywania dla tekstur HD | 3, 3a, 14 |
| `common/ui/gamemenu.cpp` | strona „Ustawienia gry” z przełącznikami NB, okno statystyk postaci (B) z 5 zakładkami, zakładki dziennika (zakończone, bestiariusz) | 5, 8, 10, 15 |
| `common/ui/gamemenu.h` | nowe funkcje ekranu statystyk i zakładek dziennika | 8, 10, 15 |
| `common/ui/inventorymenu.cpp` | okienko opisu przedmiotu (dotyk): wiersze nie nachodzą na nazwę i nie wychodzą poza okienko | 12 |

---

## Mody JS (nie trzeba budować APK)

Mody w JavaScripcie dla systemu modów projektu borsuczyny (`docs/MODS.md`). Instalacja na telefonie: launcher → **Mods** → **Add a mod (.zip)**, potem włącz przełącznik.

| Mod | Plik do zainstalowania | Co robi | Poprawka |
|---|---|---|---|
| `mods/nb-wisp` | `mods/nb-wisp.zip` | wędrujący ognik od Vatrasa: Shift+F5, przycisk w zakładce „Ognik” menu modów albo komenda `ognik` | 11 |

---

## Pliki potrzebne, których tu nie ma

| Plik | Dlaczego |
|---|---|
| `common/world/objects/npc.h` | `npc.cpp` potrzebuje pól NB (`newBalanceNpcVars`, `newBalanceGetNpcVar`/`newBalanceSetNpcVar`). Wersja z tymi polami jest u autora gałęzi (zmiany Copilota). Bez niej `npc.cpp` się nie skompiluje. |

---

## Zasada

Przy każdej poprawce w `CHANGELOG.md` jest napisane, które pliki zostały **zmienione**, a które są **nowe**. Każdy nowy plik trafia też do tabelki „Nowe pliki” na górze tej strony, razem z miejscem, gdzie go wrzucić.
