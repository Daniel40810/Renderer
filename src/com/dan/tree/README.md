# com.dan.tree – Bäume, Sträucher und Gras aus Unreal-Assets

## Was es kann

Bäume, Sträucher, Gräser und Bodenstücke aus Unreal-Assets, in zwei Formen: aus dem Procedural Vegetation Editor (USD mit Skelett und Nanite-Zweigen) und als klassisches FBX mit Stamm und Laubkarten mit Alpha-Textur, ohne Skelett. Der Kern braucht nur Java 21. Er liefert Dreiecksnetze in vier Stufen mit allen Werten für Wind und Jahreszeit. `gl/` zeichnet ganze Bestände mit JOGL (OpenGL 4.3, instanziert), `demo/` enthält einen Renderer auf dem Prozessor, die Vorschau, die JOGL-Demo und die Selbsttests. Die Gräser liegen in `grass/` (siehe dort), weil sie diesen Kern brauchen und ein Package kein anderes kennen darf.

Einstieg ist `Tree`: alle Arten mit Schlüssel und Namen (`Tree.BAEUME`, `Tree.GRAESER`, `Tree.art("schwarzerle")`), die Qualitätsstufen `Tree.Quality` (Faktor auf die Entfernungen der Stufen). Gleicher Startwert ergibt den gleichen Bestand; zufällig ist nur, was aus einem Hash über Lage und Startwert kommt.

Zum Herauskopieren reicht `src/com/dan/tree/` mit allen Unterordnern. Die Demos brauchen zusätzlich `FStyle.jar`, `gl/` braucht JOGL. Die Assets liegen als Ressourcen in den Ordnern der Arten und kommen mit in `Renderer.jar`.

## Demo und Selbsttest

Für alle Arten gleich, die Art als erstes Argument (ohne: Schwarzerle bzw. alle):

```
java -cp Renderer.jar;FStyle.jar com.dan.tree.demo.TreeDemo kikuyu        # Vorschau auf dem Prozessor
java -cp Renderer.jar;FStyle.jar;jogl… com.dan.tree.demo.TreeGlDemo laubbaum 300
java -cp Renderer.jar com.dan.tree.demo.TreeCheck                         # alle Bäume, endet mit „Alles in Ordnung.“
java -cp Renderer.jar com.dan.tree.demo.GrassCheck                        # alle Gräser und der Fußweg
```

Arten bisher:

| Paket | Art | Quelle | Varianten | Dreiecke Stufe 0 |
|---|---|---|---|---|
| `com.dan.tree.blackalder` | Schwarzerle (*Alnus glutinosa*) | PVE-USD | A breit 16 m, B jung 11 m, C schlank 15 m, D groß 18 m | 0,3 bis 2,8 Mio. |
| `com.dan.tree.europeanbeech` | Rotbuche (*Fagus sylvatica*), seit Timmendorf Phase 19 | PVE-USD, zweistufige Zweige | A alt 27 m, B hoch 24 m, C jung 9 m, D jung 10 m | 0,1 bis 0,8 Mio. |
| `com.dan.tree.balticpine` | Waldkiefer, jung (*Pinus sylvestris*), seit Timmendorf Phase 19 | PVE-USD | A 4 m, B 2,4 m, C 1,2 m, D 1 m | 0,05 bis 0,3 Mio. |
| `com.dan.tree.greasewood` | Greasewood (*Sarcobatus vermiculatus*), Strauch | PVE-USD | A bis D, 0,6 bis 1,7 m | 0,15 bis 1,9 Mio. |
| `com.dan.tree.trees60` | Laubbaum mit Herbstlaub (Art nicht angegeben) | FBX + Texturen | 1 bis 10, 14 bis 27 m | 6 600 bis 7 900 |
| `com.dan.tree.hptree` | großer Laubbaum, Einzelblätter (Art nicht angegeben) | FBX + Texturen | 1, 27,5 m | 28 000 |
| `com.dan.tree.mobiletree` | Laubbaum für schwache Hardware (Art nicht angegeben) | FBX + Texturen | 1, 24 m | 1 900 |
| `com.dan.tree.supertree` | Laubbaum mit 400 Dreiecken (Art nicht angegeben) | FBX + Texturen | 1, 25 m | 410 |

Alle Assets werden mit `tools/tree/convert_all.sh` aus `Assets/Trees/*.zip` (und für `com.dan.tree.grass` aus `Assets/Grass/*.zip`) neu erzeugt. Gleiche Eingabe ergibt die gleiche Datei.

Gras und Gelände liegen in `com.dan.tree.grass`, das auf diesem Kern aufbaut. Dafür kennt der Kern:
- **`TreeSpecies.form`:** `TREE`, `GRASS` oder `GROUND`.
- **`TreeSpecies.lods`:** eigene Stufen aus dem Asset, je Stufe eine Datei; fehlende Stufen werden mit `lodKeep` ausgedünnt.
- **Entfernungen je Art:** `lodDistance`, `fadeDistance`, `maxDistance`.
- **`TreeSeason.persistent`:** Laub, das bleibt und sich nur färbt.
- **Demos:** einen Haken für die Anordnung (`TreeDemo.Layout`) und eine Bodentextur.

## Weg eines Baums

```
Unreal (PVE) ─ USD + DynamicWind.json ─▶ tools/tree/usd2dtree.py ─┐
FBX (binär/ASCII) + Texturen ─────────▶ tools/tree/fbx2dtree.py ─┴▶ <art>/*.dtree (+ Texturen) ─▶ TreeAsset ─▶ TreeBaker ─▶ TreeMesh ─▶ SoftRenderer / gl/TreeGl
```

1. **Umwandeln, einmal:** `python3 tools/tree/usd2dtree.py Tree_X_01_A.usd src/com/dan/tree/<art>/x_a.dtree --name "X A"` (braucht `pip install usd-core scipy numpy`). Die Ast-Bibliothek (`*_Branches.usd`) und die `*_DynamicWind.json` müssen im selben Ordner liegen. Das Skript schreibt:
   - das Skelett (Eltern, Lage und Radius je Gelenk),
   - Stamm und Äste im Original (je Punkt zwei Gelenke),
   - die Zweig-Prototypen: Holz als Knotengraph im 3-cm-Raster, Blätter als Sechseck aus Stiel, Mitte, Normale und Rand,
   - die Zweig-Instanzen (Matrix und zwei Gelenke),
   - Grundfarben und Böendämpfung aus Unreal.

   Eine Variante der Schwarzerle braucht 0,4 bis 1,6 MB statt 4 bis 14 MB USD plus 144 MB Ast-Bibliothek. Seit Timmendorf Phase 19 auch zweistufige Zweige wie bei der Rotbuche: ein Holznetz neben einem inneren PointInstancer für Blätter und Knospen (`proto_geometry`, der Wandler liegt jetzt in `tools/tree` des Projekts Renderer). Gelenke ohne Lage (beim Greasewood rund 4 000 Platzhalter im Ursprung) bekommen die Lage ihres Elternteils, Zweige an ihnen werden an das nächste echte Gelenk gebunden.

   **FBX:** `python3 tools/tree/fbx2dtree.py baum.fbx src/com/dan/tree/<art>/x.dtree --name "X" --bark rinde.png --leaf laub.png --prefix art` (braucht `pip install numpy pillow`). Der Leser `fbxread.py` versteht binäres und ASCII-FBX (7.x), Achsen und Einheit aus den `GlobalSettings` und Modell-Transformationen. Was folgt:
   - Polygone mit einem Material, das „leaf“ heißt, werden Laubkarten, alles andere Stamm.
   - Die Texturen werden auf 1024 Punkte verkleinert, die Rinde als JPEG, das Laub als PNG mit Alpha. Unter durchsichtigen und halb durchsichtigen Stellen wird die Farbe der Nachbarn eingezogen, damit beim Filtern keine Säume aus dem Hintergrund der Vorlage (oft Magenta) entstehen.
   - Die Texturen liegen einmal je Präfix neben den `.dtree`, und die Varianten nennen sie mit Namen.
   - Ein Skelett gibt es nicht, nur ein Gelenk am Fuß (siehe Wind ohne Skelett).
2. **Laden:** `TreeAsset.load(Owner.class, "x_a.dtree")`, nur Daten.
3. **Backen:** `TreeBaker.bake(asset, TreeLod.of(stufe))` liefert ein `TreeMesh`: 15 Werte je Punkt, nämlich Lage, Normale, Ausschlag von Hauptast und Zweig, Zittern, zwei Phasen, Art (0 Holz, 1 Laub, 2 Kronenhülle), Zufall mit Verdeckung und Texturkoordinaten.
4. **Zeichnen:** Wind und Jahreszeit rechnen `TreeWind.apply` bzw. `TreeMesh.color` auf dem Prozessor und `gl/tree.vert` auf der Grafikkarte, beide mit derselben Formel. Für einen anderen Tag muss nichts neu gebacken werden.

## Stufen

| Stufe | Stamm und Äste | Zweigholz | Laub | Schwarzerle A / C |
|---|---|---|---|---|
| 0 | Originalnetz aus Unreal | Röhren mit 3 Seiten, Ketten bis 15° Knick zusammengefasst | jedes Blatt als Sechseck | 2,8 Mio. / 320 000 Dreiecke |
| 1 | Röhren mit 5 Seiten ab 6 mm Radius | Röhren ab 3 mm | 35 % der Blätter als Raute, größer | 177 000 / 20 000 |
| 2 | Röhren mit 4 Seiten ab 2 cm | – | 10 % als Raute (wie die Büschel in `com.dan.forest`) | 50 000 / 5 700 |
| 3 | Stamm und Hauptäste ab 3 cm, 3 Seiten | – | Hülle der Krone, 8 × 5 | 344 / 272 |

FBX-Bäume haben kein Skelett für Röhren. Ihr Stamm wird deshalb in Rasterzellen vereinfacht: Stufe 0 ist das Original, Stufe 1 nutzt Zellen von Höhe/160, Stufe 2 Höhe/60, Stufe 3 Höhe/24. Die Laubkarten werden wie Blätter ausgedünnt und vergrößert (100 / 35 / 10 %), Stufe 3 ist die Hülle aus den Kartenmitten. Beispiel HighPoly-Baum: 27 800 / 10 300 / 3 300 / 330 Dreiecke.

Wenn Blätter wegfallen, werden die übrigen um 1/√Anteil größer, damit die Laubfläche gleich bleibt. Ohne Laub zeigt auch Stufe 3 das Gerippe aus Stamm und Hauptästen. Alle Felder von `TreeLod` sind öffentlich, eigene Stufen sind möglich. `TreeGl` wählt die Stufe nach der Entfernung in Baumhöhen (`lodDistance`, voreingestellt 1,4 / 3,5 / 14).

## Wind

`TreeSkeleton` wertet das Skelett aus. An jeder Gabel setzt das Kind den Ast fort, das am besten in die bisherige Richtung zeigt (dickere Kinder werden bevorzugt). Die übrigen Kinder beginnen einen Ast der nächsten Ordnung: 0 Stamm, 1 Hauptast, 2 und mehr Zweige. Je Punkt entstehen daraus:

- **Stamm:** Er neigt sich mit h² in den Wind und pendelt leicht (0,22 Hz, Phase je Ort).
- **Hauptast:** Ausschlag (Weg im Hauptast / Reichweite)² · Reichweite, mit dem Wind und quer dazu, eine Phase je Hauptast. Die Reichweite schließt die Zweige ein, die an ihm hängen, damit der Übergang zum Zweig ohne Sprung bleibt.
- **Zweig:** Er wippt in eine eigene Richtung. Alle Zweige eines Asts haben dessen Phase, sonst würden sie an ihrem Ansatz reißen.
- **Blatt:** Es zittert entlang der Normale, von 0 am Stiel bis 1 an der Spitze.

**Wind ohne Skelett (FBX):** Der Ausschlag mit dem Hauptast ist (r/R)² · R · 0,8. Dabei ist r der waagerechte Abstand von der Achse durch den Fuß und R der größte Abstand im Baum. Die Phase ist die Richtung um die Achse, also stetig, denn 0 und 1 sind dieselbe Phase. Benachbarte Äste schwingen so gemeinsam, und nichts reißt. Jede Laubkarte wippt mit einer eigenen Phase um ihre Ecke, die der Achse am nächsten liegt, und zittert zur äußeren Ecke hin stärker.

Die Stärke wächst mit (U/20 m/s)², höchstens 2. Böen laufen als Welle mit dem Wind über das Land und werden mit `gustAttenuation` aus Unreal gedämpft. `TreeSpecies.flexTrunk`, `flexBranch` und `flutter` stellen die Biegsamkeit je Art ein. Wind von außen kommt über `TreeWind.external` (Schnittstelle `Wind`, {Ost, oben, Süd} in m/s), etwa aus `com.dan.wind`.

## Jahreszeit

`TreeSeason` (Felder wie in `com.dan.forest.Species`: `leafOut`, `colorStart`, `colorFull`, `bare`, `spring`, `summer`, `autumn`) liefert je Tag einen `Look`. Sichtbar sind die Blätter mit `lo ≤ Zufall < hi`: Beim Austrieb erscheinen zuerst die mit kleinem Zufall, im Herbst färben sich diese zuerst und fallen zuerst. Schnee legt sich auf nach oben weisende Flächen.

**Texturen:** Rinde mit Textur bekommt Weiß als Grundfarbe, die Textur liefert die Farbe. Laub mit Textur wird mit `Farbe der Jahreszeit / Sommergrün` getönt (`Look.reference`). Bei den FBX-Arten ist das Sommergrün die mittlere Farbe der Laubtextur, im Sommer bleibt die Textur also, wie sie ist. Die Kronenhülle (Art 2) bleibt ohne Textur. Beide Renderer lesen die Texturen als sRGB, rechnen linear und verwerfen Laub unter Alpha 0,5. Der Prozessor-Renderer nutzt dafür Mip-Stufen, `gl/` sRGB-Texturen mit Mipmaps.

## Benutzung

```java
TreeSpecies erle = BlackAlder.species();
TreeMesh m = erle.bake(0, 1);                         // Variante A, Stufe 1

// Prozessor:
TreeWind wind = new TreeWind();
wind.speed = 8; wind.fromDeg = 250; wind.gustiness = 0.5f;
float[] pos = new float[m.vertices * 3];
wind.displace(m, erle, erle.load(0).gustAttenuation, t, x, y, z, drehung, pos);
TreeSeason.Look look = erle.season.look(tag, schnee);
m.color(i, erle, erle.load(0), look, rgb);             // false: Blatt fehlt

// JOGL:
TreeGl gl = new TreeGl(erle);
gl.add(variante, x, y, z, drehung, maßstab);
gl.init(gl4);
// je Bild:
gl.setSeason(tag, schnee);
gl.setWind(wind);
gl.update(gl4, auge);                                 // Stufen wählen, fertig gebackene Netze hochladen
gl.draw(gl4, view, light, t);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `TreeAsset` | Daten aus `.dtree` (Version 1 und 2): Skelett, Stamm (mit UVs), Zweig-Prototypen, Instanzen, Laubkarten, Namen der Texturen |
| `TreeSkeleton` | Äste, Ordnung, Hauptast, Weg und Reichweite je Gelenk |
| `TreeLod` | Einstellungen einer Stufe, `of(0..3)` |
| `TreeBaker` | Netz backen: Original, Röhren oder Rasterzellen, Zweigholz, Blätter, Laubkarten, Hülle, Verdeckung, Wind aus Skelett oder Geometrie; `chains` fasst gerade Ketten zusammen |
| `TreeMesh` | Ergebnis, 15 Werte je Punkt; `color` für Art, Jahreszeit und Tönung der Texturen |
| `TreeWind`, `Wind` | Wind, Böen, Formel je Punkt (`apply`), Wind von außen |
| `TreeSeason` | Austrieb, Verfärbung, Laubfall |
| `TreeSpecies` | Art: Varianten und Texturen als Ressourcen, Farben, Biegsamkeit, Jahreszeit; lädt einmal und backt |
| `gl/TreeGl`, `gl/tree.vert`, `gl/tree.frag` | Bestand mit JOGL: Netze im Hintergrund backen, Eimer je Variante und Stufe, ein Aufruf je Eimer |
| `demo/SoftRenderer` | Renderer auf dem Prozessor für Vorschau und Bilder |
| `demo/TreeDemo` | Vorschau einer Art: Einzelbaum, Hain, Stufen nebeneinander; `--bild` ohne Fenster |
| `demo/TreeGlDemo` | Bestand mit JOGL; zeichnet in einen Framebuffer außerhalb des Fensters und zeigt jedes Bild in einem `JPanel`, weil das durchscheinende `FFrame` unter Windows weder `GLCanvas` noch `GLJPanel` verträgt; `--bild` ohne Fenster |
| `Tree` | Einstieg: Arten, Qualitätsstufen |
| `demo/TreeCheck`, `demo/GrassCheck` | Selbsttests aller Arten (mit Skelett oder Laubkarten, Texturen; Wiese und Fußweg) |
| `demo/GrassDemo`, `demo/GrassGlDemo` | Vorschau und JOGL-Wiese der Gräser |

## Neue Art hinzufügen

1. Aus Unreal die Varianten als USD exportieren, dazu die Ast-Bibliothek und die DynamicWind-JSON. Oder FBX mit Rinden- und Laubtextur verwenden.
2. `usd2dtree.py` bzw. `fbx2dtree.py` je Variante aufrufen und die Dateien nach `src/com/dan/tree/<art>/` legen. Die Zeile gehört auch in `tools/tree/convert_all.sh`.
3. `<Art>.java` mit `species()` anlegen: Ressourcen, Namen, Rinde, Biegsamkeit, Jahreszeit. Vorlage für USD ist `blackalder/BlackAlder.java`, für FBX `trees60/Trees60.java` (`seasonFromLeafTexture` setzt die Jahreszeit aus der Laubtextur).
4. Die Art mit Schlüssel, Namen und erwarteter Höhe in `Tree.BAEUME` (Gras: `Tree.GRAESER`) eintragen; Demos und Selbsttests finden sie dann.

## Grenzen

- Der Wind ist ein Modell für kleine Winkel. Punkte werden verschoben, nicht um Gelenke gedreht. Bei Sturm dehnen sich deshalb einzelne Kanten (unter 0,2 %, geprüft).
- Die PVE-Assets bringen keine Texturen mit, nur Grundfarben. Rinde und Laub von Schwarzerle und Greasewood sind deshalb einfarbig, mit Zufall je Blatt und Verdeckung. Normal Maps der FBX-Packs werden nicht genutzt.
- Der Wind der FBX-Bäume ist aus der Geometrie geschätzt. Ein tief hängender Ast schwingt so stark wie die Krone in gleichem Abstand zur Achse.
- Die Hülle in Stufe 3 ist nur für große Entfernung gedacht. Aus der Nähe wirkt sie wie ein glatter Körper.
- In `gl/` gibt es noch keine Schattenkarte. Der Prozessor-Renderer zeichnet unter jeder Krone einen weichen Fleck.
- `TreeGlDemo` fordert kein Multisampling an. Fehler beim Backen, Hochladen oder in OpenGL zeigt es in der Statuszeile an (`TreeGl.error()`).
- Stufe 0 der großen Varianten (A 2,8 Mio., D 1,7 Mio. Dreiecke) ist für Nahansichten gedacht und braucht etwa 3 s zum Backen. `TreeGl` backt im Hintergrund und zeigt bis dahin die nächstgröbere fertige Stufe.
- `usd2dtree.py` liest Y-oben-USD mit einem PointInstancer für die Zweige, so wie ihn der Procedural Vegetation Editor schreibt. `fbx2dtree.py` liest statische Meshes. Skelette und Animationen in FBX werden ignoriert.
- Die Art der FBX-Bäume geben die Packs nicht an. Die Pakete heißen nach dem Pack, die Jahreszeit ist allgemein für Laubbäume.

## Seit welcher Phase

Seit Timmendorf Phase 5 im Renderer, aus dem Projekt Nature übernommen (dort `com.dan.tree` und `com.dan.grass`). Geändert beim Umzug: Gras ist `com.dan.tree.grass`, die Demos der Arten sind durch das Argument „Art“ ersetzt, `Tree` ist neu.
