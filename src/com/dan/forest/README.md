# com.dan.forest – Bäume und Wald

Das Paket erzeugt Bäume prozedural. Sie bewegen sich im Wind, färben sich mit der Jahreszeit und verlieren ihr Laub. Der Kern braucht nur Java 21 und hängt weder vom Geyser-Projekt noch von einer 3D-Bibliothek ab. Er liefert Dreiecksnetze mit Farben und Normalen. Unter `demo/` liegt ein einfacher CPU-Renderer als Beispiel; seit Haithabu Phase 2b zeichnet der Ordner `gl/` ganze Wälder mit JOGL (OpenGL 4.3, instanziert).

Zum Herauskopieren reicht `src/com/dan/forest/`. Die Demos brauchen zusätzlich `FStyle.jar` für die Oberfläche, `gl/` braucht JOGL.

## Arten

| Name | Art | Kronenform |
|---|---|---|
| Drehkiefer | *Pinus contorta* | schmal, Quirle |
| Espe | *Populus tremuloides* | schmal-oval, starkes Blattzittern |
| Douglasie | *Pseudotsuga menziesii* | kegelförmig |
| Eiche | *Quercus robur* | breit ausladend |
| Buche | *Fagus sylvatica* | rund |
| Birke | *Betula pendula* | oval, hängende Zweige |
| Fichte | *Picea abies* | kegelförmig, Quirle, hängende Zapfen |
| Engelmann-Fichte | *Picea engelmannii* | schmal kegelförmig, blaugrün, hängende Zapfen |
| Felsengebirgs-Tanne | *Abies lasiocarpa* | schmaler Turm bis zum Boden, aufrechte violette Zapfen |
| Weißstämmige Kiefer | *Pinus albicaulis* | rundlich, früh verzweigt, helle Rinde |
| Weißtanne | *Abies alba* | mächtiger Kegel, silbergraue Rinde, aufrechte Zapfen |
| Waldkiefer | *Pinus sylvestris* | Schirm, oben orange Rinde |
| Europäische Lärche | *Larix decidua* | lichter Kegel, im Herbst golden, im Winter kahl |
| Zirbe | *Pinus cembra* | dicht, säulenförmig, aufrechte blaue Zapfen |
| Schwarzerle | *Alnus glutinosa* | kegelig-eiförmig, waagerechte Äste; färbt sich kaum, verliert das Laub spät noch grün |

**Nadelbäume** haben ein paar eigene Werte in `Species`:

- **Kronen:** `Crown.SPIRE` für den schmalen Turm der Hochlagentannen, `Crown.UMBRELLA` für den flachen Schirm alter Kiefern.
- **Zapfen:** `cones`, `coneSize`, `coneColor`, `coneUpright`, `coneZone`. Sie stehen aufrecht oder hängen, und zwar nur im oberen Teil der Krone. Sie bewegen sich mit ihrem Ast und bekommen im Winter Schnee.
- **Rinde oben:** `barkTop`, `barkTopFrom`, zum Beispiel die orange „Spiegelrinde“ der Waldkiefer.
- **Maitriebe:** `shoots` ist die Farbe der neuen Spitzen. Sie treiben ab `leafOut` hell aus und dunkeln bis zum Hochsommer nach.
- **Lärche:** Ein Nadelbaum mit `deciduous = true` wirft seine Nadeln ab wie ein Laubbaum. Die Nadeln färben sich vorher und fallen als Laub.

`Species.ALL` enthält Erzeuger für alle Arten, jeder Aufruf liefert eine frische Kopie. `Species.byName("Buche")` sucht eine Art nach Namen. Alle Felder sind öffentlich und lassen sich ändern, zum Beispiel Höhe, Astebenen, Winkel, Blattform, Biegsamkeit, Farben je Jahreszeit und die Tage für Austrieb, Verfärbung und Laubfall. So entstehen eigene Arten.

## Benutzung

```java
Ground ground = (x, z) -> terrain.height(x, z);   // oder Ground.FLAT
Forest forest = new Forest(ground);
int pine  = forest.addSpecies(Species.lodgepolePine());
int aspen = forest.addSpecies(Species.aspen());

ForestPlanter.plant(forest, -100, -100, 100, 100, 6f, (x, z) -> 1f,
        new int[]{pine, aspen}, new float[]{0.8f, 0.2f}, 25f, ground, 42);
// oder einzeln: forest.place(species, variant, x, z, yaw, age)

forest.wind.speed = 8;  forest.wind.direction = 60;  forest.wind.gustiness = 0.6f;
forest.setSeason(285, 0);                        // Tag im Jahr, Schnee 0..1

// je Bild:
forest.update(t, dt, camX, camY, camZ);          // LOD wählen, Wind, Laubfall
forest.visit((mesh, pos, nrm, col, tree) -> {
    // mesh.tri / mesh.nt: Dreiecke; pos, nrm, col: je Punkt 3 Werte, im Baumraum
    // tree.x/y/z, tree.yaw, tree.scale: Platzierung; mesh.doubleSided[i]: Blätter beidseitig
});
int n = forest.leaves.quads(xyz, rgb, nrm);      // fallende und liegende Blätter
```

Mit `TreeGenerator.grow(species, seed, age)` und `TreeMesh.build(model, lod)` bekommt man einen einzelnen Baum ohne Wald. `TreeMesh.build(model, lod, barkLod)` baut Laub und Geäst in verschiedenen Stufen, zum Beispiel Blätter der Stufe 1 an Ästen der Stufe 2 für viele Laubbäume in mittlerer Entfernung. `TreeMesh.silhouette(model, sides, rings)` liefert eine sehr leichte Hülle mit etwa 100 Dreiecken für ferne Bäume. Das Geyser-Projekt nutzt die Silhouette für die Kiefern tief im Wald. Am Waldrand und bei den Espen nimmt es die feineren Stufen (siehe `com.dan.geyser.world.Grove`).

## Großer Wald auf der Grafikkarte (seit Haithabu Phase 2b)

`ForestField` verteilt Bäume über eine ganze Landschaft: Kacheln zu 64 m, je Platz eines Gitters (Abstand `spacing`) höchstens ein Baum, reproduzierbar aus Startwert und Kachel. Wo welche Art wächst, sagt die Schnittstelle `ForestField.Density` (je Art 0..1), die Höhe `Ground`. Je Bild sortiert `update(auge, projektionBlick, maxNeu)` die Bäume im Blickkegel in Eimer nach Art, Variante und Stufe:

| Stufe | Netz | bis (Mittel) |
|---|---|---|
| 0 | `TreeMesh.build(m, 0)`, alle Blattbüschel | 12 m je 20 m Baumhöhe |
| 1 | `build(m, 1, 2)`, gröbere Büschel an gröberen Ästen | 30 m je 20 m Baumhöhe |
| 2 | dieselben Büschel, nur jedes siebte, doppelt so groß (rund 3 000 Dreiecke) | 150 m |
| 3 | Silhouette 7 × 4 (rund 60 Dreiecke) | 600 m |
| 4 | Silhouette 5 × 2 (unter 40) | Reichweite |

Jenseits von 40 % der Reichweite wird ausgedünnt; die verbleibenden Kronen werden mit 1/√p breiter, so dass ein ferner Waldrand geschlossen bleibt. `ForestField.Quality` (`LOW` bis `ULTRA`) stellt Reichweite (1,1 bis 3,2 km), Stufengrenzen und Abstand ein.

`gl/ForestGl` backt die Netze im Hintergrund für den Tag im Jahr (Farben nach `Season`, gefallene Blätter weg, kahle Kronen in der Ferne als lichtes, graubraunes Geäst) und zeichnet jeden Eimer mit einem Aufruf. Der Wind biegt im Vertex-Shader (Neigung mit U², Böen laufen über die Fläche, Laub zittert). Licht über `ForestGl.Light`: Sonne, Mond, Himmel mit Gewicht, Dunst, Nässe, Wolkenschatten von oben und eine Sonnenschattenkarte des Benutzers; `drawShadow` zeichnet die Bäume selbst hinein (nah mit Stufe 2, sonst mit der Silhouette).

```java
List<Species> arten = List.of(Species.oak(), Species.beech(), Species.birch(), Species.alder());
ForestGl gl = new ForestGl(arten, 4, 950);              // 4 Varianten je Art
ForestField wald = new ForestField(4, 4, (x, z, art) -> dichte(x, z, art), gelaende::hoehe, 950);
wald.setHeights(gl.heights());
wald.apply(ForestField.Quality.MEDIUM);
gl.init(gl4);
// je Bild:
gl.setSeason(tagImJahr, schnee);                         // backt bei Bedarf neu
gl.setWind(nachX, nachZ, mProS, boeigkeit);
wald.update(auge, projektionBlick, 24);
gl.update(gl4, wald);
gl.drawShadow(gl4, lichtMatrix, t, 3);                   // optional, in die eigene Schattenkarte
gl.draw(gl4, view, light, t);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Species` | Artbeschreibung und Voreinstellungen |
| `TreeGenerator`, `TreeModel` | Stamm, Äste in mehreren Ebenen, Hüllform der Krone, Blattstellen |
| `TreeMesh` | Netz in vier Stufen: 0 und 1 mit Rinde und Blattbüscheln, 2 mit groben Büscheln, 3 als Silhouette |
| `WindField` | Grundwind mit Böen, die als Rauschen durch den Wald ziehen |
| `TreeAnimator` | Bewegung je Punkt in Hierarchie: Stamm neigt und schwingt, Äste schwingen und wippen, Blätter zittern |
| `Season` | Austrieb, Sommergrün, Verfärbung, kahl, Schnee |
| `LeafFall` | Blätter lösen sich, trudeln, landen, werden braun und verschwinden |
| `ForestPlanter` | Verteilung mit Mindestabstand und Artengruppen |
| `Forest` | hält alles zusammen: Varianten je Art, LOD nach Entfernung, parallele Bewegung |
| `ForestField` | großer Wald in Kacheln für die Grafikkarte: Dichte je Art, fünf Stufen, Ausdünnung, Blickkegel |
| `gl/ForestGl` | Wald mit JOGL: Netze je Tag gebacken, Instanzen, Wind im Shader, Sonnen- und Wolkenschatten |

Mit `forest.leaves.water` lassen sich Blätter, die auf Wasser landen, an ein Wasser übergeben (Schnittstelle `LeafFall.Water`, etwa ein Fluss aus `com.dan.river`, der sie weiterträgt). Nur die `maxAnimated` nächsten Bäume (voreingestellt 40) werden je Punkt bewegt, die übrigen stehen still. Die Entfernungen für die LOD-Stufen stehen in `lodDistance`.

## Demo und Selbsttest

- `com.dan.forest.demo.ForestDemo [ansicht art tag]`: Ansicht 0 = Einzelbaum, 1 = Wald Yellowstone, 2 = Wald Mitteleuropa, 3 = Nadelwald Yellowstone, 4 = Bergwald Alpen; Art 0 bis 13 in der Reihenfolge der Tabelle. Die Regler steuern Wind, Böen, Jahreszeit und Schnee.
- `com.dan.forest.demo.ForestGlDemo [--bild datei.png hain|wald|rand breite höhe tag]`: der Grafikteil mit Eiche, Buche, Birke und Schwarzerle auf welligem Land, mit Sonnenschatten; „wald“ zeigt 2 km Reichweite.
- `com.dan.forest.demo.ForestCheck`: prüft Aufbau, Wind, Jahreszeit, Laubfall und `ForestField` (Dichte je Art, Stufen, Ausdünnung, Blickkegel, Startwert) ohne Fenster und endet mit „Alles in Ordnung.“

## Grenzen

- Im Grafikteil bewegen sich die Bäume nur im Shader als Ganzes (kein Ast für sich, keine fallenden Blätter); Laubfall und feine Bewegung bleiben dem CPU-Weg (`Forest`) vorbehalten.
- Stufe 2 und die Silhouetten sind Näherungen: aus der Nähe wirken die großen Büschel wie Paddel, die Silhouetten wie glatte Kronen.
- Der Beispiel-Renderer arbeitet auf dem Prozessor. Ein Wald mit einigen hundert Bäumen braucht dort etwa 100 ms je Bild. Das Paket selbst (Bewegung und Netz) braucht rund 10 ms.
- Blätter werden als gezackte Büschel gezeichnet, nicht als einzelne Blätter. Nadelbäume bekommen Nadelbürsten.
- Die Artwerte sind nach Augenmaß angenähert und nicht vermessen.
- Die Bewegung ist ein Modell für kleine Winkel und keine Physik. Sturmbruch gibt es nicht.

## Seit Glacier Phase 2: Südbuchen

`Species.lenga()` (Nothofagus pumilio, Waldbaum bis zur Baumgrenze), `Species.nire()` (Nothofagus antarctica,
krumm, an lichten, trockenen Rändern), `Species.guindo()` (Nothofagus betuloides, immergrün, am feuchten Hang) [G].
Alle drei stehen in `Species.ALL`. Ihre Jahresdaten (Austrieb, Herbstfarbe, Laubfall) sind wie bei allen Arten
nordhemisphärisch angegeben; eine App auf der Südhalbkugel verschiebt den Tag im Jahr um 182 Tage (Glacier:
`Wiring.northernDay`). Lenga und Ñire färben sich im Herbst rot bis orange. Rückfluss in Phase 16.

## Seit Brocken Phase 5: Wind von außen

- Schnittstelle `Wind` (nur JDK): `double[] at(x, y, z)` gibt den Wind am Ort als {Ost, oben, Süd} in m/s; die App verdrahtet etwa `forest.wind.external = feld::windAt`.
- Gesetzt, nimmt `WindField.sample` Richtung und mittlere Geschwindigkeit am Baum aus dem Feld, 10 m (`externalHeight`) über dem Boden des Walds; Böen, Pendeln der Richtung und Unruhe kommen wie bisher dazu. Nahe Bäume biegen sich so je nach Ort verschieden.
- `ForestGl.setWind` bleibt ein Wind für alle fernen Bäume; die App kann ihn je Bild aus dem Feld am Auge setzen.
- Ohne `external` (null) bleibt alles wie bisher.

## Seit Brocken Phase 9: Totholz, Windwurf, Windflüchter

**Was es kann.** Ein Wald nach dem Borkenkäfer und an der Baumgrenze:

- `Species.dead`: 1 frisch abgestorben (die Nadeln hängen noch, rotbraun aus `summer`), 2 lange tot (kahl, kürzere hängende Äste, wenige dürre Zweige, silbergraue Rinde aus `bark`).
- `Species.snag`: gebrochen; der Stamm endet bei diesem Anteil der Höhe (± 25 %), darüber nichts.
- `Species.lying`: liegend; der Baum wird am Ende auf die Seite gelegt, der Stamm entlang +x, der Wurzelanlauf auf dem Boden (`TreeModel.length` ist dann die Länge).
- `Species.flagging` und `flagFromDeg`: Windflüchter; die Äste zur Wetterseite sind kurz, die anderen länger und nach Lee gebogen (Fahne).
- Neue Arten: `spruceRed()`, `spruceDead()`, `spruceSnag()`, `spruceLog()`, `spruceYoung()` (Naturverjüngung), `spruceFlag()` (Krüppelfichte), `rowan()` (Eberesche, rote Beeren als Zapfen), `downyBirch()` (Moorbirke); alle in `Species.ALL`. `Species.copy(name, latin)` kopiert eine Art als Grundlage einer eigenen.
- `ForestField.yaw[s]`, `yawSpread[s]`: feste Drehung je Art statt der zufälligen, etwa für liegende Stämme (`ForestField.yawToward(kompassgrad)`) oder Windflüchter (0, damit die Fahne im Modell auch in der Welt nach Lee zeigt); `sizeFactor[s]` skaliert den Wuchs je Art.
- Ohne Laub zeigt die Ferne (Stufe 3 und 4) ein graues Gerippe aus Stamm und den längsten Ästen statt einer geschlossenen Krone; liegende Stämme biegen sich nicht im Wind und werden in der Ferne nicht verbreitert.

**Benutzung.**
```java
List<Species> arten = List.of(Species.spruce(), Species.spruceDead(), Species.spruceSnag(), Species.spruceLog(), Species.spruceYoung());
ForestField wald = new ForestField(arten.size(), 3, dichte, boden, 1141);
wald.yaw[3] = ForestField.yawToward(65);          // liegend nach Nordost (Sturm aus Südwest)
wald.yawSpread[3] = Math.toRadians(35);
```

**Aufbau.** Alles in `TreeGenerator.grow`: `snag` kürzt den Stamm vor den Ästen, `dead` lässt Blätter, Zapfen und tiefere Ordnungen weg und kürzt die Äste, `flagging` wirkt auf die Länge und Richtung der Äste erster Ordnung, `lying` dreht zum Schluss das ganze Skelett (`layDown`). `TreeMesh.skeleton` baut die ferne Form ohne Laub; Bäume ohne Laub bekommen keine Verdunklung durch die Krone.

**Grenzen.** Liegende Stämme liegen auf dem Boden ihres Fußes, am Hang ragen sie in die Luft oder in den Hang; ohne Wurzelteller. Die abgebrochene Krone eines gebrochenen Baums liegt nicht daneben. Windflüchter sind in der Ferne (Silhouette) wieder rund. Die Werte sind nach Fotos aus dem Harz geschätzt [G].

**Selbsttest.** `ForestCheck` prüft zusätzlich: tote Fichte ohne Nadeln mit kürzeren Ästen und wenigen Zweigen, graues Gerippe in der Ferne, Stumpf, liegender Stamm (Länge, Höhe, liegt auf), Windflüchter (Äste im Mittel nach Lee), feste Fallrichtung im Feld. `ForestGlDemo --bild datei.png brocken 1280 720 230` zeigt den Bergfichtenwald.

## Seit Brocken Phase 10: Eis auf den Bäumen

`ForestGl.setRime(dicke, art, woherGrad, base, full)` legt einen Belag aus Eis auf alle Bäume, ohne dass das Package ein Eis-Modell kennt (die Dicke rechnet die App, etwa mit `com.dan.rime`): Art 0 keins, 1 Raueis, 2 Raureif, 3 Klareis; unter der Höhe `base` kein Eis, ab `full` die volle Dicke (Raueis wächst in der Wolke, also oben). Raueis schiebt Krone und obere Zweige auf der Luvseite als Fahne gegen den Wind (auch in der Schattenkarte), färbt sie weiß in Federn und drückt die Äste mit der Last nach unten; Raureif und Klareis legen sich rundum. Der Stamm bekommt nur Farbe, keine Form. `ForestGlDemo` hat einen Regler „Raueis“ (0 bis 20 cm, Wind aus 250°), ohne Fenster `-Draueis=cm`.

Grenzen: die Fahne ist ein Versatz der Ecken, keine neue Geometrie; feine Nadeln, die das Netz nicht hat, tragen kein eigenes Eis. Die Last biegt nur das Netz, Bäume brechen nicht.

## Seit LituyaBay1958 Phase 6: Küstenregenwald, Millionen Bäume auf der Grafikkarte, Zustand je Baum

**Was es kann.**

- **Neue Arten** [G, nach Wuchsbeschreibungen]:
  - `Species.sitkaSpruce()`: Sitka-Fichte, 28 bis 50 m, Kegel, hängende Zweige, blaugrün.
  - `Species.westernHemlock()`: Westliche Hemlocktanne, 24 bis 44 m, schmal, weiche hängende Zweige. Der Leittrieb nickt (`Species.leaderDroop`).
  - die jungen Formen beider (2 bis 8 m).
  - `Species.redAlder()`: Roterle, hellgraue Rinde mit Flechten, an Ufern.
  - Alle stehen am Ende von `Species.ALL`. Die Formen aller älteren Arten sind unverändert (gleiche Netze bei gleichem Startwert, geprüft).
- **Schäden** (`Species.damaged(art, Damage)`): Jede Art bekommt drei geschädigte Formen. Alle sind lange tot, also ohne Nadeln und Zapfen.
  - `BROKEN`: ein Stumpf von 20 bis 40 % der Höhe, Rinde noch dran.
  - `STRIPPED`: steht in voller Höhe, entrindet (helles Holz `Species.FRESH_WOOD`), nur wenige Stummel (`Species.branchKeep`).
  - `FELLED`: liegt entlang +x, entrindet.
- **Zustand je Baum** (`TreeStates`): ein Byte je Platz des Pflanzgitters, mit dem Zustand stehend, gebrochen, entrindet, umgelegt oder fort. Für umgelegte Bäume dazu die Fallrichtung in 32 Schritten. Wer den Zustand rechnet (Welle, `succession`), schreibt ihn hinein. Die Grafikkarte übernimmt Änderungen im nächsten Bild. Seit LituyaBay1958 Phase 14 dazu ein zweites Byte je Platz für den **Nachwuchs** (`setGrowth(i, j, art, größe)`): Art (0–14) und Größe als Anteil der ausgewachsenen Art in 16 Stufen. Ein Platz mit Nachwuchs trägt immer einen Baum dieser Art, ohne Wurf auf die Dichte (junge Erlen und Fichten im Band); auf der Grafikkarte als R16UI. `ForestGpuCheck` prüft das in einem fünften Blick.
- **Kultur** (`ForestCulture`): wo jeder Baum steht, aus Dichte je Art, Startwert und Zustand. Gespeichert wird dafür nichts.
  - Je Platz (i, j) eines Gitters mit `spacing` steht höchstens ein Baum.
  - Ausgewürfelt wird mit einem PCG-Hash, in Java und GLSL gleich. Dichte und Höhe liegen als Gitter vor (`ForestCulture.Grid`); abgetastet wird von Hand bilinear.
  - `at(i, j, baum)` gibt den Baum eines Platzes, `count(...)` die Bäume je Typ in einem Rechteck.
- **Wald auf der Grafikkarte** (`gl/ForestGpu`, neben `gl/ForestGl`; `ForestGl` und seine Shader sind unverändert):
  - `forestgpu_cull.comp` würfelt je Bild jeden Platz in Reichweite aus, wie `ForestCulture`. Danach prüft es Ausdünnung und Sichtkegel und wählt die Stufe:
    - 0 und 1: wie `ForestField`
    - 2: grobe Büschel bis `lod2`
    - 3: für stehende Bäume ein Impostor, für geschädigte ihr Gerippe
  - Die Instanzen landen in Eimern (Stufe · Typen + Typ). `forestgpu_scan.comp` legt daraus die Zeichenbefehle an.
  - Gezeichnet wird mit drei `glMultiDrawElementsIndirect`: Netze mit `forest.vert`/`forest.frag`, die Impostors mit eigenen Shadern. Es gibt weder Kacheln noch einen Speicher je Baum, und je Bild wird nichts hochgeladen.
- **Oktaeder-Impostors** (`Octahedral`): Für jeden stehenden Typ werden 8 × 8 Ansichten der oberen Halbkugel gebacken, mit Farbe (sRGB) und Normale (Modellraum). Gebacken wird beim Laden und bei jedem neuen Tag im Jahr.
  - Das Viereck zum Auge mischt die vier nächsten Ansichten bilinear. Jede Ecke wird in die Bildachsen jeder Ansicht projiziert, damit der Atlas ohne Versatz getroffen wird.
  - Das Licht ist das der nahen Bäume (Sonne, Himmel, Durchscheinen, Dunst, Schatten).
- **Qualität** (`ForestGpu.Quality`):

  | Stufe | Reichweite | Stufe 2 bis | Bildpunkte je Ansicht | Instanzen höchstens |
  |---|---|---|---|---|
  | LOW | 2 km | 100 m | 64 | 0,6 Mio. |
  | MEDIUM | 3 km | 150 m | 96 | 1,2 Mio. |
  | HIGH | 4,5 km | 220 m | 128 | 2 Mio. |
  | ULTRA | 6 km | 300 m | 160 | 3 Mio. |

**Benutzung.**

```java
ForestGpu wald = new ForestGpu(List.of(Species.sitkaSpruce(), Species.westernHemlock(), Species.redAlder()), 3, 6.0, 1958);
wald.apply(ForestGpu.Quality.HIGH);
wald.setTerrain(gelaende::hoehe, x0, z0, kante, 2048);          // oder setTerrain(ForestCulture.Grid)
wald.setDensity((x, z, art) -> dichte(x, z, art), x0, z0, kante, 1024);
TreeStates zustand = TreeStates.covering(x0, z0, x1, z1, 6.0);
wald.setStates(zustand);                                         // zustand.set(i, j, TreeStates.FELLED, 180) usw.
wald.init(gl4);
wald.setSeason(190, 0);
// je Bild:
wald.setWind(nachX, nachZ, mProS, boeigkeit);
wald.update(gl4, view);                                          // Kultur auf der Grafikkarte
wald.drawShadow(gl4, lichtMatrix, t);                            // optional: nahe Bäume in die Schattenkarte
wald.draw(gl4, view, licht, t);
```

`wald.culture()` beantwortet Fragen der App ohne Grafikkarte, etwa „steht auf Platz (i, j) ein Baum, welcher, wie hoch“. `readStats` gibt Zähler, `info()` eine Zeile für das HUD.

**Aufbau.**

| Klasse | Aufgabe |
|---|---|
| `TreeStates` | Zustand je Platz, Fallrichtung, Nachwuchs (Art und Größe, seit Phase 14), Zähler |
| `ForestCulture` | Kultur auf dem Prozessor: Hash, Gitter (Dichte, Höhe), Baum je Platz, Zählen |
| `Octahedral` | Halb-Oktaeder: Richtung ↔ Quadrat, Ansicht und ihre Achsen |
| `gl/ForestGpu` | Formen je Zustand, Netze backen (Hintergrund), Impostors backen, Kultur, Zeichnen, Prüfhilfen (`cullCpu`, `readInstances`, `atlasCoverage`) |
| `gl/forestgpu_cull.comp`, `gl/forestgpu_scan.comp` | Kultur und Zeichenbefehle |
| `gl/forestgpu_imp.vert`, `gl/forestgpu_imp.frag`, `gl/forestgpu_bake.*` | Impostors zeichnen und backen |
| `demo/BayScene`, `demo/ForestGpuDemo` | Gerechnete Fjordbucht (16 × 12 km, rund 2 Mio. Bäume, Band der Welle am Nordufer). Vier Ansichten mit Fenster, `--bild datei.png [qualität]` und `--zeiten` (die fps-Tabelle je Qualität und Ansicht) |
| `demo/ForestGpuCheck` | Prüfung auf der Grafikkarte: jede Instanz gleich dem Spiegel auf dem Prozessor (vier Ansichten), Zähler, nichts auf „fort“, Fallrichtung, Atlanten gefüllt |

**Prüfung.** `ForestCheck` prüft zusätzlich:
- die neuen Arten (Höhe, nickender Leittrieb) und die drei Schäden
- die Zustände (Fallrichtung auf 11,25°)
- die Kultur: fester Hash, Dichte 0,2 + 0,1 füllt 0,298 der Plätze, Arten 1,92 : 1, „fort“ bleibt leer
- den Halb-Oktaeder (hin und zurück 3e-7)

`ForestGpuCheck` lief hier mit Mesa (llvmpipe): in vier Ansichten 12 687 bis 58 166 Bäume, Grafikkarte = Prozessor ohne Abweichung (größte Abweichung 1e-6), die Atlanten zu 10 bis 21 % gedeckt.

**Grenzen.**
- Die Impostors haben keine Tiefe je Bildpunkt; Schnitte mit dem Gelände sind die des Vierecks.
- Zwischen den Ansichten blendet das Bild; aus der Nähe wirkt der Übergang weich.
- Im Impostor bewegt sich nichts im Wind.
- Nur stehende Bäume werden Impostors. Geschädigte bleiben bis zur Reichweite Netze (das Gerippe der Stufe 3, um 100 Dreiecke).
- Eine Schattenkarte zeichnet `drawShadow` nur aus den Stufen 0 und 1 der Bäume im Blick.
- Bis 16 Arten. Die Dichte kommt mit 8 Bit, die Höhe ist bilinear im Gitter, nicht im Gelände der App (am Hang kann ein Baum etwas schweben).
- Die Arten aus LituyaBay1958 sind nach Beschreibungen gebaut, nicht vermessen [G].


## Seit LituyaBay1958 W6 Runde 3: Nahschnitt und feine Nadeln

Zwei Schalter für Bäume aus der Nähe. Beide sind voreingestellt aus, andere Szenen bleiben gleich.

- **Nahschnitt** (`ForestGpu.nearCut`, je Art in m, `null` oder 0 = keiner; `nearBand`, Vorgabe 6 m): Stehende Bäume einer Art, die näher am Auge stehen als ihr Schnitt, zeichnet forest nicht. `nearTrees(auge, liste)` liefert genau diese Bäume auf dem Prozessor (aus `ForestCulture`, ohne Sichtkegel), je Baum Art, Variante, Fuß x, y, z, Drehung, Maßstab und Höhe des forest-Modells. Die App zeichnet sie selbst, etwa mit einem feinen Modell aus `com.dan.tree`. Im Band um den Schnitt würfeln beide Seiten mit demselben Wurf je Platz (`u01(h, 10)`), deshalb steht jeder Baum genau einmal. Der Abstand ist der im Raum (Fuß + 8 m), wie bei Reichweite und Stufen. Bäume mit Schaden und der Nachwuchs „fort“ sind nicht betroffen.
- **Feine Nadeln** (`ForestGpu.fineNeedles`, vor `init` setzen; auf dem Prozessor `TreeMesh.build(modell, 0, 0, true)`): In Stufe 0 bekommen Nadelbäume statt der zwei gekreuzten Streifen je Bürste drei Kämme im Winkel von 60°. Je Kamm und Seite stehen fünf Zähne, jeder ein Büschel Nadeln. Die Zähne stehen nach vorn zur Triebspitze, hängen außen leicht und werden zur Spitze kürzer. Innen sind sie dunkel, außen hell. Zwischen den Zähnen bleibt Luft, die dunklen Ballen dahinter füllen die Krone. Das Laub der Stufe 0 hat damit rund siebenmal so viele Dreiecke, das ganze Netz zwei- bis viereinhalbmal so viele (Fichte 65 000 → 208 000). Die Stufen 1 bis 3 bleiben gleich. Wer viele Bäume nah sieht, nimmt `lod0` zurück.

```java
wald.fineNeedles = true;                 // vor init
wald.nearCut = new double[]{0, 0, 40};   // Roterle (Art 2) unter 40 m selbst zeichnen
List<float[]> nah = new ArrayList<>();
wald.nearTrees(auge, nah);               // je Baum: Art, Variante, x, y, z, Drehung, Maßstab, Höhe
```

**Prüfung.** `ForestCheck` baut für jede Nadelart die feinen Bürsten (gültig, mehr als anderthalbmal so viele Dreiecke im ganzen Netz, ohne Nadeln gleich, Stufe 1 gleich). `ForestGpuCheck` prüft den Schnitt in einem sechsten Blick. Was die Grafikkarte wegnimmt, liefert `nearTrees` vollständig. Keiner dieser Bäume steht auf der Grafikkarte, keiner der Grafikkarte steht näher als der Schnitt, und Grafikkarte = Prozessor.

**Grenzen.** `nearTrees` geht alle Plätze im Quadrat um das Auge durch, bei 40 m Schnitt und 4 m Abstand rund 400. Für sehr große Schnitte ist das teuer. Die Farbe der feinen Nadeln ist die der Art, ohne Textur [G].
