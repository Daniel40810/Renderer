# com.dan.render.frame – der Bildrahmen

**Stand: Nachlauf P6 (14.11.2026), Version 0.2** (F49 A, F50 A). Erstes Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, Abschnitte 8.1 und 12.7). Es legt das Bildziel an, führt je Bild die Teile des Bilds aus (Gelände, Wasser, Himmel), misst die Leuchtdichte, stellt die Belichtung nach und gibt das Bild durch die Kurve aus `film` aus: auf eine Fläche im Fenster oder in ein eigenes Bild.

## Schicht

```
com.dan.render.frame        core + core.gpu + LWJGL      Rahmen, Belichtung, Filmkurve
com.dan.render.frame.film   (nur java.*)                 Kopie des Kerns von com.dan.film (Curve, Film, Format, Grade, Stock)
com.dan.render.frame.demo   dazu FStyle                  FrameCheck
```

Die Module der Schicht `render` kennen einander nicht. Was alle brauchen, steht darunter: der Blick in `core.raum.Blick`, das Bildziel und die Schnittstelle der Teile in `core.gpu` (`Bildziel`, `Bildteil`). Die Teile setzt die Schicht `lauf` zusammen. Den Kern von `film` trägt das Modul als Kopie (wie `core.astro` die Ephemeriden aus `sky`); `com.dan.film` bleibt unverändert.

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Rahmen` | Bildziel (HDR `RGBA16F` mit L · Vorbelichtung in cd/m², Tiefe `D32F` umgekehrt), je Bild: löschen, Teile vorbereiten und zeichnen, Histogramm, Ausgabe in ein Ziel beliebigen Formats (sRGB verschlüsselt die Karte) |
| `Belichtung` | Augenanpassung aus dem Histogramm wie `exposure.EyeAdaptation` (Perzentile 40–92, ins Dunkle 3 s, ins Helle 0,8 s [G]), aber absolut: E bringt das Mittel der Szene auf den Bezug; E ist die Vorbelichtung des nächsten Bilds. Gemessen wird auf Gelände und Wasser (Bildpunkte mit Tiefe), solange sie mindestens 10 % [G] der gezählten tragen, sonst über das ganze Bild (F49 A, `nurBoden`). Sehen bei Nacht nach Krawczyk, Myszkowski und Seidel (2005) (F50 A, `nachtsehen`): α(Ȳ) = 1,03 − 2 / (2 + log₁₀(Ȳ + 1)) bestimmt wie bei Krawczyk die Helligkeit des Mittels im Bild, α / (1 + α), im Verhältnis zum Tag bei 1 000 cd/m² (Bezug 0,12 [G]); der Bezug vor der Filmkurve wird daraus zurückgerechnet; Stäbchen σ(Y) = 0,04 / (0,04 + Y) mit Blauverschiebung (1,05; 0,97; 1,27) in der Ausgabe, vor der Kurve [G] |
| `Filmkurve` | `Film.develop` (Vision3 250D auf 2383, ohne Korn, Halation, Bildstand, Flackern [G]) für die Karte: Matrix und Projektion genau, je Schicht eine Tabelle über log₁₀ H (2048 Werte von −7 bis 3) |
| `film.*` | Kopie von `com.dan.film` ohne `gl/` und `demo/` |
| `demo.FrameCheck` | Prüfung ohne Fenster |

## Benutzung

```java
try (Rahmen r = new Rahmen(g, konto, 1920, 1080)) {
    Ablauf a = new Ablauf(g, g.grafik);
    r.bild(a, List.of(gelaende, wasser, himmel), blick, zeit, ziel, dt);   // ziel: Bild der Swapchain oder eigenes
    // a in einen Befehlspuffer der Grafik-Warteschlange aufnehmen und abschicken (Flaeche.bild oder g.grafik.einmal)
}
```

Das Histogramm eines Bilds liest der Rahmen zwei Bilder später aus einem geteilten Puffer, ohne zu warten; das setzt voraus, dass höchstens zwei Bilder zugleich unterwegs sind (so hält es `core.gpu.Flaeche`).

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.frame.demo.FrameCheck
```

Teile: 1 Blick (Lage relativ zum Auge in float bis 30 km, Projektion gegen den Bildpunkt in double, Tiefe umgekehrt, Strahl), 2 Filmkurve gegen `Film.develop`, 3 Ausgabe auf der Karte (RGBA32F gegen dieselbe Rechnung und gegen `Film.develop`, sRGB gegen linear), 4 Histogramm gegen Java und Belichtung, 5 Messen auf Gelände und Wasser (beide Histogramme und die Wahl gegen Java, Gegenlicht nachgestellt), 6 Sehen bei Nacht (Bezug, Ausgabe mit Stäbchen gegen Java), 7 Aufräumen. Attest `docs/status/P6_Frame.md`.

## Grenzen

- Keine zeitliche Rekonstruktion (8.1); ob sie gebraucht wird, entscheidet Prüfstein 8 (12.7).
- Kein Glühen, keine Linse, kein Verschluss; sie kommen mit dem Fotomodus.
- Die Belichtung misst nur das Bild; die Vorbelichtung aus dem Himmel (`exposure.PreExposure`) folgt mit render.air, wenn ihr Nutzen gemessen ist.

## Quellen

- Werkbuch HalligHooge, Teil A, Abschnitte 8.1, 12.7.
- `com.dan.film` (Kurven nach den Datenblättern von Kodak, geformt [G]) und `com.dan.exposure` (Augenanpassung).

## Seit 0.3 (P11c2): Auflagen

`Rahmen.bild(a, teile, auflagen, blick, zeit, aus, dt)`: Die `core.gpu.Auflage`n bereiten nach den Teilen vor und legen
nach der Ausgabe durch die Kurve in `aus` auf; sie dürfen die Tiefe der Szene testen oder lesen. Ohne Auflagen ist das
Bild bitgleich zu 0.2 (die alte Form ruft die neue mit einer leeren Liste).
