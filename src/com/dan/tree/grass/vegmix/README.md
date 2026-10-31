# com.dan.tree.grass.vegmix – Vegetationsmix

Rasenstück aus „Grass Vegitation mix“ (FBX): etwa 2,7 m × 2,5 m, 0,7 m hoch, aus 65 Meshes (Gräser, Löwenzahn, Kräuter, Laub am Boden) mit vier Texturen. Sie sind zu einem Atlas von 1024² zusammengefasst.

| Datei | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|
| `vegmix.dtree` (4,6 MB), `vegmix_leaf.png` | 195 697 / 28 763 / 10 395 / 4 646 |

Als Kachel gestreut, etwa eine je 3 m², beliebig gedreht und überlappend, ergibt sich eine bunte, dichte Wiese. Das Asset hat nur eine Stufe, die übrigen dünnen stark aus (15 / 5 / 2 %). Stufengrenzen 5 / 12 / 30 Höhen, die Kacheln enden nach 60 Höhen (40 m).

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo vegetationsmix [ansicht variante stufe tag]`: Vorschau auf dem Prozessor, auf dem Rasenboden. Ansicht 0 Büschel, 1 Wiese (Kreis von 30 Höhen, Stufe nach Entfernung, in der Ferne ausgedünnt), 2 alle Stufen. Ohne Fenster: `TreeDemo vegetationsmix --bild datei.png [einzel|wiese|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo vegetationsmix [dichte je m²] [fläche m] [tag]`: Wiese mit JOGL. Ohne Fenster: `TreeGlDemo vegetationsmix --bild datei.png [dichte] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.GrassCheck vegetationsmix`: prüft die Wiese (gleicher Startwert, Dichte, Ausschnitt, Maske) und die Art (Format, Stufen, Wind, Jahreszeit). Endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh`.
