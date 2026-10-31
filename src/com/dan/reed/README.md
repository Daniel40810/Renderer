# com.dan.reed – Schilf, Binsen und Gras im Wind

Das Paket pflanzt Halme auf ein Gelände und bewegt sie im Wind: Schilfrohr am Ufer, Binsen am nassen Rand, Wiesengras als Büschel. Der Kern braucht nur Java 21 und beantwortet, wo welche Halme stehen und wie weit sich ihre Spitze gerade neigt. Der Ordner `gl/` zeichnet die Halme mit JOGL (OpenGL 4.3, instanziert, die Form entsteht im Vertex-Shader). Das Paket kennt weder Haithabu noch andere Pakete; wo etwas wächst und wie hoch der Boden liegt, gibt der Benutzer über zwei kleine Schnittstellen hinein.

Zum Herauskopieren reicht `src/com/dan/reed/`. Nur `demo/ReedDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Drei Arten:** `SCHILF` (bis 3 m, Rispe oben, im Herbst gold), `BINSE` (knapp 1 m, steif, dunkel), `GRAS` (Büschel, 0,3 bis 0,6 m). Jede Art hat Höhe, Streuung, Halme je m², Reichweite und Steifigkeit.
- **Wo es wächst:** `Reed.Density` liefert die Dichte 0..1 am Ort und je Art, `Reed.Ground` die Bodenhöhe. Die Halme werden in Zellen zu 16 m verteilt (`cell`, `around`), reproduzierbar aus `seed` und Zelle.
- **Wind:** stetige Neigung mit U² (`lean`, steife Arten weniger, höchstens 0,7 der Halmhöhe), Böenwellen von 18 m Länge, die mit 0,7 · U über die Fläche laufen, dazu Flattern quer zum Wind (`sway`). Der Shader rechnet dieselbe Formel.
- **Jahreszeit und Nässe:** `season` 0..1 färbt von Frühlingsgrün über Sommer bis Herbstgold und Wintergrau (`color`); `wetness` lässt die Halme hängen.
- **Qualität:** `LOW` bis `ULTRA` ändern Reichweite und Dichte.
- **Grafik (gl):** nahe Halme mit vier Abschnitten und fünf Halmen je Büschel, ferne mit zwei; die Liste um die Kamera wird im Hintergrund neu gebaut, wenn sie sich mehr als 6 m bewegt. Sonne, Himmelslicht (mit Gewicht `skyWeight`), Durchscheinen gegen die Sonne und Dunst über `ReedGl.Light`; seit Haithabu Phase 3 auch der Mond und eine Schattenkarte von oben (`shadowTexture`, `shadowRect`, etwa die Wolken), die jeder Halm an seinem Ort liest.

## Benutzung

```java
Reed reed = Reed.breeze();                    // Bft 3
reed.windFromDeg = 245;
reed.season = 0.75;                           // Herbst
Reed.Density wo = (x, z, kind) -> kind == Reed.Kind.SCHILF && ufer(x, z) ? 1 : 0;
Reed.Ground boden = gelaende::height;

ReedGl gl = new ReedGl();
gl.init(gl4);
// je Bild:
gl.update(gl4, reed, camX, camZ, wo, boden);  // baut bei Bedarf im Hintergrund neu
gl.draw(gl4, reed, view, light, t);
```

## Demo und Selbsttest

- `com.dan.reed.demo.ReedDemo` zeigt ein Ufer mit Schilf, Binsen und Gras und Reglern für Wind, Jahreszeit und Dichte; `ReedDemo --bild datei.png` rechnet ein Bild ohne Fenster.
- `com.dan.reed.demo.ReedCheck` prüft Verteilung, Reproduzierbarkeit, Neigung, Böen und Farben und endet mit „Alles in Ordnung.“.

## Schnee und Reif (seit Haithabu Phase 12)

- `snowDepth`: Schneehöhe in m. Die Halme wachsen aus der Schneedecke, ihre Spitzen bleiben, wo sie waren. Gras, das kürzer ist als der Schnee, verschwindet.
- `frost`: Reif von 0 bis 1 macht die Halme weißgrau, oben stärker als unten; von 1 bis 2 (seit Brocken Phase 10) Raueis: die Halme dick weiß verkrustet.

## Grenzen

- Halme stoßen nicht aneinander und werden nicht von Körpern niedergedrückt.
- Keine Schatten von Körpern auf den Halmen (nur die Schattenkarte von oben); die Selbstverschattung im Bestand ist eine Näherung über die Höhe am Halm.
- Die Böen sind ein Muster, keine Windsimulation; den mittleren Wind am Ort gibt seit Brocken Phase 5 auf Wunsch ein Feld von außen (siehe unten).

## Seit Glacier Phase 2: Steppe

`Reed.Kind.STEPPE` – Horstgras der patagonischen Steppe (Festuca, Stipa) [G]: dichte Büschel, 35 bis 50 cm, steif,
strohgelb mit grünem Grund, Spitzen hell. Die Halme stehen in Horsten (Shader-Zweig `kind == 3`), nicht im
gleichmäßigen Teppich. Die Felder in `ReedGl`, `reed.vert` und `reed.frag` sind nach der Zahl der Arten bemessen
(jetzt 4). Glacier gibt nur `STEPPE` und `GRAS` eine Dichte; Schilf und Binsen gibt es am Gletschersee nicht.
Rückfluss nach Haithabu/Lighthouse in Phase 16.

## Seit Brocken Phase 5: Wind von außen

- Schnittstelle `Wind` (nur JDK): `double[] at(x, y, z)` gibt den Wind am Ort als {Ost, oben, Süd} in m/s. Die App verdrahtet etwa `reed.wind = feld::windAt`.
- Gesetzt, gilt an jedem Halm der Wind in `windHeight` (10 m) über seinem Boden: Richtung und Stärke der Neigung, Böenwellen und Zittern laufen mit dem Wind am Ort. `windAt(x, boden, z)` und `sway(x, boden, z, …)` rechnen das auf dem Prozessor.
- `ReedGl` legt den Wind als Windkarte um die Kamera (64 × 64 Zellen, RG32F auf Einheit 6, alle `mapInterval` = 0,5 s im Hintergrund neu abgetastet); der Shader liest sie je Halm.
- Ohne `wind` (null) bleibt alles wie bisher; die Vorgänger bauen unverändert.

## Seit Sylt (Phase 9): Strandhafer und Übersandung

- `Reed.Kind.STRANDHAFER` (Ammophila arenaria): Horste aus steifen, eingerollten, graugrünen Blättern, 0,6 bis 1 m, spitz nach außen gebogen, Spitzen strohgelb (im Herbst weiter herunter). Shader-Zweig `kind == 4`; die Felder in `reed.vert` und `reed.frag` sind jetzt für fünf Arten bemessen.
- Schnittstelle `Burial` (nur JDK): `depth(x, z)` gibt die Übersandung in m, die die Halme noch nicht durchwachsen haben (etwa `com.dan.dune.SandBed.burialAt` über die App). Gesetzt (`reed.burial`), ragt jeder Halm um so viel weniger aus dem Boden; ganz begrabene fehlen. Gilt für alle Arten.
- `ReedCheck` prüft Höhe, Übersandung und Farbe des Strandhafers.
