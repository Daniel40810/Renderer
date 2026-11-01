# com.dan.clipmap – Gelände bis zum Horizont

**Stand: Phase 4 von LituyaBay1958 (01.11.2026).** Geometrie-Clipmaps um das Auge: feine Zellen am Auge, nach außen in Stufen doppelt so grob, bis zur Kimm. Die Höhen kommen aus einer Quelle, die das Package nicht kennt (Schnittstelle `Source`), oder aus Ordnern mit Höhenkacheln (`TileFolder`, `TileStack`). Ein eigener Faden lädt bei jeder Bewegung nur die neuen Streifen nach. Licht, Material und Dunst zeichnet die App; das Package gibt ihr die Scheitel.

Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` zusätzlich FStyle. Zum Herauskopieren reicht `src/com/dan/clipmap/`.

## Was es kann

- **Stufen:** Jede Stufe ist ein Gitter aus `grid` × `grid` Punkten (`Quality`: LOW 129, MEDIUM 193, HIGH 257, ULTRA 385). Die Zellweite verdoppelt sich von Stufe zu Stufe, ab `cell0` (etwa 0,5 m für Lidar). Es gibt so viele Stufen, dass die gröbste mindestens `range` weit reicht. Mit HIGH, 0,5 m und 140 km sind das 13 Stufen bis 2 048 m Zellweite, und sie reichen 262 km weit.
- **Schachtelung:** Jede Stufe liegt mittig um das Auge. Sie ist auf ein Vielfaches ihrer doppelten Zellweite gerastet. Damit liegen ihre Ränder auf Gitterlinien der nächst gröberen Stufe. Zur gröberen Stufe bleiben mindestens 3 Zellen Rand.
- **Nähte ohne Risse:** In einem Band am Rand (ein Zehntel der Kante) gleitet die Höhe jeder Stufe auf die der gröberen über, ebenso die Normale (Übergang nach Losasso und Hoppe 2004). Am Rand sind beide Höhen gleich, und die Dreiecke der feinen Stufe treffen die Kanten der groben. Die grobe Stufe verwirft im Pixelshader, was die feinere zeichnet (`clipmapHidden`). Ganz bedeckte Blöcke zeichnet sie gar nicht erst.
- **Nachladen:** `start()` startet den Ladefaden, `update(x, z)` je Bild meldet das Auge. Der Faden holt nur die Streifen, die neu ins Fenster kommen. Er legt sie ringförmig in die Speicher der Stufen und gibt alle Stufen zugleich frei. `gl/ClipmapGl.upload` lädt nur die geänderten Rechtecke hoch. Bei einem Sprung lädt er die ganze Stufe neu.
- **Erdkrümmung mit Refraktion:** Jeder Punkt wird um r²/2R' gesenkt, mit R' = R/(1 − 0,13) und r als waagerechtem Abstand zum Auge. Das sind 6,8 m auf 10 km und 683 m auf 100 km. `curvatureRadius = 0` schaltet die Krümmung ab.
- **Kachelordner** (`TileFolder`): `STUFE.txt` mit `zelle`, `kachel_zellen`, `kacheln_ost`, `kacheln_nord`, `suedwest_ost`, `suedwest_nord`. Dazu je Kachel `E<Ost>_N<Nord>.r32` (Südwestecke): float32 Little Endian, Zeilen von Nord nach Süd, NaN ohne Wert.
  - Der Ursprung (e0, n0) legt Ost und Nord in den Raum der Clipmap: x = Ost − e0, z = n0 − Nord.
  - Gelesene Kacheln bleiben im Speicher, bis zu `cacheBytes` (768 MB).
  - Ist die Abtastung gröber als die Kachel, mittelt der Ordner 2 × 2 bzw. 4 × 4 Punkte, damit die Ferne nicht flimmert.
- **Stapel** (`TileStack.folders(dir, e0, n0)`): alle Unterordner mit `STUFE.txt`, von fein nach grob.
  - Für eine Stufe gilt die gröbste Quelle, die noch fein genug ist.
  - Wo sie nichts hat, springt die nächst gröbere ein; außerhalb aller Quellen gilt `Clipmap.noData`.
- **Abfragen ohne Grafikkarte:**
  - `height(x, z)`: aus der feinsten geladenen Stufe, für Kamera und Boden
  - `vertexHeight(level, k, l)`: wie der Shader, mit Übergang
  - `morph(k, l)`, `drop(r)`, `windows()`, `describe()`
- **Sichtfeld:** Jede Stufe hat 8 × 8 Blöcke. `draw` lässt Blöcke außerhalb der vier Seitenebenen weg und auch die, die die feinere Stufe ganz bedeckt. `info()` zählt Dreiecke, Blöcke und die Zeit für das Hochladen.

## Benutzung

```java
TileStack src = TileStack.folders(new File("daten/gelaende"), 352_000, 6_503_000);   // Ursprung Ost, Nord
TileFolder fine = (TileFolder) src.sources()[0];
Clipmap clip = new Clipmap(src, Clipmap.Quality.HIGH, fine.cell, fine.xOfCol(0), fine.zOfRow(0), 140_000);
clip.noData = -200;                // Meeresgrund außerhalb der Daten
clip.start();
// je Bild, im GL-Faden
clip.update(eye[0], eye[2]);
clipGl.upload(gl, clip);
gl.glUseProgram(prog);             // eigenes Programm der App, mit dem Baustein
clipGl.draw(gl, prog, clip, viewProj, eye, 3);
float boden = clip.height(x, z);   // NaN, solange nichts geladen ist
```

Scheitelshader der App:

```glsl
#define CLIPMAP_VERTEX
#include "/com/dan/clipmap/gl/clipmap.glsl"
out vec3 vWorld; out vec3 vNormal;
void main() {
    ClipVertex c = clipmapVertex();          // world (mit Krümmung), normal, height, morph
    vWorld = c.world; vNormal = c.normal;
    gl_Position = uVP * vec4(c.world, 1.0);
}
```

Im Pixelshader (mit demselben Baustein, ohne `CLIPMAP_VERTEX`) als Erstes `if (clipmapHidden(vWorld.xz)) discard;`. Der Eingang `aClipGrid` liegt auf Stelle `ClipmapGl.ATTRIB` (0).

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Clipmap` | Kern: Stufen, Fenster, Ladefaden, Speicher, Übergang, Abfragen |
| `Source` | Schnittstelle der Höhen (`height`, `fill`, `finestCell`) |
| `TileFolder` | ein Ordner mit Höhenkacheln (`STUFE.txt`, `.r32`), Speicher der gelesenen Kacheln |
| `TileStack` | mehrere Quellen von fein nach grob |
| `gl/ClipmapGl` | Gitter, Blöcke, Texturfeld R32F, Hochladen, Zeichnen mit Auswahl nach Sichtfeld |
| `gl/clipmap.glsl` | Baustein: `clipmapVertex()`, `clipmapHidden()` |
| `demo/ClipmapDemo` | Vorschau mit Himmel, Dunst und Licht. Steuerung: Maus, W A S D, Q/E. Regler für Qualität, Sonne, Krümmung, eingefärbte Stufen und Gitter. `--daten <ordner> <ost> <nord>` und `--auge x z höhe richtung`; `--bild datei.png` zeichnet vier Ansichten mit der Zeit je Bild auf der Grafikkarte |
| `demo/ClipmapCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“; `--daten …` vergleicht 1 000 Stichproben mit den Kacheln; `--gpu` rechnet den Baustein auf der Grafikkarte gegen Java (`ClipmapGpuCheck`) |

## Prüfung

`ClipmapCheck` prüft auf einem gerechneten Gelände:
- Zahl und Reichweite der Stufen je Qualität
- den Übergang (0 innen, 1 am ganzen Rand)
- 60 Runden mit Schritten und Sprüngen: Stichproben aller Stufen gleich der Quelle, die Schachtelung und die **Naht**. Dabei liegt jeder Randscheitel auf der Kante der gröberen Stufe, die größte Abweichung ist 7,6e-6 m (Rundung in float).
- die Höhe am Auge, die Erdkrümmung und den Ladefaden
- einen kleinen Kachelordner zur Probe (Lage der Zellmitten; die Clipmap genau gleich der Datei)

Phase 4 hat den Baustein außerdem mit Mesa (llvmpipe) gegen Java gerechnet. Höhen, Übergang und Naht stimmten in allen Stufen auf 1,2e-4 m.

## Seit welcher Phase

Phase 4 von LituyaBay1958 (Werkbuch 5.11). Geplant: virtuelle Textur für Material und Farbe mit `surface` (Phase 5).

## Grenzen

- Die Normale kommt aus Differenzen im Gitter der Stufe. Feiner als die Zelle zeigt sie nichts; Feinform und Material kommen mit `surface`.
- Die Stufen sind quadratisch und um das Auge zentriert; in Blickrichtung wird nichts verlängert.
- Bei Höhen über 4 000 m und Blick zur Kimm zeichnet die gröbste Stufe mit 2 048 m Zellweite. Die Berge in 200 km Entfernung sind dann nur grob.
- Die Höhe liegt in float im Shader. Zellweiten ab 0,5 m und Abstände bis 260 km bleiben genau (Ganzzahlen bis 2²⁴).
- In GLSL ist `%` mit negativen Zahlen undefiniert. Der Baustein rechnet den Rest deshalb selbst (`clipMod`; Befund Phase 4: auf Mesa waren die Zeilen verschoben).
