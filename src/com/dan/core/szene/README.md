# com.dan.core.szene – die gemeinsame Szene

**Stand: P8c (16.11.2026); nachgezogen seit F61 (P7f–P7l, P8b, P8c).** Teil des Kerns, nur `java.*` und `core` (Werkbuch HalligHooge, Teil A, 12.8, F52 A und F53 A). Was das Echtzeitbild (`render.air`, `render.terrain`, `render.water`) und die Referenz (`render.reference`) beide rechnen, steht hier: dieselbe Quelle, nicht nur dieselben Werte (Leitidee 2). Module derselben Schicht kennen einander nicht (Schichtenvertrag, 4); darum liegt die Szene im Kern.

| Klasse | Inhalt | vorher |
|---|---|---|
| `Atmosphere` | Lufthülle: Rayleigh, Mie aus der Sicht, Ozon, Radien (Hillaire 2020, Bruneton 2017) | `render.air.streuung` |
| `Scattering` | Tabellen und Strahlengang auf dem Prozessor (Prüfung von `render.air`); seit P7e erste Streuung genau, ab der zweiten J aus `Streuordnungen` (Ψ nach Hillaire nur noch mit `hillaire = true`), dazu `radianceSpektral` (15 Wellenlängen nach sRGB) und die Bilder von J für die Karte; seit P10d `durchWieTabelle` (die Durchlässigkeit, wie die Karte sie bilinear aus der Tabelle liest, aus den vier Stützstellen) | `render.air.streuung` |
| `Streuordnungen` (P7e) | Quelle der Mehrfachstreuung J(h, µ, µ_s, φ) je Wellenlänge, Rayleigh und Mie getrennt, nach Bruneton (2017) in sechs Streuordnungen mit echten Phasenfunktionen und dem Boden; Gitter 24 h (100 km · x³, dicht am Boden) × 32 µ × 40 µ_s (ab −0,2, tiefer 0) × 32 φ, in µ_s und h logarithmisch gelesen; die Durchlässigkeit liegt als optische Tiefe in den Tabellen (seit F59 A); drei Kanäle oder 15 Wellenlängen zu 22 nm, nach linearem sRGB je nm mit dem Licht dazwischen quadratisch im Logarithmus (`nachRgb`, `spektralTafel`); float16, abgelegt unter `~/.hallighooge/luft/` | neu |
| `Lichtquellen` | Sonne, Mond, Nachthimmel: Örter aus `core.astro`, Brechung, Stärken je Kanal; seit P7k (F68 A) `SONNE_FARBE`, Stärke und Farbe der direkten Sonne aus dem Spektrum der Referenz (rund 1,145 / 1,020 / 0,984 auf 133 100 lx); seit P10d `farbeNachLuft`: der Faktor nach der Luft für die Höhe der Quelle (das Spektrum je nm durch die Luft, Beiwerte wie die Referenz, durch die Durchlässigkeit, die die Karte aus ihrer Tabelle liest; je Bild für Sonne und Mond, rund 0,02 ms je Quelle) | `render.air.Himmel` |
| `Wasseralbedo` | seit P10d (F105 A): Albedo des Wassers für den Himmel je Kanal, Fläche aus `wasseralbedo.bin` (gebacken von `render.reference.Wasseralbedotabelle`: Referenz über einer rauen Wasserkugel ohne Säule gegen Kugeln nach Lambert, 11 Höhen der Sonne × 5 Winde) plus Säule aus `Saeule`; seit F106 B in der Anwendung nicht in Gebrauch (bei tiefer Sonne schlecht bestimmt, Datei 47) | `render.air.Himmel` (nur mit `wasserOptik`) |
| `Boden` | Albedo je Rauheitsklasse, Struktur aus dem Luftbild, Klassen in 1 m und 10 m; `pixel` für die Referenz | `render.terrain.Material` |
| `Wasseroptik` | Pope und Fry, Schwebstoff, Fresnel, Cox und Munk, Wellen der Kräuselung | `render.water.Optik` |
| `Saeule` (P7d) | Tabellen der Wassersäule (`saeule.bin`, gebacken von `render.reference.Saeulentabelle`): L, C, T, U, R über u, Tiefe und Winkel (seit F61 A 32 Stufen in x, x_j = 40 (j/31)⁴, dicht im Flachen); die Tafel für `wasser.frag` je Bild | neu |
| `Flaeche` (P7g, F63 A) | die wirksame Wasserfläche: was die Neigungen, die ein Bildpunkt nicht auflöst, im Mittel über die sichtbaren Flanken tun (Beckmann mit σ² nach Cox und Munk, Schattung nach Smith wie `render.reference.Facette`): wirksame Spiegelung, Höhe und Breite des Spiegelbilds, Durchlass; seit P11d (A26) dazu die Höhe an den Quantilen 1/8, 3/8, 5/8, 7/8 (acht Werte je Stelle); als Tafel für `wasser.frag` und `licht.comp` | neu |
| `Seegangsnetz` (P8b) | ein Gitter des Seegangs für das Bild: Hs, Tp, Richtung (als Vektor), mss, diss aus `waves.*` in zwei Ständen, linear in der Zeit | neu |
| `Bodennetz` (P9b) | das Gitter des Sediments für das Bild: die optisch wirksame Trübung C_opt = Schlick + 0,05 · Sand (g/m³, aus `sed.c`) in zwei Ständen, und der Boden – Rippel (Höhe, Länge, Richtung als Vektor), Schlickanteil, Bodenänderung aus `bed.*` – in zwei Ständen mit eigenem Takt |
| `Seegangsoptik` (P8b, F76 A) | die 72 langen Wellen in 6 Bändern × 12 Richtungen, Tafel des JONSWAP und Gegentafel, Schaum; `neigung` (Varianz für Prüfstein 9). Seit P8c `werte` (die sechs Werte am Ort wie `seeWerte` im Shader) und `neigungAm` (die Neigung am Ort mit Phase, in double), damit die Referenz denselben Seegang sieht | neu |
| `Spiegel` | ein Gitter des Wassers: zwei Stände, Bauwerke, Schwellen (F47) | `render.water.Spiegel` |
| `Spiegelbild` | η_Bild für ein Auge und eine Zeit: Kopf des Shaders und dieselbe Regel in Java | `render.water.Wasserbild` |
| `spektrum.Spektrum` | 360–830 nm in 1 nm: CIE 1931, D65, Sonne (ASTM G173), Ozon, reines Wasser, Rayleigh nach Bodhaine, RGB → Spektrum nach Mallett und Yuksel | neu |

**Geprüft beim Umzug:** `AirCheck` 19, `FrameCheck` 28, `WaterCheck` 15 und `TerrainCheck` 13 Prüfsteine ohne Verstoß; `WaterCheck` und `TerrainCheck` geben vor und nach dem Umzug Zeichen für Zeichen dieselbe Ausgabe (bis auf Zeiten).

**Daten** (`spektrum/spektrum.txt`): CIE 1931 2° und D65 aus `colour-science` 0.4.7; ASTM G173-03 aus `pvlib` 0.16.1; reines Wasser nach Pope und Fry (1997), außerhalb 380–727,5 nm nach IOCCG (2018), aus github.com/raphidoc/aw (MIT); Ozon nach Serdyuchenko u. a. (2014) in 10 nm aus Bruneton (2017, BSD); Basis nach Mallett und Yuksel (2019) aus `colour-science`.

**Grenzen:** Mie ohne Gang mit der Wellenlänge (Ångström 0) wie im Echtzeitbild; Randverdunklung der Sonne grau; Nachthimmel mit der Form des Sonnenspektrums [G].

## Seit P12a: Bauten

`Bauten` beschreibt die Häuser eines Gebiets als Körper ohne Grafik (Ablage `bau/gebaeude.bin`, Schritt `gebaeude` der
Aufbereitung): Dreiecke mit Normale, Lage auf der Fläche (u, v, w für Fenster, Backstein und Halme) und Art (Wand, Dach),
je Haus Boden, Traufe, First, Dachart (Reet, Ziegel, flach), Albedo von Dach und Wand und die gemessene Farbe des Dachs
im DOP20. Raum wie die Clipmap des Geländes (x = Ost − e0, z = n0 − Nord). `Bauten.Bauer` zerlegt ebene Ringe
(`Zerlegung`: Normale nach Newell, Ohrenschneiden). Gelesen von `render.bau`, später von `render.reference` (P12c).

## Seit P12b: Gestalt

`Gestalt` ist die Karte der Gestalt (Ablage `gestalt/`, Schritt `gestalt`): Kacheln (`Kachel`: Südwestecke, 2 Byte je
Bildpunkt, Zeile 0 im Norden) mit Art (`FENNE` … `HOLZ`) und Wasserstand als Byte (`stand`, `standByte`:
w/50 − 1 m NHN, 0 = keiner). `bei(ost, nord)` gibt beides am Ort. Die Körper der Gestalt sind `Bauten` in
`bau/koerper.bin` (`Bauten.KOERPER`, Arten `HOLZ`, `STEIN`, `BETON`, `BUSCH`); `Bauten.zusammen` hängt sie an die Häuser.

## Seit P12c: Gehölze

`Gehoelz` sind die Gehölze eines Gebiets (Ablage `bau/gehoelz.bin`, Schritt `gehoelz`): je Gehölz Lage im Raum der
Bauten (x = Ost − e0, z = n0 − Nord), Boden (m NHN), Höhe über dem Boden, acht Halbmesser der Krone (Ost, dann gegen
den Uhrzeigersinn), Art (`ESCHE` … `HUNDSROSE`), Fläche. `kroneIn(a)` gibt den Halbmesser in einer Richtung.

**Zonen der Salzwiese (P13b).** `Zonen`: je Bildpunkt (1 m, Kacheln zu 500 m wie die Gestalt) Byte Zone (0 keine, 1 Pionierzone, 2 untere, 3 obere Salzwiese, 4 Fenne ohne Tidezone seit F155 A) und Byte Höhe über MThw (b / 100 − 1,28 m, 0 kein Wert); Ablage `vegetation/zonen/zonen.txt` und `E<ost>_N<nord>.rg8`. `zone(über, −0,40, +0,35)` teilt nach der Höhe; `aufGestalt(Gestalt)` gibt eine Gestalt mit den Arten 6 (Pionierzone), 7 (untere), 8 (obere Salzwiese), 9 (Fenne unten oder Pionier) für das Bild; `ohneZone(art)` führt sie auf 2 und 1 zurück.

**Schlick (F158 A).** `aufGestalt` macht Salzwiese der Karte unter der Pionierzone (Zone 0 mit Höhe) zur Art 13 `ART_SCHLICK`: im Bild Gelände ohne Gras und ohne Halme; `ohneZone(13)` ist wieder Salzwiese.

## Seit P13c (Brandung, Werkbuch 12.14)

| Klasse | Inhalt |
|---|---|
| `Kanten` | Böschungen der Brandung je 5 m: Fuß, Richtung zur See, Krone, tan α, γ_f; Ablage `brandung/kanten.txt` |
| `Brecherform` | Formen der Brecher im Querschnitt, unverändert aus `com.dan.surf.BreakerShape`; Typ nach Battjes |
| `Brandung` | je Bild Brecher als Netz, Auflauf nach EurOtop 2018, Bündel der Gischt; ohne Zustand, ohne Grafik |
| `Seegangsoptik` | dazu Höhe der langen Wellen, Kammform, Brechen, Schaumköpfe (Φ, Φ⁻¹), `D_KAL` |


## Spiegelbild: Saum (seit C1b, A52)

`Spiegelbild.SAUM` (Vorgabe an, `-Dwasser.saum=aus`): eine trockene Zelle zeigt am Rand zu einer nassen Seitenzelle deren Spiegel, wenn die Kante offen ist und unter dem Spiegel liegt. Siehe `render/water/README.md`.
