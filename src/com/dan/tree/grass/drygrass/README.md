# com.dan.tree.grass.drygrass – Trockenes Gras

Trockenes Gras aus Megascans „Dry Grass“: fünf Büschel aus dürren Halmen mit Samenständen, je zwei Stufen aus dem Asset (LOD0, LOD1; das Billboard LOD2 bleibt weg). Die Art ist nicht angegeben.

| Variante | Höhe | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|
| A | 0,67 m | 2 252 / 1 106 / 362 / 154 |
| B | 0,58 m | 1 743 / 851 / 271 / 135 |
| C | 0,46 m | 840 / 412 / 152 / 80 |
| D | 0,10 m | 988 / 488 / 160 / 40 |
| E | flach | 236 / 100 / 44 / 12 |

Wiese: 12 Büschel je m². Das Gras ist schon dürr, die Farbe ändert sich über das Jahr kaum (`seasonFromTexture(…, 0,15)`).

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo trockengras [ansicht variante stufe tag]`: Vorschau auf dem Prozessor, auf dem Rasenboden. Ansicht 0 Büschel, 1 Wiese (Kreis von 30 Höhen, Stufe nach Entfernung, in der Ferne ausgedünnt), 2 alle Stufen. Ohne Fenster: `TreeDemo trockengras --bild datei.png [einzel|wiese|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo trockengras [dichte je m²] [fläche m] [tag]`: Wiese mit JOGL. Ohne Fenster: `TreeGlDemo trockengras --bild datei.png [dichte] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.GrassCheck trockengras`: prüft die Wiese (gleicher Startwert, Dichte, Ausschnitt, Maske) und die Art (Format, Stufen, Wind, Jahreszeit). Endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh`.
