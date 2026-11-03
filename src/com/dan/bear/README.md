# com.dan.bear – Küstenbraunbären

**Stand: Stufe 18 – Körper, Gangarten und Verhalten (LituyaBay1958 Phase 18a, Werkbuch 5.14).**

Küstenbraunbären (*Ursus arctos*) als Körper und Verhalten. Sie ziehen in ihrem Gebiet umher, schnüffeln, graben am trockenen Strand nach Muscheln, fischen von Juli bis September am Bach, fressen und ruhen. Vor Menschen und Booten richten sie sich erst auf und nehmen Witterung, dann weichen sie aus oder fliehen im Galopp. Beim Beben laufen alle bergauf, weg vom Wasser. Eine Bärin führt ihre Jungen dicht bei sich.

Das Package kennt kein Gelände: Grund, Wasser, Bäche, Strände und Störer kommen von außen. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` FStyle. Wiederverwendbar für Grizzlys (Preset) und, mit anderen Maßen, für Schwarzbären.

Zum Herauskopieren reicht `src/com/dan/bear/`.

## Was es kann

- **Körper** (`BearBody`): rund 3 700 Dreiecke in Körperkoordinaten (Kopf-Rumpf-Länge 1) [G, nach Fotos und Maßen von Küstenbraunbären]:
  - schwerer Rumpf mit dem Buckel über den Schultern;
  - breiter Kopf mit gerader Schnauze, kleinen runden Ohren und einem Unterkiefer, der sich öffnet;
  - Schwanzstummel;
  - vier Beine aus je zwei Gliedern und einer Pfote: vorn Zehengänger mit langen Krallen, hinten Sohlengänger.
- **Biegung** wie bei `com.dan.seal`:
  - Die Wirbelsäule biegt sich um die Hüfte: Kopf heben, Kopf drehen, Buckel, ganze Neigung bis zum Aufrichten auf die Hinterbeine.
  - Danach dreht die Rolle den Körper (Liegen).
  - Die Beine drehen um ihre Gelenke: Schwung und Beugung je Bein. Schulterblatt und Becken gleiten mit.
  - Java (`deform`) und `gl/bear.vert` rechnen dasselbe, auf 1e-6 m genau gegen Mesa geprüft.
- **Gelenke gelöst** (`BearBody.solveLeg`): Die Sohle steht genau, wo der Gang sie haben will (zwei Glieder, Kosinussatz, die Pfote nachgeführt).
- **Gangarten** (`Bear.Gait`, `Bear.legs`, `Bear.stand`):

  | Gang | Fußfolge | Stand | bei 2,2 m Länge |
  |---|---|---|---|
  | Gehen | hinten links, vorn links, hinten rechts, vorn rechts | 62 % | bis 1,7 m/s, 0,95 Doppelschritte/s bei 1,25 m/s |
  | Traben | diagonale Paare | 46 % | bis 4,6 m/s |
  | Galopp | Rotationsgalopp, die Wirbelsäule beugt und streckt sich | 34 % | bis 13 m/s |

  - Die Pfoten im Stand stehen auf einer Ebene, die im Pendel des Gangs steigt und sinkt.
  - Im Gehen und Traben rutschen sie um höchstens 4 %, im Galopp um 6 %.
- **Verhalten** (`Sleuth`):
  - Tätigkeiten: umherziehen, schnüffeln, graben (nur wenn der Strand trocken liegt), fischen, fressen, liegen (öfter bei hoher Sonne), stehen, sich aufrichten, fliehen.
  - Fischen: Der Bär steht im flachen Wasser, den Kopf tief, und springt alle 40 bis 180 s nach einem Lachs; jeder vierte Sprung gelingt [G]. Den Lachs frisst er 1 bis 2,5 min.
  - Störer: Unter 160 m richtet er sich auf (Witterung), dann weicht er im Trab aus. Unter 80 m flieht er im Galopp (eine Bärin mit Jungen schon unter 112 m, und nur so schnell, wie die Jungen mitkommen). Danach bleibt er 10 min scheu: kein Liegen, Ziele weg vom Störer. Boote zählen halb so nah [G].
  - `quake(stärke)`: alle fliehen 25 bis 60 s bergauf.
  - Die Jungen folgen der Mutter in 3 m und rufen, wenn sie zurückbleiben.
  - Das Fell wird im Wasser nass und trocknet in 15 min halb.
- **Fell** (`gl/bear.frag`):
  - vom dunklen Schokoladenbraun bis Blond (je Tier);
  - der Rücken heller mit hellen Spitzen über dem Buckel, die Beine dunkel, die Schnauze heller;
  - Nase und Augen dunkel, die Krallen hell;
  - Strähnen längs des Körpers, die sich im Wind bewegen (`Light.wind`, `Light.time`), Gegenlicht am Rand;
  - nass dunkler, verklebt und glänzend.
  - Je Tier ein weicher Schatten auf dem Grund (`gl/shadow.*`).
- **Ereignisse** für die Szene:
  - `onCall`: Schnauben, Kieferklappern, Brummen, Junges (für den Klang);
  - `onTrack`: Trittsiegel jeder Pfote auf trockenem Grund (für Sand und Schlick);
  - `onCatch`: ein Lachs ist gefangen.
- **Presets und Zahl:** `coastalBrown()`, `grizzly()`. `Quality` regelt die Zahl: 3, 5, 7 oder 8 Tiere bei höchstens 8, die Jungen dazu.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Bear` | Art: Presets, offene Werte, Gangarten, Beine im Gang |
| `BearBody` | Netz und Biegung (wie `bear.vert`), Gelenke lösen, Sohlen, Nase, höchster Punkt |
| `Sleuth` | Gruppe in ihrem Gebiet: Verhalten, Bewegung, Haltung, Lage; `World`, `Stream`, `Beach`, `Range`, `Disturber` als Eingänge |
| `gl/BearGl` | Netz, Instanzen (24 Werte je Tier), Schatten; `bear.vert`, `bear.frag`, `shadow.vert`, `shadow.frag` |
| `demo/BearDemo` | Fenster mit dem Bären von nah (Gangarten, Haltungen) und der Bucht von oben (Wanderer per Klick, „Beben“); `--bild datei.png` schreibt den Bogen, `--glbild datei.png` zeichnet die Haltungen mit `BearGl` |
| `demo/BearCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |
| `demo/DemoBay`, `demo/Soft`, `demo/BearGlBild` | gerechnete Bucht, Software-Rasterer für die Demo, Bild mit der Grafikkarte |

## Einsetzen

```java
Sleuth s = new Sleuth(Bear.coastalBrown());
s.world = new Sleuth.World() { ... ground(x, z) ... water(x, z) /* NaN, wo trocken */ };
s.streams.add(new Sleuth.Stream("Bach", spots));        // Plätze {x, z} zum Fischen
s.beaches.add(new Sleuth.Beach(x, z, 40));
s.ranges.add(new Sleuth.Range(x, z, 600));
s.count = 3; s.cubs = 2; s.month = 7; s.sunAltDeg = 30;
s.onTrack = (x, z, dx, dz, leg, len) -> sand.addTrack(x, z, dx, dz);
s.settle();                                             // nach Sprüngen der Uhr
s.disturbers.clear(); s.disturbers.add(new Sleuth.Disturber(fx, fz, 1, false));
s.step(szenenSekunden);                                 // in Teilschritten von höchstens 0,05 s
if (beben) s.quake(1);
// im GL-Faden: nach dem Gelände, vor dem Wasser
bearGl.update(gl, s); bearGl.draw(gl, view, light);
```

## Grenzen

- Die Glieder sind starre Röhren, das Fell ist gemalt. Es gibt keine Fellschalen und keine Haare über den Umriss.
- Die Beine stehen auf einer Ebene durch die Hüfte und folgen dem Gelände nur über die Neigung des Rumpfs. Auf Geröll und an Stufen schweben einzelne Pfoten bis 5 cm.
- Im Galopp setzen die Pfoten nicht ganz flach auf (bis 5 cm).
- Bären schwimmen nicht: Wasser über 0,4 m meiden sie, beim Fischen gehen sie bis 0,9 m hinein.
- Sie berühren einander nicht; Kämpfe, Paarung, Tagesverstecke und Winterruhe fehlen.
- Zeiten, Weiten und Erfolge sind gesetzt [G] und gegen Beschreibungen (ADF&G, NPS) gestaltet, nicht gegen Filmaufnahmen abgenommen.
