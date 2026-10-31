# com.dan.wetglass – Tropfen auf der Scheibe

Das Paket macht eine Glasscheibe im Regen nass. Einschläge setzen Tropfen ab. Kleine Tropfen bleiben haften, große laufen im Zickzack hinab, nehmen kleinere mit und lassen eine Spur aus Resttröpfchen und einen nassen Film zurück. Jeder Tropfen ist eine kleine Linse: Er zeigt die Welt dahinter kopfüber und verkleinert, sein Rand ist dunkel, weil das Licht dort im Wasser zurückgeworfen wird. Was zu klein ist, um einzeln gezeichnet zu werden, macht die Scheibe milchig.

Dazu kommen drei Schichten, die keine einzelnen Tropfen sind: Beschlag auf der Innenseite, eine Salzkruste außen und die Wasserhaut, die ein Schwall über die Fläche legt. Und dieselbe Rechnung läuft auf gekrümmten Flächen, etwa um ein Rohr herum.

Der Kern braucht nur Java 21 und rechnet die Tropfen auf dem Prozessor. Der Ordner `gl/` (JOGL, GL 4.3) zeichnet daraus eine Höhen- und Normalenkarte und liefert einen Baustein `wetglass.glsl`, den ein fremder Fragment-Shader einbindet. Das Paket kennt weder Regen noch Turm noch andere Pakete: Wie viele Tropfen kommen, wie groß sie sind und woher der Wind weht, sagt der Aufrufer. Zum Herauskopieren reicht `src/com/dan/wetglass/`; nur `demo/WetglassDemo` braucht zusätzlich `FStyle.jar`.

**Seit Brocken Phase 1 im Projekt Renderer** (Brocken F19 A): aus dem Projekt Lighthouse unverändert hierher geholt, damit jede Szene es aus `Renderer.jar` nehmen kann (am Brocken: die Fenster der Lok und des Brockenhauses im Nebel). Lighthouse behält bis zu seinem nächsten Bau mit dem neuen `Renderer.jar` seine eigene Kopie; danach gehört `src/com/dan/wetglass/` dort gelöscht, sonst liegen die Klassen doppelt im Klassenpfad.

## Was es kann

- **Tropfen als Kugelkappe:** Ein Tropfen sitzt mit 60° Randwinkel auf dem Glas. Ein Regentropfen von 2 mm Durchmesser bedeckt einen Kreis von gut 1,5 mm Halbmesser. Volumen und Höhe folgen aus der Kappe (`volume`, `radiusForVolume`, `capHeight`); beim Verschmelzen bleibt das Wasser genau erhalten.
- **Haften und Laufen:** Jede Stelle des Glases hält verschieden fest (Haftkarte 128 × 128, Faktor 0,6 bis 1,5). Ein Tropfen läuft, wenn er größer ist als `A_CRIT` = 1,6 mm, skaliert mit Haftung und Antrieb. Der Antrieb ist die Neigung (`slopeX`, `slopeY`) plus der Wind auf der Fläche (`windX`, `windY`), kleine Tropfen spüren den Wind stärker. Läufer gehen ruckweise, suchen sich den Weg entlang der schwächeren Haftung und werden mit der Größe schneller (bis 0,5 m/s).
- **Spuren:** Ein Läufer verliert alle paar Tropfenbreiten ein Resttröpfchen und zieht einen Film. Auf dem Film haftet es um bis zu 45 % schwächer, darum folgen spätere Läufer gern derselben Bahn. Der Film trocknet in `filmLife` Sekunden.
- **Verschmelzen:** Berühren sich zwei Tropfen, werden sie einer, mit der Summe des Wassers, am gewichteten Ort. Ein Raster sucht die Nachbarn; auf der Kachel auch über den Rand hinweg.
- **Feintropfen:** Einschläge unter 0,5 mm Halbmesser werden nicht einzeln geführt. Sie gehen als Bedeckung in ein Raster und werden im Shader aus Zellen von 1,1 mm gezeichnet. Über 70 % Bedeckung fließen sie zu richtigen Tropfen zusammen, ein Läufer nimmt sie mit.
- **Regen:** `rain(mm/h, Wind, Ausrichtung)` rechnet die Einschläge je m² und Sekunde nach Marshall und Palmer und die Größenverteilung (`hitLambda`). Eine senkrechte Scheibe fängt nur den Anteil, den der Wind schräg treibt; eine liegende fängt alles. Man kann `hitRate` und `hitLambda` auch direkt setzen.
- **Kachel:** Mit `periodic = true` (Standard) ist die Fläche eine Kachel von 0,35 m, die sich nahtlos wiederholt. Ein Läufer, der unten hinausläuft, kommt oben wieder herein, wenn die Scheibe länger ist als die Kachel (`drainLength`), sonst läuft er ab. So reicht eine Kachel für viele Scheiben.
- **Beschlag:** `condensation` ist Wasser, das je Sekunde innen kondensiert (Schichtdicke in m/s; negativ trocknet es). Voll ist der Beschlag bei 1 µm (1 g/m²). `wipeInside(x, y, r)` wischt einen Kreis mit weichem Rand, `wipeAllInside()` alles. Wo gewischt wurde, bleibt ein Schmierfilm, auf dem der Beschlag nur mit 40 % des Tempos wiederkommt und der in einer Viertelstunde verblasst; so bleibt die Spur eine Weile sichtbar.
- **Salz:** `saltRate` legt Salz aus dem Sprühnebel ab (Bedeckung je Sekunde; 1 entspricht rund 5 g/m², einer weißen Kruste), an rauen Stellen mehr. `spray(x, y, d)` ist ein salziger Einschlag: Das Seewasser (35 g/l) lässt sein Salz um den Ort. Süßwasser löst es wieder: Jeder Regentropfen nimmt so viel, wie er lösen kann (14 µm Wasser für eine volle Kruste), ein Läufer zieht eine salzfreie Spur. Salz macht die Fläche rauer, Tropfen haften dort bis zu 60 % fester.
- **Überspülung:** `flood(mm, salty)` legt eine Wasserhaut über die ganze Fläche. Sie nimmt alle Tropfen und Feintropfen auf (das Wasser bleibt), läuft ab, dünne Haut langsamer als dicke (die Menge fließt mit der dritten Potenz der Dicke), und zerreißt unter 0,06 mm in viele Tropfen und Feintropfen. War es Seewasser, bleibt danach Salz. Der Shader zeichnet die Haut mit Wellen, die in Fließrichtung ablaufen, und Rinnsalen quer dazu.
- **Gekrümmte Flächen (F10):** Mit `surface` sagt eine `Surface` je Ort, wohin die Schwerkraft entlang der Fläche zieht, ob ein Tropfen aufliegt, an einer senkrechten Wand sitzt oder darunter hängt, wie der Wind entlang der Fläche weht und wie sehr der Ort dem Regen zugewandt ist. Gerechnet wird im abgewickelten Flächenraum, sonst ändert sich nichts. Hängende Tropfen fallen ab einer Größe ab (gut 2,4 mm Halbmesser) und lassen einen Rest. `Cylinder` ist so eine Fläche: liegend (Handlauf, Rohr) liegen die Tropfen oben, laufen über die Flanken, sammeln sich unten und fallen; stehend (Pfosten, Turmwand) umströmt sie der Wind, an den Flanken doppelt so schnell, dahinter still, und schiebt die Tropfen zur Seite; der Regen trifft nur die zugewandte Seite.
- **Wischen und Trocknen:** `wipe(x, y, r)` räumt einen Kreis ab und lässt nur Film, `wipeAll()` die ganze Scheibe, `wipeStroke(punkte, r)` einen ganzen Strich eines Scheibenwischers auf einmal (seit Phase 13); beides nimmt auch das Salz mit. `evaporation` lässt Tropfen langsam schrumpfen.
- **Zeichnen:** `WetglassGl.update` legt die Tropfen als Kappen in eine Höhenkarte (R16F, größte Höhe gewinnt), rechnet daraus Höhe, Film und Steigung (RGBA16F mit Mipmaps) und lädt Film, Feintropfen, Beschlag und Salz hoch (RGBA32F). Der Baustein `wetglass.glsl` liefert `wgSample` (Karte samt Feintropfen), `wgCover`, `wgRim`, `wgNormal`, `wgHaze`, `wgFog`, `wgSalt`, `wgSheet` und `wgOffset`, die Verschiebung des Hintergrunds, mit der ein Tropfen die Welt kopfüber zeigt. Der dunkle Rand und die Feintropfen blenden aus, sobald ein Bildpunkt zu groß für sie wird, damit nichts flimmert.
- **Reproduzierbar:** gleicher `seed`, gleiche Tropfen. Die Grafikkarte zeichnet genau die Tropfen des Prozessors; `selfCheck` vergleicht die Höhe an 4 096 Stellen, im Abgleich auf ± 0,0 µm.
- **Stufen:** `LOW`, `MEDIUM`, `HIGH`, `ULTRA` wählen die Höchstzahl der Tropfen (1 500 bis 6 000) und die Auflösung der Karte (512² bis 2048²).

## Benutzung

```java
Wetglass g = Wetglass.breeze();            // calm(), breeze(), gale(), storm(), beaufort(n)
g.quality = Wetglass.Quality.HIGH;
g.drainLength = 1.05;                      // so lang ist die echte Scheibe (m)
g.stepRate = 30;                           // Schritte je Sekunde (Rechenzeit)

// je Bild
g.rain(regen, windGegenDieScheibe, 1);     // oder g.hitRate / g.hitLambda direkt
g.windX = windQuer;                        // m/s in der Ebene der Scheibe
g.step(dt);

// vor dem Zeichnen der Scheibe
WetglassGl wg = new WetglassGl();
wg.init(gl4);
wg.update(gl4, g.snapshot());

// Beschlag, Salz, Überspülung
g.condensation = 3e-9;                     // drinnen wärmer und feuchter als das Glas
g.saltRate = 1e-4;                         // Sprühnebel im Sturm
g.flood(1.2, true);                        // ein Brecher: 1,2 mm Seewasser

// gekrümmt: ein Handlauf von 7 cm, abgewickelt
Wetglass.Cylinder rohr = new Wetglass.Cylinder(0.035, true);
Wetglass h = new Wetglass();
h.surface = rohr;
h.width = rohr.circumference();
rohr.wind = 8;
h.rainAcross(regen, wind);                 // Einschläge auf eine Fläche quer zum fallenden Regen

// im eigenen Shader:  #include "/com/dan/wetglass/gl/wetglass.glsl"
//   vec4 s = wgSample(flaechenOrt);  float cov = wgCover(s);
//   vec2 ax, ay; wgScreenAxes(flaechenOrt, uViewport, ax, ay);
//   vec3 bild = texture(uSzene, uv + wgOffset(s, ax, ay, 0.3)).rgb;
wg.bind(gl4, programm, 9);                 // Karte auf Einheit 9, Film auf 10
```

Die Quelle des Bausteins gibt `WetglassGl.includeSource()` zurück, falls der eigene Shader-Lader `#include` nicht kennt. Die Flächenkoordinaten laufen in Metern, +x nach rechts, +y nach oben.

## Aufbau

| Datei | Inhalt |
|---|---|
| `Wetglass.java` | Kern: Tropfen, Haftkarte, Film, Feintropfen, Laufen, Verschmelzen, Einschläge, Wischen, Kachel, Voreinstellungen, `Snapshot` mit `heightMm` |
| `gl/WetglassGl.java` | Höhen- und Normalenkarte, Film-Textur, `bind`, `selfCheck` |
| `gl/wg_drop.vert`, `gl/wg_height.frag` | Kappen als Instanzen in die Höhenkarte (vier Kopien je Tropfen auf der Kachel) |
| `gl/wg_normal.frag` | Höhe, Film, Steigung aus der Höhenkarte |
| `gl/wetglass.glsl` | Baustein für fremde Shader: Feintropfen, Bedeckung, Rand, Normale, Verschiebung, Milchigkeit |
| `gl/check.frag`, `gl/fullscreen.vert` | Abgleich und Vollbild |
| `demo/WetglassDemo.java`, `demo/WetglassScene.java` | Fenster mit Hintergrund und Scheibe oder Zylinder, `--bild` |
| `demo/pane.frag`, `demo/tube.frag` | Scheibe vor dem Hintergrund; ein lackiertes Rohr, auf das der Strahl analytisch trifft |
| `demo/WetglassCheck.java` | Prüfung ohne Fenster |

## Demo und Selbsttest

- `com.dan.wetglass.demo.WetglassDemo` zeigt eine Scheibe, einen Handlauf oder einen Pfosten vor einem Bild von See und Turm. Es gibt Regler für Regen, Wind, Neigung der Scheibe, Ausschnitt, Beschlag, Sprühnebel, Fokus, Zeit und Stufe, dazu „Überspülen“, „Alles wischen“ und „Trocken“. Ziehen mit der Maus wischt außen und innen.
- Ohne Fenster rechnet `WetglassDemo --bild datei.png [breite]` zwölf Bilder: die ersten Tropfen, nach einer Minute, nach drei Minuten, nach dem Wischen, eine Nahaufnahme mit dem Turm kopfüber, eine geneigte Scheibe; Beschlag mit Wischspur, eine Überspülung und das Salz danach; einen Handlauf und einen Pfosten im Regen und einen Handlauf im Sprühnebel. Dabei vergleicht es Grafikkarte und Prozessor, auch auf dem Zylinder.
- `com.dan.wetglass.demo.WetglassCheck` prüft ohne Fenster und ohne Grafikkarte, mit 21 Prüfungen; Ende „Alles in Ordnung.“:
  - Kugelkappe, Höhe in der Mitte
  - Haften, Laufen, Neigung, Wind
  - Verschmelzen ohne Wasserverlust, Resttröpfchen und Film
  - Einschläge aus dem Regen, Feintropfen, Wischen, Kachel
  - Beschlag mit Wischspur, Salz aus Sprühnebel und Gischt, gelöst von Läufer und Regen, Überspülung mit Wasserbilanz
  - liegender Zylinder (oben liegen, Flanke laufen, unten abfallen, Regen nur oben), stehender im Wind (vorn nass, hinten trocken)
  - Grenzen, Stufen, Voreinstellungen, Reproduzierbarkeit

## Grenzen

- Die Kachel wiederholt sich alle 0,35 m. Aus der Nähe sieht man das nicht, über eine ganze Laterne hinweg kann ein aufmerksames Auge dieselbe Spur zweimal finden; verschiedene Scheiben verschieben die Kachel darum gegeneinander. Auch eine Wischspur im Beschlag wiederholt sich mit der Kachel.
- Die Tropfen laufen auf dem Prozessor (etwa 1 ms je Schritt bei 3 000 Tropfen). Wer mehrere Flächen rechnet, rechnet sie am besten in einem eigenen Faden; `step` und alle Eingriffe sind synchronisiert, `snapshot()` ist jederzeit lesbar. Seit Sylt Phase 19 gibt ein langer `step` (etwa ein Nachholen über 30 s) die Fläche nach je 32 inneren Schritten kurz frei; ein Eingriff aus einem anderen Faden wartet dann Millisekunden statt einer Sekunde.
- Beschlag ist eine Bedeckung, keine Tropfen: Er läuft nicht ab, auch wenn er sehr dick wird. Tropfen außen kühlen das Glas dahinter nicht.
- Salz in Tropfen wird nicht mitgeführt: Ein Tropfen, der trocknet, lässt nichts zurück; Salz kommt nur aus Sprühnebel, salzigen Einschlägen und der Haut einer Überspülung.
- Die Haut einer Überspülung ist gleich dick auf der ganzen Kachel; ihre Wellen zeichnet der Shader, die Simulation kennt nur die Dicke.
- Gekrümmte Flächen werden abgewickelt: Die Fläche selbst muss sich abwickeln lassen (Zylinder, Kegel, Wand). Eine Kuppel oder Kugel geht nur in Stücken, deren Verzerrung man in Kauf nimmt.
- Die Feintropfen werden im Shader aus einem Hash gezeichnet; der Abgleich mit dem Prozessor prüft nur die geführten Tropfen.
- Ein Tropfen bricht das Bild hinter der Scheibe nur nach seiner Steigung (eine Näherung der Linse), nicht mit echter Strahlverfolgung durch das Wasser.
