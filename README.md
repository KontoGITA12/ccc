# Visual Target (Fabric 1.21.4)

Mod kliencki. Klawisz (domyslnie **Prawy Shift**) otwiera menu **Visuals**.
Gdy celownikiem najedziesz na gracza lub moba, wokol niego pojawiaja sie efekty.

## Efekty (menu Visuals)
- **Box** - obrys hitboxa
- **Ring** - pulsujacy pierscien pod nogami + obracajace sie znaczniki
- **Scanner** - pierscien jezdzacy w gore i w dol
- **Orbit** - male krzyzyki krazace wokol celu
- **Targets** - wszystkie istoty albo tylko gracze
- **Rainbow** - tecza zamiast wlasnego koloru
- **Red / Green / Blue** - wlasny kolor, **Range** - zasieg, **Speed** - predkosc animacji

Ustawienia zapisuja sie w `config/visualtarget.json`.
Klawisz zmienisz w *Opcje > Sterowanie > Visual Target*.

## Jak zbudowac
1. Zainstaluj **JDK 21** (np. Temurin 21) i najlepiej IntelliJ IDEA (wersja Community wystarczy).
2. Otworz folder projektu w IntelliJ (File > Open) i poczekaj, az Gradle sciagnie zaleznosci (pierwszy raz kilka minut).
   W ustawieniach Gradle (Settings > Build Tools > Gradle) ustaw **Gradle JVM = 21**.
3. Zbuduj moda: panel **Gradle** po prawej > Tasks > build > **build** (dwuklik).
   Bez IntelliJ: zainstaluj Gradle 8.12+ i wpisz w folderze projektu `gradle build`.
4. Gotowy plik jest w `build/libs/visualtarget-1.0.0.jar` (nie ten z `-sources`).
5. Wrzuc jara do `.minecraft/mods` razem z **Fabric API** (do 1.21.4) i uruchom Minecrafta z profilem **Fabric Loader 1.21.4**.

Do testow w IDE: Gradle > Tasks > fabric > **runClient** (odpala Minecrafta z modem).

Jesli Gradle zglosi, ze jakas wersja nie istnieje, sprawdz aktualne numery na https://fabricmc.net/develop/
i wpisz je w `gradle.properties`.

## Struktura
- `VisualTargetClient.java` - start moda, klawisz
- `VisualsScreen.java` - menu (przyciski i suwaki)
- `TargetRenderer.java` - wykrywanie celu i rysowanie efektow
- `VisualConfig.java` - zapis i odczyt ustawien
