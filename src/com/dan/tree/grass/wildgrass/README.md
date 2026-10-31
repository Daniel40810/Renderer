# com.dan.tree.grass.wildgrass – Wildgras

Wildgras aus Megascans „Wild Grass“: acht üppige, grüne Büschel von 0,1 bis 0,4 m, je drei Stufen aus dem Asset (LOD0 bis LOD2; das Billboard LOD3 bleibt weg). Die Laubtextur kommt aus BaseColor und Opacity (2K → 1024²).

| Variante | Höhe | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|
| A | 0,36 m | 1 598 / 1 246 / 862 / 342 |
| B | 0,17 m | 6 240 / 2 328 / 842 / 280 |
| C | 0,15 m | 3 600 / 2 782 / 978 / 312 |
| D | 0,26 m | 1 246 / 978 / 660 / 270 |
| E | 0,17 m | 728 / 568 / 380 / 128 |
| F | 0,14 m | 2 258 / 1 888 / 1 518 / 466 |
| G | 0,10 m | 1 078 / 848 / 576 / 174 |
| H | 0,10 m | 1 282 / 974 / 732 / 242 |

Wiese: 14 Büschel je m². Die Jahreszeit kommt aus der Textur (grün, `seasonFromTexture(…, 1)`): ab Tag 85 frisch, ab Tag 255 vergilbt, ab Tag 330 dürr.

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo wildgras [ansicht variante stufe tag]`: Vorschau auf dem Prozessor, auf dem Rasenboden. Ansicht 0 Büschel, 1 Wiese (Kreis von 30 Höhen, Stufe nach Entfernung, in der Ferne ausgedünnt), 2 alle Stufen. Ohne Fenster: `TreeDemo wildgras --bild datei.png [einzel|wiese|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo wildgras [dichte je m²] [fläche m] [tag]`: Wiese mit JOGL. Ohne Fenster: `TreeGlDemo wildgras --bild datei.png [dichte] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.GrassCheck wildgras`: prüft die Wiese (gleicher Startwert, Dichte, Ausschnitt, Maske) und die Art (Format, Stufen, Wind, Jahreszeit). Endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh`.
