# Poprawki OpenGothic (New Balance) – gałąź test

> ⚠️ **`common/game/nboptions.h` to nowy plik.** Nie ma go w projekcie borsuczyny. Skopiuj go do `common/game/`, obok `gamescript.cpp`, bo inaczej build się nie uda (`gamescript.cpp` i `gamemenu.cpp` go potrzebują).

Pliki do podmiany 1:1 w projekcie `borsuczyna/opengothic-android` (ta sama ścieżka co tutaj):

| Plik | Co zawiera |
|---|---|
| `common/world/triggers/cscamera.cpp` | poprawka czarnego ekranu w scenie z Xardasem (kamera `EVT_START_CAM`) |
| `common/world/triggers/cscamera.h` | j.w. |
| `common/game/gamescript.cpp` | Twoja wersja (obsługa NB od Copilota) + `RX_OPENCRAFT`: stoły alchemiczne, runiczne itd. otwierają stare menu z przepisami |
| `common/gothic.cpp` | ekran wczytywania: losowy obrazek `LOADING_<numer>` moda (jak w Unionie), gdy brak obrazka – czarne tło zamiast białego |
| `common/mainwindow.cpp` | j.w. (czarne tło zamiast białego prostokąta) |
| `common/ui/gamemenu.cpp` | strona „Ustawienia gry” z przełącznikami NB |
| `common/game/nboptions.h` | ⚠️ **NOWY PLIK** – wrzuć go do folderu `common/game/`, obok `gamescript.cpp`. Lista opcji NB i ich domyślne wartości (wyciągnięte z pluginu Union) |
| `common/world/objects/npc.cpp` | Twoja wersja (zmienne NB postaci, `RX_GETNPCVAR`/`RX_SETNPCVAR`) – bez zmian |

`npc.cpp` wymaga Twojego `npc.h` (pola `newBalanceNpcVars`, `newBalanceGetNpcVar`/`newBalanceSetNpcVar`), którego tu nie ma.

Historia poprawek: [CHANGELOG.md](CHANGELOG.md)
