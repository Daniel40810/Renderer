# com.dan.tree.grass.wheatgrass – Weizengras

Weizengras (*Triticum*) aus Megascans „Wheat Grass“: sechs Büschel mit Ähren, reif und trocken, je zwei Stufen aus dem Asset.

| Variante | Höhe | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|
| A | 1,27 m | 6 048 / 1 126 / 292 / 90 |
| B | 1,11 m | 2 880 / 586 / 164 / 58 |
| C | 1,26 m | 3 508 / 696 / 160 / 72 |
| D | 1,05 m | 1 380 / 342 / 106 / 46 |
| E kurz | 0,61 m | 488 / 88 / 20 / 8 |
| F liegend | flach | 482 / 141 / 49 / 27 |

Wiese: 6 Büschel je m². Die Farbe ändert sich über das Jahr wenig (`seasonFromTexture(…, 0,2)`).

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo weizengras [ansicht variante stufe tag]`: Vorschau auf dem Prozessor, auf dem Rasenboden. Ansicht 0 Büschel, 1 Wiese (Kreis von 30 Höhen, Stufe nach Entfernung, in der Ferne ausgedünnt), 2 alle Stufen. Ohne Fenster: `TreeDemo weizengras --bild datei.png [einzel|wiese|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo weizengras [dichte je m²] [fläche m] [tag]`: Wiese mit JOGL. Ohne Fenster: `TreeGlDemo weizengras --bild datei.png [dichte] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.GrassCheck weizengras`: prüft die Wiese (gleicher Startwert, Dichte, Ausschnitt, Maske) und die Art (Format, Stufen, Wind, Jahreszeit). Endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh`.
