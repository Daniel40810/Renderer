# com.dan.tree.mobiletree – Mobile-Baum

Laubbaum aus „Mobile Tree Packs“ (`Assets/Trees/mobile_trees_model.zip`, ASCII-FBX mit TGA-Texturen), 24 m hoch, mit rund 2 000 Dreiecken. Zweigbüschel sind als Karten aus einem Textur-Atlas gebaut, die Rinde ist hell, ein Skelett gibt es nicht. Das Pack ist für viele Bäume auf schwacher Hardware gedacht. Die Art gibt es nicht an.

| Datei | Höhe | Karten | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|---|
| `mobiletree.dtree` (0,04 MB), `mobiletree_bark.jpg`, `mobiletree_leaf.png` | 24,2 m | 420 | 1 943 / 1 012 / 407 / 190 |

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo laubbaum-leicht [ansicht variante stufe tag]`: Vorschau auf dem Prozessor. Ansicht 0 Einzelstück, 1 Hain mit Stufen nach Entfernung, 2 alle Stufen nebeneinander. Ohne Fenster: `TreeDemo laubbaum-leicht --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo laubbaum-leicht [bäume] [fläche m] [tag]`: Bestand mit JOGL. Kamera, Abstand und Fläche (14 Höhen) richten sich nach der Höhe. Ohne Fenster: `TreeGlDemo laubbaum-leicht --bild datei.png [bäume] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.TreeCheck laubbaum-leicht`: Selbsttest ohne Fenster, endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh` (alle Arten).
