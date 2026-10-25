# Visual Target (Fabric 1.21.4)

Mod kliencki. Klawisz (domyslnie **Prawy Shift**) otwiera menu. Klawisz zmienisz w Opcje > Sterowanie > Visual Target.
Ustawienia zapisuja sie w `config/visualtarget.json`.

## Zakladki menu
- **Themes** - motywy: Black, Purple-Pink, Dark Purple; przezroczystosc panelu, zaokraglone rogi, animowane kolory
- **Target** - efekty wokol moba/gracza, w ktorego celujesz: Box, Ring, Scanner, Orbit, Ghost (wiatr), Glow; zasieg, predkosc, wszyscy / tylko gracze
- **Extras** - Jump circle, Trails, Blizzard, Fireflies
- **Sky** - Aurora, Nebula, Galaxy, Caustics, Black hole (tylko Overworld); intensywnosc i predkosc
- **Pets** - Bat, Cat, Chicken, Dog chodza lub lataja wokol Ciebie; rozmiar

Kolory wszystkiego (menu, efekty, niebo, obroze petow) pochodza z wybranego motywu.

## Jak zbudowac
Najlatwiej przez GitHub Actions (`.github/workflows/build.yml`): wrzuc projekt do repo,
w zakladce Actions poczekaj na zielony ptaszek i pobierz artefakt `visualtarget-mod`.

Albo lokalnie: JDK 21 + IntelliJ, panel Gradle > Tasks > build > build.
Gotowy plik: `build/libs/visualtarget-1.0.0.jar` (nie ten z `-sources`).
Do folderu `mods` wrzuc tez **Fabric API** (1.21.4).

## Struktura
- `VisualTargetClient.java` - start moda, klawisz
- `VisualsScreen.java` - menu (rysowane recznie)
- `Theme.java`, `VisualConfig.java` - motywy i zapis ustawien
- `Painter.java`, `TargetRenderer.java`, `ExtrasRenderer.java` - efekty w swiecie
- `SkyRenderer.java` - niebo, `PetRenderer.java` - pety
