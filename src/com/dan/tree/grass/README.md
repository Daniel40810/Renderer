# com.dan.tree.grass – Gras aus Unreal-Assets

Gras und Bodenstücke nach dem Muster von `com.dan.tree`. Jede Art liegt in einem eigenen Ordner `com.dan.tree.grass.<art>`. Der Kern baut auf `com.dan.tree` auf (Format `.dtree`, Backen, Wind, Jahreszeit, Renderer). Er ergänzt nur, was für Gras dazugehört:

- **`GrassField`:** streut Büschel über eine Fläche: ein Raster mit Zellen von 1/√Dichte, Lage, Variante, Drehung und Größe aus einem Hash. Gleicher Startwert ergibt die gleiche Wiese, auch im Ausschnitt. `Mask` hält Flächen frei (etwa einen Weg), `Ground` liefert die Höhe.
- **`GrassSpecies`:** Voreinstellungen auf einer `TreeSpecies`:
  - Form `GRASS` und eigene Stufen aus dem Asset (Megascans LOD0, LOD1, …)
  - Stufengrenzen in Grashöhen (4 / 10 / 25), ab 35 Höhen ausgedünnt, ab 70 weg
  - Wind: Der ganze Büschel biegt sich mit h², jeder Halm wippt mit eigener Phase um seinen tiefsten Punkt.
  - Laub das ganze Jahr: Frühling frisch, Sommer wie die Textur, Herbst gelber, Winter dürr (`seasonFromTexture`).
- **`ground/GrassGround`:** Rasentextur „Mat 025“ als Boden der Demos.
- **Demos** in `com.dan.tree.demo`: `GrassDemo` (Prozessor: Büschel, Wiese, Stufen), `GrassGlDemo` (Wiese mit JOGL) und `GrassCheck` (Selbsttest aller Gräser und der Wiese); die Art ist das erste Argument.

Gehört zu `src/com/dan/tree/` und wird mit ihm kopiert. Die Demos brauchen `FStyle.jar`, `gl/` braucht JOGL.

## Arten

| Paket | Inhalt | Varianten | Stufen aus dem Asset | Dreiecke Stufe 0 je Büschel | Dichte |
|---|---|---|---|---|---|
| `com.dan.tree.grass.wildgrass` | Wildgras, üppig grün (Megascans) | A–H, 0,1–0,4 m | 3 | 700–6 200 | 14 / m² |
| `com.dan.tree.grass.kikuyu` | Kikuyugras (*Cenchrus clandestinus*), Megascans | A–H, 0,2–0,5 m | 3 | 4 300–15 900 | 8 / m² |
| `com.dan.tree.grass.drygrass` | trockenes Gras mit Samenständen (Megascans) | A–E, bis 0,7 m (E liegt flach) | 2 | 240–2 300 | 12 / m² |
| `com.dan.tree.grass.wheatgrass` | Weizengras (*Triticum*) mit Ähren (Megascans) | A–F, bis 1,3 m (F liegt flach) | 2 | 480–6 000 | 6 / m² |
| `com.dan.tree.grass.vegmix` | Vegetationsmix: Rasenstück 2,7 m mit Gräsern, Löwenzahn, Laub | 1 Kachel, 0,7 m | 1 | 196 000 | 0,35 Kacheln / m² |
| `com.dan.tree.grass.footpath` | Fußweg im Gras: Gelände aus Fotogrammetrie (glTF) | 33 m × 42 m | – | 120 000 | – |

Die Megascans-Packs bringen als letzte Stufe ein Billboard mit. Es wird nicht übernommen. Fehlende Stufen entstehen durch Ausdünnen (`TreeSpecies.lodKeep`): Gras behält dabei die höchsten Halme und wird nur breiter, nicht höher, damit die Wiese in der Ferne nicht niedriger wirkt.

## Weg eines Grases

```
Megascans: VarX_LODn.fbx + BaseColor.jpg + Opacity.jpg ─▶ tools/tree/fbx2dtree.py --all-leaf --opacity ─┐
FBX mit mehreren Texturen ─────────────────────────────▶ tools/tree/fbx2dtree.py --all-leaf --atlas   ─┼▶ <art>/*.dtree + Textur ─▶ GrassSpecies ─▶ GrassField ─▶ TreeDemo / gl/TreeGl
glTF-Gelände ──────────────────────────────────────────▶ tools/tree/gltf2dtree.py --terrain           ─┘
```

- **`--all-leaf`:** Alles wird zu Laubkarten (Gras hat keinen Stamm). Der Fuß liegt in der Mitte der tiefsten 5 %.
- **`--opacity`:** Die Deckkraft kommt aus einem eigenen Bild. Megascans legt sie nur in den Rotkanal, deshalb zählt der hellste Kanal, nicht die Helligkeit.
- **`--atlas ordner`:** Je Material wird die erste Textur (nach Dateinamen) in einen Atlas gelegt, und die UVs werden umgerechnet. Der Vegetationsmix hat vier Texturen und wird so zu einer.
- **`gltf2dtree.py --terrain`:** liest glTF mit `.bin`. Die flachste Achse wird zur Höhe, die Mitte liegt auf (0, 0), die mittlere Höhe auf 0. Die Farbtextur wird verkleinert (2048²). Das Netz wird zum „Stamm“ ohne Laub, als Form `GROUND`. Es bewegt sich nicht.

Alles erzeugt `tools/tree/convert_all.sh` neu, auch die Gras-Pakete.

## Benutzung

```java
TreeSpecies gras = WildGrass.species();
GrassField wiese = WildGrass.field();                     // 14 Büschel je m²
wiese.ground = FootPath.ground();                         // etwa auf dem Fußweg-Gelände
wiese.mask = (x, z) -> Math.abs(z) < 1.2f ? 0 : 1;        // Weg frei lassen

TreeGl gl = new TreeGl(gras);                             // Stufen und Ausdünnen aus der Art
for (GrassField.Clump c : wiese.place(gras.variantCount(), -30, -30, 30, 30))
    gl.add(c.variant(), c.x(), c.y(), c.z(), c.yaw(), c.scale());
gl.init(gl4);
// je Bild wie bei Bäumen: setSeason, setWind, update, draw
```

## Grenzen

- Gras wird wie Bäume als Instanzen gezeichnet, eine Wiese von 70 m hat 17 000 bis 46 000 Büschel und 2 bis 10 Mio. Dreiecke. Für größere Flächen muss die App eigene Kacheln nachladen (`GrassField.place` mit Ausschnitt). Die Wiese ist dafür reproduzierbar.
- Die Normal-, Translucency- und Billboard-Texturen der Megascans werden nicht genutzt.
- Der Vegetationsmix ist sehr dicht (196 000 Dreiecke je Kachel). Die Stufen dünnen ihn stark aus (15 / 5 / 2 %) und enden schon nach 60 Höhen.
- Der Prozessor-Renderer braucht für eine Wiese 1 bis 5 s je Bild. Er ist für Bilder und Prüfungen gedacht, nicht für flüssige Bewegung.
- Die Gelände-Höhe des Fußwegs ist ein Raster von 0,25 m (`FootPath.ground()`). Wo der Weg ist, weiß das Paket nicht. Eine `Mask` muss die App setzen.
