# com.dan.film – der Film

Das Paket bringt das Bild auf Film, wie es im Kino war: vom Licht der Szene über das Negativ und die Kopie auf die Leinwand. Es ersetzt die Filmkurve einer App (ACES oder Ähnliches) durch zwei echte Materialien hintereinander, je drei Schichten mit eigener Schwärzungskurve, mit der orangen Maske, der Nebenabsorption der Farbstoffe, dem Kopierlicht in Punkten, dem Korn nach Selwyn, der Halation aus dem Träger, dem Bildstand im Projektor und dem Flackern. Zwei Zeiten liegen bereit: **1958** (Eastman Color Negative 5248 auf Eastman Color Print 5382, CinemaScope) und **heute** (Kodak Vision3 250D oder 500T auf Vision Print 2383, Super 35). Wer will, sieht die Kopie von 1958 auch so, wie sie heute aus dem Archiv kommt: Gelb und Cyan verblasst, alles purpur.

Seit LituyaBay1958, Werkbuch Welle W9b „Der Film“ (07.11.2026).

Der Kern braucht nur Java 21. Der Ordner `gl/` rechnet mit JOGL (GL 4.3, drei Vollbild-Durchgänge). Das Paket kennt keine Szene: Es bekommt ein HDR-Bild in den Einheiten der Szene und die Belichtung der App, und schreibt das fertige Bild (sRGB) ins Ziel. Zum Herauskopieren reicht `src/com/dan/film/`.

## Physik (mit Quellen; [G] = gestaltet)

| Was | Wie | Quelle |
|---|---|---|
| Schwärzungskurve | je Schicht D(x) = D_min + γ (s_t ln(1 + e^((x − x_t)/s_t)) − s_s ln(1 + e^((x − x_s)/s_s))), x = log₁₀ H relativ zur Graukarte: gerades Stück mit γ, weicher Fuß und weiche Schulter, überall stetig und steigend. Negativ γ ≈ 0,6, Kopie γ ≈ 2,5–2,8; Systemgamma an der Graukarte 1,38 (Vision3) und 1,29 (5248) | Hurter und Driffield 1890; Kodak, Datenblätter 5207, 5219, 2383 (Form) [G, die Zahlen] |
| Spielraum | aus der Kurve: Vision3 14,6 Blenden, 5248 9,3 Blenden | wie oben |
| Schichten | Empfindlichkeit gegen lineares sRGB als Matrix (Zeilen auf 1); 5248 mit mehr Überlappung (weniger reine Farben) [G] | – |
| Orange Maske | D_min rot < grün < blau; sie gleicht die Nebenabsorption des Negativs aus, darum hat nur die Kopie eine | – |
| Kopie | log E der Kopie = −D_Negativ + b + Kopierlicht; b so, dass die Graukarte in jeder Farbe auf dieselbe Nettodichte fällt (Grün D = 1,0): auf der Leinwand neutral. Das Systemgamma je Farbe gleich (Negativ γ · Kopie γ), so bleibt der Graukeil neutral; bei 5248/5382 ein kleiner Rest [G] | Laboratory Aim Density (Kodak) |
| Kopierlicht | Punkte zu 0,025 log E, 25-25-25 neutral; ein Punkt mehr Rot → mehr Cyan in der Kopie (gemessen 0,0596 gegen γ · 0,025 = 0,0590) | Bell & Howell Model C, die Lichtbestimmung |
| Farbstoffe | Nebenabsorption der Kopie als Matrix, Zeilen auf 1 (Grau bleibt grau); Leinwand 10^(−(D − D_min)) | – |
| Alte Kopie | je Jahrzehnt verblasst ein Anteil jedes Farbstoffs; 5382: 12 % Gelb, 10 % Cyan, 3 % Purpur, 2383 rund 1 % [G, zu prüfen] → nach 68 Jahren ist Grau purpur | H. Wilhelm, *The Permanence and Care of Color Photographs* (1993) zur Richtung |
| Korn | Selwyn: σ_D · √A fest; die RMS-Körnigkeit gilt bei D = 1,0 für eine Blende von 48 µm (Vision3 250D 7, 500T 9, 5248 14, Kopie 3–4 [G]); ein Bildpunkt deckt die Fläche (Breite/px)·(Höhe/px) des Negativs, nie weniger als eine Farbstoffwolke (10 µm [G]); σ wächst mit √D [G]; je Schicht eigenes Korn, leicht verbunden (ρ ≈ 0,22) [G], jedes Filmbild neu. Anamorphotisch sind die Wolken im Bild doppelt so breit wie hoch | E. W. H. Selwyn 1935; Kodak, *Motion Picture Film* (RMS-Körnigkeit) |
| Halation | Licht, das bis in den Träger kommt, wird an seiner Rückseite gespiegelt und belichtet die rote Schicht von hinten. Kern ∝ R(θ) · cos⁴θ · 10^(−2 D_T / cos θ) im Abstand r = 2 t tan θ: Lambert (cos θ), Weg (1/d²), Neigung (cos θ); R gegen Luft nach Fresnel ((n − 1)/(n + 1))² = 3,75 % senkrecht, ab θ_c = arcsin(1/n) = 42,5° Totalreflexion → **Ring** bei r_c = 2 t tan θ_c = 0,233 mm (Vision3) und 0,256 mm (5248). Rückschicht aus Ruß (Vision3, „remjet“): keine Totalreflexion, kein Ring, nur ein schwacher Schein. Grau eingefärbter Träger (5248, D_T = 0,25 [G, zu prüfen]): dämpft die schrägen Wege (jenseits 2 r_c 12,5 % statt 31,5 %). Mit der Streuung der Schichten auf dem Rückweg gefaltet (0,1 r_c [G]). Anamorphotisch eine Ellipse 2 : 1 | Snellius, Fresnel; J. H. Altman, *The Theory of the Photographic Process* (Halation) |
| Bildstand | das Bild wandert im Fenster um 0,010 mm waagerecht, 0,016 mm senkrecht (glattes Rauschen, 0,45 s) [G] | SMPTE RP 116 (Größenordnung) |
| Flackern | die Belichtung schwankt je Filmbild um 0,6 % [G] | – |
| Leinwand → Bildschirm | Graukarte bei rund 20 % (sRGB 0,48), weiches Knie über 0,8 [G] | – |

## Was es kann

- **Material**: `Stock.vision3_250D()`, `vision3_500T()`, `eastmanColor5248()`, `print2383()`, `print5382()`; eigene als `record Stock(...)`.
- **Format**: `Format.SUPER35_239`, `CINEMASCOPE` (2× anamorphotisch), `ACADEMY`, `VISTAVISION` – aus ihm folgen das Korn (Fläche je Bildpunkt) und die Halation (Ring in Bildpunkten).
- **Lichtbestimmung**: `Grade` mit Punkten je Farbe und Dichte; `NEUTRAL`, `ABEND_1958`, `WELLE`, `MORGEN_DANACH`, `HEUTE_ABEND`, `NACHT`; aus Text `Grade.parse("24 25 26 +1")`, dazwischen `Grade.mix(a, b, t)` (etwa über den Sonnenstand).
- **Alter der Kopie**: `film.printAgeYears`.
- **Auf dem Prozessor**: `develop(r, g, b)` (der ganze Weg ohne Korn), `halate(...)` (die Halation genau wie auf der Grafikkarte), `grainSigma`, `weaveMm`, `flicker`.

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Curve` | die Schwärzungskurve: Dichte, Steigung, D_max, Spielraum, Umkehrung |
| `Stock` | ein Material: Kurven, Schichten, Farbstoffe, Korn, Träger, Halation, Verblassen |
| `Format` | das Bildfenster und die Stauchung |
| `Grade` | das Kopierlicht |
| `Film` | der ganze Weg und die Einstellungen (öffentliche Felder: Material, Format, Kopierlicht, Belichtung, Schalter, Stärken, Bildrate) |
| `gl/FilmGl` | die Grafik: `init`, `apply(gl, View)`, `frame()`, `halationSize()`, `dispose` |
| `demo/FilmDemo` | Graukeil, Farbtafel, Abendhimmel mit Sonne und Laterne in vier Spalten (ACES, Vision3, 5248, 5248 nach 68 Jahren); `--bild datei.png` |
| `demo/FilmCheck` | 26 Prüfungen ohne Grafikkarte |

## Einbau

```java
Film film = new Film();                  // Vision3 250D → 2383, Super 35, neutral, 24 B/s
film.negative = Stock.eastmanColor5248(); film.print = Stock.print5382();
film.format = Format.CINEMASCOPE; film.grade = Grade.ABEND_1958;
FilmGl filmGl = new FilmGl(film);
filmGl.init(gl);
// je Bild, statt der Filmkurve der App:
FilmGl.View v = new FilmGl.View();
v.hdrTexture = hdr; v.targetFbo = ziel; v.outWidth = w; v.outHeight = h;
v.exposure = belichtung;                 // wie vor der Filmkurve: Szene · Belichtung = Bild
v.timeS = zeit;                          // Filmbild = ⌊t · 24⌋: Korn, Bildstand, Flackern
filmGl.apply(gl, v);
```

Die Belichtung der App bleibt: Was sie auf die Graukarte (0,18) bringt, liegt im Negativ auf der Graukarte. `exposureStops` belichtet über oder unter.

## Prüfung

`java -cp build/classes:src com.dan.film.demo.FilmCheck` – Graukarte neutral und auf der Zieldichte, Systemgamma, Spielraum, orange Maske, Kopierlicht in Punkten, Selwyn, Halation (Fresnel, Ring, grauer Träger, Energie eines Lichts, Ellipse), Kopierlicht aus Text, Verblassen, Bildstand, Flackern. Endet mit „Alles in Ordnung.“ Auf der Grafikkarte (Mesa): `film.frag` gegen `Film.develop` 0,0019 in sRGB, die Halation gegen `Film.halate` 0,17 % im Mittel.

## Grenzen

- Die Kurven sind nach den Datenblättern geformt, nicht aus ihnen abgetastet [zu prüfen gegen die gedruckten Kurven].
- Die Schichten sehen sRGB-Primärlicht, nicht das Spektrum; Farben, die nur im Spektrum verschieden sind (Metamerie), bleiben gleich.
- Das Korn ist Rauschen in der Dichte mit der richtigen Streuung und Wolkengröße, keine einzelnen Körner.
- Kein Zwischenpositiv und Internegativ (die Kopie im Kino war die vierte Generation); ihre Kurven folgen, wenn sie gebraucht werden.
- Kein Verschluss: Bewegungsunschärfe und die Belichtung über 1/48 s kommen mit W9c.
