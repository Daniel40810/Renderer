# com.dan.render.air – der Himmel

**Stand: P7l, nachgezogen mit P8c (16.11.2026), Version 0.2.** Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, Abschnitte 8.1, 12.7 und 12.8). Der Himmel im Bild mit Sonnen- und Mondscheibe, die Himmelsansicht und die Luftperspektive für alles, was Gelände und Wasser gezeichnet haben. Sonne und Mond kommen aus `core.astro`.

**Seit P7e (F58 A):** Die erste Streuung rechnet der Strahlengang genau, ab der zweiten liest er die Quelle J(h, µ, µ_s, φ) aus `core.szene.Streuordnungen` (Rayleigh und Mie getrennt, nach Bruneton 2017 in sechs Streuordnungen mit den echten Phasenfunktionen und dem Boden gerechnet). Das ersetzt Ψ(h, µ_s) nach Hillaire, dem die Vorwärtsstreuung um die Sonne fehlte (−14 … −37 % zur Sonne und zur Kimm, Befund P7e). Die Sonne in der Himmelsansicht ist spektral (15 Wellenlängen zu 22 nm ab 390 nm in fünf Gruppen zu drei), nach linearem sRGB wie die Referenz; Mond, Luftperspektive und das Licht am Boden bleiben in drei Kanälen.

**Seit F62 A:** Die Himmelsansicht wird über fünf Bilder verteilt, je Bild eine Gruppe (ein Kernel, drei Wellenlängen der Sonne je Einheit Bestrahlung), und nach der fünften getauscht; so kostet sie je Bild ein Fünftel. **Seit P7l:** Bodenalbedo 0,23 (Wasser und Watt um Hooge, gemessen am Himmel der Referenz über der Szene); eine Änderung von Sicht oder Albedo rechnet die Tabellen neu. **Seit P7k (F68 A):** Die direkte Sonne hat Stärke und Farbe aus dem Spektrum (`core.szene.Lichtquellen.SONNE_FARBE`).

## Schicht

```
com.dan.render.air            core + core.gpu + LWJGL      Himmel (Bildteil), Kernels
com.dan.core.szene           (nur java.*)                 seit P7a (F52 A): Atmosphere, Scattering und Lichtquellen (Sonne, Mond, Nachthimmel)
com.dan.render.air.demo       dazu FStyle                  AirCheck
```

Die Rechnung auf der Karte steht in `/com/dan/core/gpu/luft.glsl`, nicht im Modul: Gelände und Wasser lesen dieselben Tabellen (Spiegelung des Himmels, Licht der Sonne am Boden), und die Module der Schicht `render` kennen einander nicht. `luft.glsl` rechnet Zeile für Zeile wie `Scattering`.

## Klassen und Kernels

| Teil | Aufgabe |
|---|---|
| `Himmel` | Bildteil. Vorbereiten: Werte des Bilds (Sonne, Mond, Blick) mit `vkCmdUpdateBuffer` in den Puffer; bei neuer Sicht oder Albedo J aus Java hochladen (drei Kanäle, fünf spektrale Gruppen mit ihrer Durchlässigkeit, Beiwerte und Gewichte) und die Durchlässigkeit der drei Kanäle rechnen; Himmelsansicht je Bild; legt alles im Bildziel ab. Zeichnen (als letzter Teil): `himmel.comp` |
| `durchlaessigkeit.comp` | 256 × 64 RGBA32F, optische Tiefe bis zur Oberkante (seit P7e; gelesen als e^−τ), 64 quadratische Schritte |
| `ansicht.comp` | 256 × 128 RGBA32F, cd/m²: Sonne in fünf Gruppen zu drei Wellenlängen, je 64 Schritte, nach sRGB je nm 360–830 (quadratisch im Logarithmus durch die drei nächsten Wellenlängen, Gewichte aus CIE 1931 und der Sonne nach ASTM); Mond in drei Kanälen (64 Schritte); Nachthimmel |
| `himmel.comp` | je Bildpunkt: Tiefe 0 → Himmel aus der Ansicht, Sonnenscheibe mit Randverdunklung, Mond als Lambert-Kugel; sonst HDR · T + Streulicht auf dem Weg (8 Schritte [G], J einmal je Lichtquelle in der Mitte des Wegs [G]) |
| `core.szene.Streuordnungen` | J je Wellenlänge (24 Höhen, 100 km · x³ × 32 µ × 40 µ_s ab −0,2 × 32 φ, Rayleigh und Mie; in µ_s und h logarithmisch gelesen), auf float16 gerundet, unter `~/.hallighooge/luft/` abgelegt; auf 16 Fäden rund 10 s je Wellenlänge beim ersten Mal [S], 18 Wellenlängen je Luft |
| `core.szene.Atmosphere`, `Scattering` | Kopie aus `sky` für die Prüfung auf dem Prozessor; seit P7a in der gemeinsamen Szene, die auch die Referenz liest |
| `core.szene.Lichtquellen` | Örter und Stärken von Sonne, Mond und Nachthimmel; `Himmel` legt sie in die Werte des Bilds |
| `demo.AirCheck` | Prüfung ohne Fenster |
| `demo.SonnenfarbeCheck` | seit P10d, ohne Karte: direkte Sonne durch die Luft, Echtzeit gegen spektral; Kosten |
| `jmisch.comp` | seit P10d: J zwischen den Tabellen für Wasser und Land gemischt; seit F105 A je Kanal bzw. Wellenlänge, auch über 1 hinaus |

## Im Bildziel abgelegt

| Name | Art | Inhalt |
|---|---|---|
| `Himmel/Durchlaessigkeit` | Bild | optische Tiefe (r, µ) bis zur Oberkante (seit P7e; T = e^−τ) |
| `Himmel/Mehrfachstreuung` | Bild | J der drei Kanäle, 2560 × 768 RGBA16F: x = m + 32 k (J_R) bzw. + 1280 (J_M), y = j + 32 i (seit P7e; bis P7d Ψ) |
| `Himmel/Ansicht` | Bild | Himmel vom Auge, cd/m² ohne Vorbelichtung |
| `Himmel/Werte` | Puffer | `Himmelswerte` (luft.glsl, std430) |
| `Himmel/Sonne`, `Himmel/Mond` | Zahlen | Richtung in der Szene, Beleuchtungsstärke außerhalb der Luft (lx), Höhe, Azimut (Mond: Phasenwinkel) |

## Festlegungen

- **Licht:** Die Sonne bringt außerhalb der Luft 127 500 lx je Kanal in 1 AE; das Ergebnis ist cd/m². Mond nach seiner Helligkeit wie `sky` (−12,73 + 0,026 |ψ| + 4 · 10⁻⁹ ψ⁴ + Abstand), Farbe 1,06 / 0,98 / 0,88; Nachthimmel 22 mag/″² im Zenit.
- **Richtungen:** wahr aus `core.astro` (mit Parallaxe), um die Meridiankonvergenz von UTM 32 ins Gitter gedreht (an Hooge −0,37°). Die Scheiben stehen um die Strahlenbrechung (Sæmundsson) höher. Seit P10d (F100 A) kommt auch das Licht aus der gehobenen Richtung (Streuung, Luftperspektive, Gelände, Wasser, Schatten, Säule), wie in der Referenz; bis P10c rechnete die Streuung mit der wahren. Die Zahlen `Himmel/Sonne` und `Himmel/Mond` tragen die gehobene Richtung und die scheinbare Höhe, dahinter die wahre.
- **Farbe der direkten Quellen (seit P10d):** `hw.farbe` (Sonne) und `hw.farbeM` (Mond, neu; `Himmelswerte` 56 → 60 Werte) sind je Bild der Faktor nach der Luft aus `Lichtquellen.farbeNachLuft` für die Höhe der Quelle: Gelände und Wasser rechnen weiter in drei Kanälen, am Boden kommt so das Licht der spektralen Rechnung an (Prüfstein 1 von P10d: auf 0,03 % von 60° bis 3,2°). Neu gerechnet, wenn sich die Höhe um mehr als 10⁻⁵ im Sinus ändert.
- **Genauigkeit in float:** Höhe als q = r² − R² geführt, Abstand zum Boden als q / (−r µ + √D); kein `acos` auf der Karte (auf llvmpipe auf 10⁻⁴ ungenau, das verschob die streifende Richtung der Mehrfachstreuung um 80 m). Die Tabellen werden ohne Filter der Karte bilinear von Hand gelesen.
- **Himmelsansicht** nur neu, wenn Sonne oder Mond um mehr als 0,01° wandern, ihre Stärke oder die Höhe des Auges sich um mehr als 10⁻³ ändern [G] (die Sonne wandert 0,01° in 2,4 s).
- **Sicht** 30 km [G] bis `atmos` sie rechnet, Bodenalbedo 0,08 wie `sky`. Ohne Wolken, ohne Kimmung (P10).

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.air.demo.AirCheck
```

Teile: 1 Sonne und Mond gegen Werte aus `sky.astro` (Prüfstein 4: ≤ 0,01°), Drehung ins Gitter, Brechung; 2 Tabellen (Durchlässigkeit, J) und Himmelsansicht gegen `Scattering` (spektral für die Sonne, Prüfstein 4: ≤ 10⁻³); 3 Himmel und Luftperspektive im Bild gegen dieselbe Rechnung auf dem Prozessor, 8 gegen 256 Schritte; 4 Sonnenscheibe (Licht über die Bildpunkte = E · T, Lage); 5 tiefe Sonne (seit P10d, Prüfsteine 2 und 3: der Himmel im Bild bei Sonne 3,2° und 1° gegen Java mit derselben Richtung, je Band der Höhe, dazu das Lesen der Ansicht getrennt); 6 Aufräumen, Validierung. Ohne Karte: `SonnenfarbeCheck` (seit P10d, Prüfsteine 1, 4 zum Teil und 8; Attest `docs/status/P10_Sonnenfarbe.md`). Attest `docs/status/P6_Himmel.md`. Gegen die Referenz prüft `render.reference.demo.LuftCheck` (Attest `docs/status/P7_Luft.md`).

## Grenzen

- Kein Milchstraßenbild, keine Sterne und Planeten, keine Halos und Regenbögen, keine Lichtglocken, keine Mondoberfläche und kein Erdschein (alles in `sky`); sie folgen, wenn das Bild sie braucht.
- Die Luftperspektive rechnet je Bildpunkt (8 Schritte, zwei Lichtquellen). Ob ein Volumen wie bei Hillaire nötig ist, zeigt die Zeit auf der 680M (Prüfstein 7).
- Der Mond ist eine Lambert-Kugel; sein Licht in der Streuung folgt der Helligkeit, seine Scheibe dem Lambert-Gesetz (bei Vollmond gleich).

## Quellen

- Werkbuch HalligHooge, Teil A, Abschnitte 8.1, 12.7.
- S. Hillaire, „A Scalable and Production Ready Sky and Atmosphere Rendering Technique“, EGSR 2020; E. Bruneton, „Precomputed Atmospheric Scattering“, 2017.
- J. Meeus, *Astronomical Algorithms*, 2. Aufl. (Kap. 16, 40, 48); C. W. Allen, *Astrophysical Quantities* (Helligkeit des Monds).


## Seit P10b: die Kimmung (`Kimmung` 0.1, F93 A, F94 A, F95 A)

`Kimmung` ist ein zweiter Bildteil des Moduls, der letzte im Bild (nach Gelände, Wasser und Himmel). Lichtstrahlen laufen durch die Luft über dem Wasser in Bögen, deren Krümmung das Gefälle der Brechzahl gibt (`air.n` aus `physics.atmos`; das Modul kennt nur ein Profil n − 1 über der Höhe, `luft(hoehen, nMinus1, flaeche)` – die Schicht `lauf` gibt es ihm). Über warmem Wasser sinkt die Kimm, darüber steht das Spiegelbild der Ferne; über kaltem hebt sich die Ferne.

| Teil | Aufgabe |
|---|---|
| `Kimmung.tafel(...)` | nur java.*: für 1 024 Sehwinkel zwischen ± 0,5° [G] je ein Strahl vom Auge durch das geschichtete Profil; in jeder Schicht ist das Gefälle fest, der Strahl eine Parabel, z″ = 1/R + dn/dz. Die Tafel hält je Sehwinkel und Entfernung (256 Werte, geometrisch 50 m bis 100 km), um wie viel der Strahl im gesenkten Bild über der Geraden liegt (δ = y / d − tan β), dazu je Sehwinkel die Entfernung, in der er die Fläche erreicht (Sperrhöhe, F93 A), die Richtung am Ende und die Grenzen von δ. Rund 30 ms |
| `Kimmung.durchgang`, `punkt` | derselbe Durchgang wie der Kernel, auf dem Prozessor (Prüfung) |
| `kimmungkopie.comp` | kopiert die Zeilen des Streifens (und 160 darüber und darunter) des HDR-Bilds in ein zweites Bild |
| `kimmung.comp` | je Bildpunkt im Streifen: von unten nach oben durch die Zeilen der Spalte, die der Strahl überstreicht; jede zeigt im geraden Bild einen Punkt in der Entfernung d (aus der Tiefe). Liegt der Strahl dort nicht mehr über ihm, ist er zwischen dieser und der vorigen Zeile getroffen; erreicht er vorher die Fläche, zeigt er das Wasser dort; trifft er nichts, den Himmel in seiner Richtung am Ende. Die Farbe kommt aus der Kopie, linear zwischen zwei Zeilen. Bildpunkte, deren Strahl nirgends ein Viertel einer Zeile abweicht, bleiben |

In der Normalatmosphäre (k = 0,13, mit der Gelände und Wasser gesenkt sind) ist δ = 0 und das Bild bleibt bitgleich. Die Tafel geht mit `vkCmdUpdateBuffer` in Stücken zu 64 KB im Ablauf des Bilds auf die Karte; niemand wartet. Neu gerechnet wird sie, wenn sich das Profil, die Höhe des Auges über der Fläche (um 5 mm) oder die Sperrhöhe ändert.

**Grenzen.** Die Luft gilt im Blickfeld als waagerecht geschichtet (ein Profil); zu sehen ist nur, was das gerade Bild zeigt – was erst die Krümmung über die Kimm hebt, fehlt; eine Sperrhöhe für alles (über trockenem Watt gilt dieselbe wie über Wasser); die Tiefe des Bilds bleibt die des geraden Bilds; die Spalten des Bilds gelten als Ebenen gleicher Richtung (keine Rolle der Kamera, Neigung klein gegen den Bildwinkel im Streifen). Die Scheiben von Sonne und Mond stehen weiter mit der Strahlenbrechung der Normalatmosphäre; im Streifen verschiebt die Kimmung sie wie alles andere.

**Prüfung.** `com.dan.lauf.demo.KimmungBildCheck` (ohne Karte: Tafel gegen die Strahlgleichung, Normalatmosphäre, untere Spiegelung, Durchgang gegen die Referenz mit gekrümmten Strahlen), `KimmungKarteCheck` (Kernel gegen den Prozessor, im Gebiet, Kosten).

**Albedo aus der Szene (seit P10d, Messung d):** `Himmel.wasserAnteil` (Vorgabe NaN: wie bisher `albedo`) setzt die Anwendung je Bild (`lauf.Bild`: Anteil der Zellen mit Wasser im Umkreis von 15 km). Die Albedo der Luft ist dann 0,06 + 0,17 · (1 − Anteil). J wird nicht neu gerechnet, sondern auf der Karte zwischen den Tabellen für Wasser (0,06) und Land (0,23) gemischt (`jmisch.comp`; beide Sätze beim ersten Mal gerechnet und unter `~/.hallighooge/luft/` abgelegt, 12 Bilder zu 15,7 MB). Neu gemischt, wenn sich die Albedo um mehr als 0,003 ändert; die Ansicht folgt dann verteilt über fünf Bilder. Linear in der Albedo auf 1,2 · 10⁻³ (bezogen auf den größten Wert, gemessen bei 0,145). Prüfung: `AirCheck` Teil 6. Gemessen (Datei 44): mit 0,23 über Wasser ist die Luft auf dem Weg nach unten 2- bis 3,5-mal zu hell.

**Albedo des Wassers (seit P10d, F105 A):** Die 0,06 über Wasser zählten nur die Spiegelung für Licht von überall; Lauf C zeigte den Himmel über viel Wasser dann 10 % zu dunkel (Datei 46). Mit `Himmel.wasserOptik` und `Himmel.wind` (setzt `lauf.Bild` je Bild) ist die Albedo über Wasser je Kanal die von `core.szene.Wasseralbedo`: Fläche (gebacken mit der Referenz: Himmel über einer rauen Wasserkugel ohne Säule gegen Kugeln nach Lambert, je Höhe der Sonne – bei Nacht des Monds – und Wind) plus Säule (aus der Tafel, Licht des Himmels von überall, 3 m tief), höchstens 0,6 [G]. Die Albedo der Szene ist Anteil · Wasser + (1 − Anteil) · 0,23 je Kanal; J wird je Kanal gemischt (in den Gruppen der Himmelsansicht je Wellenlänge, linear zwischen 440, 550 und 680 nm), auch über 0,23 hinaus. Die Luft der Werte (`luft()`, eine Zahl) trägt das Mittel nach Y. Neu gemischt, wenn sich ein Kanal um mehr als 0,003 ändert. Prüfung: `AirCheck` Teil 6 (je Kanal), `render.reference.demo.WasseralbedoCheck`, `lauf.demo.ReferenzCheck` Prüfstein 19. **Seit F106 B ist die Mischung in der Anwendung aus** (`lauf.Bild.ALBEDO_AUS_ANTEIL = false`): Wasser ist für den Himmel kein Boden nach Lambert – die gleichwertige Albedo reicht je nach Sonne, Wind und Richtung von 0,02 bis über 1, nach unten wirkt es dunkel, zur Kimm hell (Datei 47). Der Himmel rechnet wieder mit 0,23 fest; die Mischung bleibt im Code und in `AirCheck` geprüft. J mit spiegelndem Wasser geht nach P11.
