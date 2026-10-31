# com.dan.cliff – Steilwände

**Stand: Engine (Phase 5 von Geiranger), zuletzt Timmendorf Phase 9 (Voreinstellung `brodtenerUfer()`).** Fels als Material und Feinform für jede Gebirgsszene: Gneis in Bändern entlang der Schieferung, Klüfte, Feinform, die die Wand im Scheitelshader verschiebt, Flechten, Moos, Sickerstreifen, Nässe nach Regen, Schnee auf Absätzen, Geröll am Fuß, nasse Streifen unter Fällen und Überhänge als Zusatzgeometrie. Das Package kennt keinen Fjord: Höhenfeld, Nässe, Schnee und die Lage der Fälle kommen von außen. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` FStyle. Zum Herauskopieren reicht `src/com/dan/cliff/`; in Phase 18 geht es nach `Renderer.jar`.

## Was es kann

- **Keine Streifen an senkrechten Stellen:** Alles ist 3D-Rauschen am Ort, keine Projektion auf Ebenen (auch keine dreiachsige). An einer senkrechten Wand ändert sich die Farbe hoch und quer gleich stark (Prüfung: Verhältnis 1,2).
- **Gneis:** Bänder entlang einer schwach geneigten Schieferung (10° nach Nordwest [G]) in drei Maßstäben (1,6 m, 7 m, 33 m), mit 3D-Rauschen verbogen, dazu rötliche Feldspatlagen und Korn.
- **Klüfte:** zwei senkrechte Scharen (Streichen 30° und 115°, Abstand 9 m [G]) als dunkle Fugen, die mal offen, mal geschlossen sind.
- **Feinform:** Relief entlang der Normale in bis zu vier Oktaven (24 m, 8 m, 2,7 m, 0,9 m; Höhe 4 m), gratig wie verwitterte Bänke, je Oktave gedreht gegen Kästchen im Gitter. Der Scheitelshader verschiebt die Wand bis `Quality.reliefRange`; der Bildpunkt neigt die Normale aus demselben Relief. Feine Oktaven blenden mit der Bildpunktgröße aus (nichts flimmert, in 2 km bleibt die Grobform).
- **Wandkarte** (`CliffMap`): je Zelle Wand, Geröll (unter einer Wand von mindestens 25 m im Umkreis von 110 m), Nässe (Streifen unter Fällen: `addFall` geht vom Fuß bergauf bis zur Kante, unten breiter) und Absatz (flache Stellen in der Wand für Moos und Schnee). Gerechnet einmal je Höhenfeld auf dem Prozessor, als RGBA8 auf die Grafikkarte.
- **Bewuchs und Wetter:** Flechten auf trockenem, steilem Fels; Moos auf nassen Absätzen; Sickerstreifen; nach Regen dunkel und glänzend; Schnee auf Absätzen und flachen Stellen.
- **Überhänge** (`Overhang`): eine Felsnase vor der Wand, oben am weitesten, unten hohl, mit Rauschen; die Rückseite steckt 4 m in der Wand. Gezeichnet mit demselben Material.
- **„Wand hier?“:** `Cliff.isWall(Neigung)`, `Cliff.wallAt(Höhenfeld, x, z, e)` für `fall` und `forest`.
- **Reproduzierbar und schnell:** ganzzahliger Hash (pcg3d) auf einem Gitter, das sich nach 64 Zellen wiederholt. Die Grafikkarte holt die Gitterwerte aus einer 3D-Textur 64³ (R16, Einheit 15): ein Abruf je Rauschwert statt acht Hashes, die quintische Glättung steckt in der Stelle des Abrufs. Die Normale kommt aus den Ableitungen im Bild (ein Reliefwert statt vier); ohne Fels und Schnee rechnet der Bildpunkt nichts. Der Selbsttest vergleicht 64 Punkte (Farbe, Relief); die Filterung der Grafikkarte rundet die Gewichte, Abweichungen von Bruchteilen eines Prozents sind normal.
- **Stufen:** `LOW` (2 Oktaven, ohne Überhänge, Feinform bis 600 m), `MEDIUM` (3, bis 1,5 km), `HIGH` (3, bis 2,5 km), `ULTRA` (4, bis 4 km).

## Benutzung

```java
Cliff cliff = new Cliff();
CliffMap map = CliffMap.build(cliff, terrain::height, x0, z0, 12, 2048);
map.addFall(fussX, fussZ, 400, 30, 1);              // nasser Streifen unter einem Fall

CliffGl gl = new CliffGl();
gl.init(gl4);
gl.upload(gl4, map);
gl.setOverhangs(List.of(new Overhang(x, y, z, 180, 46, 26, 11, 7)));

// im eigenen Shader: Include-Zeile mit dem Pfad /com/dan/cliff/gl/cliff.glsl, dann
//   vec3 p = cliffDisplace(world, normal, footprint, dist, range);          (Scheitelshader, vorher #define CLIFF_VERTEX)
//   CliffSample s = cliffMaterial(world, normal, rainWet, snow, footprint); (Bildpunkt)
gl.bind(gl4, program, cliff, 14);                   // Uniforms, Wandkarte auf Einheit 14, Rauschtextur auf 15
gl.drawOverhangs(gl4, cliff, view, light);          // Zusatzgeometrie in das gebundene Ziel
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `CNoise` | Hash und Wertrauschen (3D, 1D), Ausblenden nach Bildpunktgröße |
| `Cliff` | Eingänge, Stufen, Relief, Material, „Wand hier?“ |
| `CliffMap` | Wandkarte: Wand, Geröll, Nässe unter Fällen, Absatz |
| `Overhang` | Überhang als Netz |
| `gl/cliff.glsl` | Relief und Material Zeile für Zeile wie der Kern |
| `gl/CliffGl` | Uniforms, Wandkarte, Überhänge und Wand als Netz (`rock.vert`, `rock.frag`), Selbsttest (`check.vert`, `check.frag`) |
| `demo/DemoWall` | die Wand der Vorführung als Höhenfeld |
| `demo/CliffScene`, `demo/CliffDemo` | Vorführung mit Reglern; `--bild datei.png [breite]` rechnet sechs Ansichten |
| `demo/CliffCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `com.dan.cliff.demo.CliffDemo`: eine Wand von 300 m mit Absatz, Überhang, nassem Streifen und Geröll. Regler für Feinform, Kluftabstand, Flechten, Moos, Nässe, Schnee, Sonnenhöhe und -richtung, Stufe; „Aus 2 km“, „Aus 50 m“. Unten die Zeit des Felses auf der Grafikkarte.
- `CliffDemo --bild datei.png` rechnet die Wand aus 2 km, aus 50 m, senkrecht von der Seite, am Abend, nach Regen und im Mai mit Schnee.
- `CliffCheck`: 19 Prüfungen ohne Grafikkarte.
- `CliffGl.selfCheck`: 64 Punkte auf der Grafikkarte gegen den Prozessor (auf Mesa: Farbe ± 0,00 %, Relief ± 0,0 mm).

## Grenzen

- Nischen hinter Fällen (Hohlräume in der Wand) kann ein Höhenfeld nicht; sie kommen in Phase 7 mit den Fällen in der Szene als Zusatzgeometrie hinter dem Schleier.
- Die Feinform verschiebt die Wand entlang der Normale des Höhenfelds; Kanten werden dadurch rauer, aber nicht schärfer als das Raster.
- Die Feinform blendet dicht vor dem Auge (unter 40 m) aus, damit die Wand nicht über ein Auge wächst, das knapp über dem Höhenfeld steht.
- Schatten der Feinform fehlen (die Schatten der Wände kommen aus dem Höhenfeld der App).
- Farben und Maße sind gesetzt [G], nach den Fotos von Geiranger (Gneis) und vom Brocken (Granit, `Cliff.granit()`); Kalk oder Sandstein brauchen andere Werte.

## Seit Phase 7 von Geiranger

- `CliffMap.paintWet(x, z, breite, stärke)`: Nässe an beliebigen Punkten, etwa die nasse Linie aus `com.dan.fall` (die Wandkarte kennt den Fall nicht, nur die Punkte). `addFall` bleibt für Fälle ohne eigenen Weg.
- `Cliff.fallWet` (0..1): wie nass die Streifen unter den Fällen gerade sind, nach dem Abfluss; die Karte hält die Lage, dieser Wert die Stärke (Uniform `uCliffFallWet`, Prozessor und Grafikkarte gleich).
- Behoben: `CliffGl.upload` löschte beim Hochladen der Wandkarte die 3D-Rauschtextur; danach rechnete der Fels ohne Rauschen (Selbsttest ± 96 %).


## Seit Phase 8 von Brocken: Granit, Felsburgen, Blockmeer

**Was es kann.** `Cliff.rock = GRANIT` (Vorgabe `Cliff.granit()`) malt statt der Bänder des Gneises körnigen, ungebänderten Granit: Fleckung auf 3 m und 0,9 m, Feldspat (rosa, 3,5 cm) und Biotit (dunkel, 2 cm) im Korn, in der Ferne gemittelt, Flechten. Die Feinform sind **Wollsäcke**: zwei senkrechte Kluftscharen (`jointADeg`, `jointBDeg`, `jointSpacing`) und die waagerechte Bankung (`sheetSpacing`) zerlegen den Fels in Blöcke, die an den Fugen über `blockRound` des Abstands gerundet sind. Eine Kluft reißt nicht durch, sie ist stückweise offen, die Bankung ist verbogen und unterschiedlich dick. `Tor` baut eine **Felsburg** aus gestapelten Superellipsoiden (Teufelskanzel, Hexenaltar), `Blockfield` streut ein **Blockmeer** aus gerundeten Blöcken, halb im Boden.

**Benutzung.**
```java
Cliff granit = Cliff.granit();                       // Material und Feinform wie bisher: material(), relief(), CliffGl.bind
gl.setTors(List.of(new Tor(x, boden, z, 7, 6, 20, 3)));   // Fuß, Höhe 7 m, Breite 6 m, Ausrichtung 20°, Seed
gl.drawTors(g, granit, view, light);
Blockfield f = new Blockfield();
f.density = (x, z) -> karte.blockmeer(x, z);          // 0..1, sagt die App
f.ground  = gelände::height;
gl.drawBlocks(g, granit, f, view, light);             // die Blöcke um das Auge, instanziert; gibt die Zahl zurück
```
`material(..., force)` erzwingt Fels (für Netze, deren Neigung nichts über Fels sagt); der Shader hat dafür `uCliffForce`. `Cliff.topMoss` (0..1) legt Moos auf alles, was nach oben schaut (Blöcke im feuchten Wald). Auf Felsburgen und Blöcken setzt `CliffGl` `uCliffPillow` = 0: die Blöcke sind dort selbst die Wollsäcke (sonst lägen Fugen quer über einem Block). Wer `cliff.glsl` selbst einbindet, ruft `CliffGl.bind` (setzt 1).

**Aufbau.** `Cliff.granitBlock` (Java) und `cliffGranitBlock` (`cliff.glsl`) rechnen dasselbe: je Schar der Abstand zur nächsten Fuge, gerundet und mit der Öffnung gewichtet, multipliziert; in der Ferne das Mittel 0,79. `Tor.blocks()` legt Lagen von unten nach oben, nach oben schmaler und versetzt, oben eine flache, überstehende Platte; `Tor.mesh()` macht daraus ein Netz in Weltlage. `Blockfield` streut in Kacheln zu 32 m aus einem Seed je Kachel; `instances()` liefert die Blöcke im Umkreis, in der Ferne nur die großen, und dasselbe Feld, solange das Auge weniger als 8 m wandert. `gl/block.vert` macht aus einem Gitter (6 × 10 Felder) je Block den Superellipsoid; Material aus `rock.frag`.

**Grenzen.** Die Felsburg wirft keinen Schatten auf das Gelände (Sache der App). Die Blöcke des Blockmeers liegen auf dem Boden der App, nicht aufeinander; Blöcke über 450 m Abstand fallen weg (dort trägt das Material des Geländes). Wollsäcke an einer 300-m-Wand wirken regelmäßiger als in der Natur; der Brocken hat solche Wände nicht. Werte [G] nach Fotos von Teufelskanzel, Hexenaltar und den Blockmeeren am Brocken.

**Selbsttest.** `CliffCheck` prüft zusätzlich: Granit ohne Bänder, Wollsäcke (Anteil Fuge und voller Block), Bankung, Ausblenden in der Ferne, Felsburg (Höhe, Lagen, Normalen nach außen, Kugel bei e = 1, Seed), Blockmeer (Seed, Dichte, Zahl je Fläche, kein neues Feld unter 8 m Weg, weniger Blöcke bei gröberen Bildpunkten). `CliffDemo --bild datei.png 1500 --granit` zeigt Granit, Wollsäcke, Felsburg und Blockmeer und vergleicht Grafikkarte und Prozessor.

## Seit Phase 10 von Brocken: Eis am Fels

`CliffGl.Light` hat `rimeThickness` (m), `rimeKind` (0 keins, 1 Raueis, 2 Raureif, 3 Klareis), `rimeFromDeg` (woher der Wind kam) und das Höhenband `rimeBase`/`rimeFull` (darunter kein Eis, darüber die volle Dicke). `rock.frag` färbt danach Wände, Felsburgen und Blöcke: Raueis auf der Luvseite und an Kanten des Reliefs in Federn, mit der Dicke um die Kanten herum; Raureif überall, oben mehr; Klareis dunkel. Das Package rechnet kein Eis (das tut die App, etwa mit `com.dan.rime`). `CliffDemo` nimmt `-Draueis=cm` (Wind aus 250°).

Grenzen: nur Farbe, keine Form (die Blöcke wachsen nicht); `cliff.glsl` im Gelände anderer Apps bleibt ohne Eis, die App legt es dort selbst darüber.


## Seit Timmendorf Phase 9: Brodtener Ufer

`Cliff.brodtenerUfer()` ist Lockergestein ohne untere Schicht: graubrauner, fleckiger Geschiebemergel der Weichsel-Eiszeit bis zum Strand, oben Grasnarbe und brauner Waldboden, Findlinge, Runsen, Quellhorizonte, Geröll am Fuß. Weil das Gelände dort nur als 10-m-Raster vorliegt, gilt der Hang ab 30° als Fels und ab 42° als Wand. In Timmendorf bindet der Geländeshader `cliff.glsl` ein (Wandkarte, Schichtkarte über 2,6 × 2,6 km). Prüfung: `CliffCheck` „Brodtener Ufer: Mergel bis zum Strand …“.

## Seit Sylt Phase 4: Lockergestein

**Was es kann.** `Cliff.rotesKliff()` (Gestein `Rock.LOCKER`) malt ein Kliff aus Lockergestein nach einem **Schichtprofil**: von oben die Grasnarbe, Flugsand (Mächtigkeit je Ort), ein Podsol aus Bleichsand über dunklem Ortstein, die Steinsohle, dann der rostrote **Geschiebelehm** (fleckig, körnig, mit **Findlingen** und **Trockenrissen**, wenn er trocken ist) und unten heller **Kaolinsand** mit feinen Lagen und einer **Eisenschwarte** an der Grenze. Die Grenzen sind wellig. **Runsen** vom Regen ziehen als senkrecht gestreckte Rinnen die Falllinie hinab (Relief und Schatten), **Rieselsand** sammelt sich in den Rinnen und in Zungen über dem Lehm, **Sickerstreifen** treten an der Grenze Sand/Lehm aus und laufen einige Meter hinab, **nasser Lehm** wird dunkler und satter. Am Fuß liegen **Sturzkegel und Sandfuß** (Wandkarte mit Geröll schon unter 4 m Wand: `CliffMap.build(..., talusDrop, talusReach)`). Die **Hohlkehle** kommt von außen: `StrataMap.paintNotch` trägt ein, wie tief die Brandung den Fuß ausgewaschen hat; die Wand weicht im Band `notchBase` bis `notchBase + notchHeight` zurück, im Scheitelshader in jeder Entfernung. Die **Grasnarbe als Überhang** (`Sod`) ist ein Schlauch entlang der Kante, der über die Kante hinausragt, durchhängt und stellenweise abgerissen ist; oben Gras, darunter Narbe und Sand.

**Benutzung.**
```java
Cliff kliff = Cliff.rotesKliff();
kliff.strata = StrataMap.build(gelaende, x0, z0, 1, nx, nz, 30, (x, z) -> kaolinOben(x, z), (x, z) -> flugsand(x, z), 1.5);
kliff.strata.paintNotch(x, z, 3, 1.2);                     // Hohlkehle (in Sylt aus der Sturmflut, Phase 12)
kliff.strata.clearNotch(x0, z0, x1, z1);                   // seit Sylt Phase 12: nach einem Abbruch steht die Wand frisch
CliffMap map = CliffMap.build(kliff, gelaende, x0, z0, 1, n, 4, 18);
gl.upload(gl4, map);
gl.strataUnit = 13;                                        // Einheit der Schichtkarte (Vorgabe 13)
gl.bind(gl4, programm, kliff, 14);                         // lädt die Schichtkarte (RGBA16F), wenn sie neu ist
gl.setSods(List.of(new Sod(kante, 5)));                    // Kante: je Punkt x, y, z, ox, oz
gl.drawSods(gl4, kliff, view, light);
```

**Prüfung.** `CliffCheck` 39 Prüfungen, neu: Gneis und Granit unverändert (Prüfsumme über Material und Relief an 6000 Punkten), Schichten in der Reihenfolge, nasser Lehm, Runsen, Sickerstreifen, Schichtkarte, Hohlkehle, Grasnarbe, Sturzkegel. `CliffDemo --kliff` (Fenster) und `CliffDemo --bild datei.png --kliff` (sechs Ansichten). Unter Mesa: Grafikkarte = Prozessor Farbe ± 0,01 %; die Bilder von Gneis und Granit sind Bildpunkt für Bildpunkt gleich wie vorher.

**Grenzen.** Mächtigkeiten und Farben sind gesetzt [G nach den Fotos vom Roten Kliff]; die Schichtgrenzen sind waagerecht bis auf die Wellen (die untere Grenze und die Decke kommen je Ort aus der Karte). Die Hohlkehle verschiebt nur das Höhenfeld entlang seiner Normale; ein echter Hohlraum unter überhängendem Lehm kommt mit `fracture` (Phase 11). Die Grasnarbe wird nicht vom Wind bewegt.
