# Visual Target (Fabric 1.21.4)

Mod kliencki. Klawisz (domyslnie **Prawy Shift**) otwiera menu **Visuals**.
Gdy celownikiem najedziesz na gracza lub moba, wokol niego pojawiaja sie efekty
(pojawiaja sie i znikaja plynnie).

## Menu Visuals
**Effects**
- **Box** - obrys hitboxa
- **Ring** - pulsujacy pierscien pod nogami + obracajace sie znaczniki
- **Scanner** - pierscien jezdzacy w gore i w dol (z ogonem)
- **Orbit** - swiecace punkty krazace wokol celu (z ogonami)
- **Ghost (wind)** - wiatr: smugi spiralnie wznoszace sie wokol celu

**Color** - jeden suwak: biel na poczatku, potem cala teczka. **Rainbow** zmienia kolory w czasie.

**Settings** - Range (zasieg), Speed (predkosc animacji), cele: wszyscy / tylko gracze.
**Options** - Glow (miekka poswiata na liniach).

Ustawienia zapisuja sie w `config/visualtarget.json`.
Klawisz zmienisz w *Opcje > Sterowanie > Visual Target*.

## Jak zbudowac
Najlatwiej przez GitHub Actions (plik `.github/workflows/build.yml`): wrzuc projekt do repo,
w zakladce Actions poczekaj na zielony ptaszek i pobierz artefakt `visualtarget-mod`.

Albo lokalnie: JDK 21 + IntelliJ, panel Gradle > Tasks > build > build.
Gotowy plik: `build/libs/visualtarget-1.0.0.jar` (nie ten z `-sources`).
Do folderu `mods` wrzuc tez **Fabric API** (1.21.4).

## Struktura
- `VisualTargetClient.java` - start moda, klawisz
- `VisualsScreen.java` - menu (rysowane recznie)
- `TargetRenderer.java` - wykrywanie celu i rysowanie efektow
- `VisualConfig.java` - zapis i odczyt ustawien
