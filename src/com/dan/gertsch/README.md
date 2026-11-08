# com.dan.gertsch – Bäume und Gras nach Franz Gertsch (Probe)

**Stand: Phase 1 (08.11.2026).** Ein Probe-Paket. Es soll Bäume und Gras so zeichnen, wie Franz Gertsch (1930–2022) sie gemalt und geschnitten hat:

- jedes Blatt ein flacher Fleck mit harter Kante
- wenige Tonstufen
- ein fast schwarzer Grund
- warme Lichtflecken
- kein Himmel und keine Tiefenschärfe

Bei Gertsch entsteht das Abstrakte aus der Treue zum Foto, nicht aus einem Filter. Darum rechnet das Paket zuerst nach, was die Bilder ausmacht, und vergleicht später jedes gerenderte Bild mit diesen Zahlen.

Phase 1 vermisst die Vorlagen und legt die Ergebnisse als Presets ab (`Vorlagen`). Gezeichnet wird ab Phase 2.

Der Kern braucht nur Java 21, `demo/` zusätzlich FStyle. Zum Herauskopieren reicht `src/com/dan/gertsch/`.

![Kennblätter der sechs Vorlagen](../../../../docs/img/gertsch.png)

## Die Vorlagen

Die Bilder liegen lokal in `templates/` und sind **nicht im Repository** (`.gitignore`). Es sind Werke von Franz Gertsch und urheberrechtlich geschützt. Im Repository stehen nur die gemessenen Kennwerte, und die Tafel oben enthält keine Bildpunkte der Werke.

| Vorlage | Datei | Grund (L* < 20) | Median L* | Farbton | Akzente | Fleck hell | Lücke dunkel | senkrecht | Kohärenz | Hauptrichtung |
|---|---|---|---|---|---|---|---|---|---|---|
| Herbst | `franz-gertsch1.jpg` | 7 % | 50 | 70° ocker | 0,0 % | 0,80 % | 0,77 % | 51 % | 0,51 | 94° |
| Winter | `franz-gertsch2.png` | 16 % | 53 | unbunt (C* 3) | 0 % | 1,33 % | 1,35 % | 47 % | 0,60 | 114° |
| Gräser | `franz-gertsch3.jpg` | 40 % | 32 | 108° gelbgrün | 0,6 % | 3,72 % | 3,69 % | 50 % | 0,95 | 102° |
| Sommer | `franz-gertsch4.jpg` | 29 % | 32 | 125° grün | 0,5 % orange | 0,64 % | 1,17 % | 24 % | 0,50 | 179° |
| Holzschnitt | `franz-gertsch5.jpg` | 0 % | 46 | 217° blau | 0 % | 0,81 % | 2,17 % | 44 % | 0,66 | 84° |
| Blätter | `franz-gertsch6.jpg` | 32 % | 33 | 118° grün | 1,7 % rotbraun | 1,06 % | 1,97 % | 40 % | 0,69 | 106° |

Fleckgrößen sind Durchmesser als Anteil der Bildbreite (Median nach Fleckmasse). Bei 1920 Bildpunkten Breite sind die Laubflecken im Sommer also etwa 12 px groß, die Halme der Gräser etwa 71 px breit.

`franz-gertsch2.jpg` ist ein WebP-Bild mit falscher Endung, und `ImageIO` liest kein WebP. Gemessen wird deshalb die PNG-Kopie `franz-gertsch2.png` mit 474 × 321 px. Das ist sehr klein, die Fleckgrößen sind dort grob.

**Was die Zahlen für Phase 2 sagen:**

- **Sommer:**
  - Fast ein Drittel der Fläche ist dunkler Grund.
  - Sechs Tonstufen liegen fast gleichmäßig verteilt (L* 12, 23, 35, 49, 65, 80).
  - Die dunklen Lücken sind fast doppelt so groß wie die hellen Flecken.
  - Die Laubflecken liegen eher **waagerecht** (Hauptrichtung 179°, nur 24 % senkrecht). Das war nicht zu erwarten und wird ein Ziel für die Form der Flecken.
- **Gräser:** fast nur Linien (Kohärenz 0,95), Halme rund fünfmal so breit wie Laubflecken, 40 % Schwarz.
- **Herbst:** kaum dunkler Grund, dafür feine Flecken in hellem Ocker und senkrechte Zweige.

## Was es kann

- **Lab** (`Lab`): sRGB ↔ CIELAB (D65), ΔE*ab, Buntheit, Farbton (CIE 15:2004, IEC 61966-2-1).
- **Arbeitsbreite** (`Bild.aus`): verkleinert eine Vorlage durch Mittel im linearen Licht. Die Werte für `Quality` LOW bis ULTRA:

  | Quality | Breite |
  |---|---|
  | LOW | 400 px |
  | MEDIUM | 640 px |
  | HIGH | 1000 px |
  | ULTRA | 1600 px |
- **Tonstufen** (`Palette.kmeans`): k-means in Lab mit Start nach k-means++, aufsteigend nach L*, mit Flächenanteil; `naechste(L, a, b)` ordnet eine Farbe ihrer Stufe zu.
- **Helligkeit:** Verteilung von L* in zehn Fächern, Perzentile 5/50/95, Anteil des dunklen Grunds (`grundL` = 20) [G].
- **Farbe:** Der herrschende Farbton ist das Mittel der Farbvektoren (a*, b*), damit nach Buntheit gewichtet. **Akzente** sind bunte Bildpunkte (C* ≥ 15), deren Farbton mehr als 60° davon abweicht [G], also etwa die orangen Punkte im Grün.
- **Fleckgrößen** (`Morphologie`): Granulometrie nach Matheron und Serra mit Scheiben auf einer Leiter von Radien (1, 2, 3 … ×1,25 bis 3,5 % der Breite [G]).
  - Öffnungen geben die hellen Flecken, Schließungen die dunklen Lücken.
  - Minimum und Maximum je Zeile rechnet der Algorithmus von van Herk / Gil–Werman.
  - Ergebnis sind Summenverteilung, Median und der Anteil, der in Flecken statt in großen Flächen steckt.
- **Richtung** (`Struktur`): Strukturtensor (σ_D = 1, σ_I = 2 Bildpunkte [G]). Er liefert Energie je 15°, Anteil senkrecht (60°–120°), Kohärenz (0 = nur Flecken, 1 = nur Linien) und die Hauptrichtung als Mittel über den doppelten Winkel.
- **Presets** (`Vorlagen`): `herbst()`, `winter()`, `graeser()`, `sommer()`, `holzschnitt()`, `blaetter()`, `nachName(…)`, `alle()`. Die Datei ist erzeugt (`GertschDemo --quelltext`), nicht von Hand geschrieben.
- **Offene Werte, reproduzierbar:** Alle Einstellungen der `Messung` sind öffentliche Felder. Gleicher `seed`, gleiche Messung.

## Benutzung

```java
Messung m = new Messung();                         // HIGH: auf 1000 px, 6 Tonstufen, seed 1
Vorlage v = m.messen(new File("templates/franz-gertsch4.jpg"));
System.out.println(v.bericht());

Vorlage ziel = Vorlagen.sommer();                  // gespeichert, ohne Bild
double fleckPx = ziel.hellD50 * 1920;              // ≈ 12 px bei 1920 px Breite
int stufe = ziel.palette.naechste(L, a, b);        // Tonstufe für eine Farbe
float[] flecken = Messung.lichtflecken(v.bild, 6); // helle Flecken bis Radius 6 (Zylinderhut)
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Lab` | Farbumrechnung sRGB ↔ CIELAB, ΔE, Buntheit, Farbton |
| `Bild` | Bild in Lab (drei `float`-Ebenen), Verkleinern auf die Arbeitsbreite |
| `Palette` | Tonstufen: k-means, nächste Stufe, Bild in Stufen |
| `Morphologie` | Erosion, Dilatation, Öffnung, Schließung mit der Scheibe; Summen für die Granulometrie (parallel) |
| `Struktur` | Strukturtensor, Gauß-Glättung |
| `Messung` | Einstellungen, Qualitätsstufen, Leiter; `messen(…)` liefert eine `Vorlage`; Zylinderhut für Lichtflecken und Lücken |
| `Vorlage` | Kennwerte (offene Felder), `bericht()`, `alsJava(…)` |
| `Vorlagen` | die sechs gemessenen Vorlagen als Presets (erzeugt) |
| `demo/GertschDemo` | Fenster und Standbilder, Erzeugen von `Vorlagen.java` |
| `demo/Kennblatt` | zeichnet die Kennwerte einer Vorlage |
| `demo/GertschCheck` | Prüfung ohne Fenster |

## Demo und Selbsttest

```bash
java -cp "build/classes:lib/*:src" com.dan.gertsch.demo.GertschDemo
```

Das Fenster zeigt links die Vorlage in sechs Ansichten:

- Original
- Tonstufen
- Lichtflecken
- dunkle Lücken
- Richtungen (Farbe = Winkel)
- Akzente

Rechts steht das Kennblatt. Unten wählt man Vorlage, Ansicht, Zahl der Tonstufen (3–10) und Qualität. Fehlt die Vorlage, zeigt das Kennblatt die gespeicherten Werte.

```bash
java -cp "build/classes:lib/*:src" com.dan.gertsch.demo.GertschDemo --bild gertsch.png
```

```bash
java -cp "build/classes:lib/*:src" com.dan.gertsch.demo.GertschDemo --bild sommer.png sommer
```

```bash
java -cp "build/classes:lib/*:src" com.dan.gertsch.demo.GertschDemo --quelltext src/com/dan/gertsch/Vorlagen.java
```

```bash
java -cp "build/classes:lib/*:src" com.dan.gertsch.demo.GertschCheck
```

- `--bild datei.png` schreibt die Tafel aller sechs Kennblätter (nur Zahlen).
- `--bild datei.png sommer` misst eine Vorlage neu und schreibt vier Ansichten mit Kennblatt. Das Bild enthält das Werk, ist also nur für den eigenen Gebrauch.
- `--bericht` gibt die Kennwerte als Text aus.
- `--quelltext` misst alle Vorlagen und schreibt `Vorlagen.java` neu.
- `--vorlagen ordner` gibt an, wo die Bilder liegen (Standard `templates`).

`GertschCheck` prüft:

- Lab an festen Punkten und im Hin und Zurück
- Erosion und Dilatation Punkt für Punkt gegen die direkte Rechnung
- Scheiben bekannter Größe (5, 9 und 13 px werden genau getroffen)
- Streifen bekannter Richtung (0°, 45°, 90°)
- Rauschen ohne Richtung
- k-means auf vier bekannten Farben
- Akzente und Grund mit bekannten Anteilen
- die Form der Presets und ihre Kennzeichen, etwa „Gräser sind Linien“ oder „Sommer hat orange Akzente“

Liegen die Bilder in `templates/`, misst die Prüfung sie neu und vergleicht mit den Presets. Sie endet mit „Alles in Ordnung.“

## Grenzen

- Die Vorlagen sind Fotos von Werken in sehr verschiedener Auflösung (Winter nur 474 px breit), mit JPEG-Spuren. Der helle Rand mancher Aufnahmen zählt mit.
- Die Granulometrie misst Inkreise: Ein langer, schmaler Fleck zählt nach seiner Breite.
- k-means teilt nach Flächen, nicht nach Gertschs Mischungen. Seltene Farben bekommen keine eigene Stufe; dafür gibt es die Akzente.
- Akzente werden nur über den Farbton erkannt.
- Holzschnitt: Die gestochenen Punkte sind in der Vorlage 1–2 px groß und liegen damit unter der Arbeitsbreite. Die Punktdichte über dem Tonwert misst erst Phase 4.

## Weiter

1. ~~Vorlagen vermessen~~ (diese Phase).
2. Standbild „Sommer“ auf der CPU:
   - ein Testdickicht aus `seed`, ein Fleck je Blatt
   - die Fleckgröße im Bild als Regel, in der Ferne zusammengefasst
   - Tonstufen aus `Vorlagen.sommer().palette`, heller Saum
   - die Messung des Ergebnisses gegen das Preset als Prüfung
3. `gl/`: Flecken und Halme instanziert, Lichtflecken über die Schattenkarte, Hysterese der Stufen im Wind.
4. Holzschnitt: Punkte nach Blue-Noise-Rang, eine Farbe, senkrechte Wolken im Farbauftrag.
5. Eine Schnittstelle für Blätter und Halme von außen, damit eine App echte Kronen aus `forest` und Gras aus `reed`/`ground` hineinreichen kann. Das Paket kennt sie selbst nicht.
