# com.dan.tree.hptree – HighPoly-Baum

Großer Laubbaum aus „HighPoly Tree Packs“ (`Assets/Trees/highpoly_tree_model.zip`): FBX, 27,5 m hoch, mit 12 926 einzelnen Blättern als Karten mit herzförmiger Blatttextur und einem Stamm mit Rindentextur, ohne Skelett. Die Art gibt das Pack nicht an.

| Datei | Höhe | Karten | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|---|
| `hptree.dtree` (0,8 MB), `hptree_bark.jpg`, `hptree_leaf.png` | 27,5 m | 12 926 | 27 800 / 10 300 / 3 300 / 326 |

Weil jede Karte ein einzelnes Blatt ist, zittert das Laub wie bei einem echten Baum (Zittern 1,2). Die Jahreszeit kommt aus der Laubtextur (`TreeSpecies.seasonFromLeafTexture`).

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo grosser-laubbaum [ansicht variante stufe tag]`: Vorschau auf dem Prozessor. Ansicht 0 Einzelstück, 1 Hain mit Stufen nach Entfernung, 2 alle Stufen nebeneinander. Ohne Fenster: `TreeDemo grosser-laubbaum --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo grosser-laubbaum [bäume] [fläche m] [tag]`: Bestand mit JOGL. Kamera, Abstand und Fläche (14 Höhen) richten sich nach der Höhe. Ohne Fenster: `TreeGlDemo grosser-laubbaum --bild datei.png [bäume] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.TreeCheck grosser-laubbaum`: Selbsttest ohne Fenster, endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh` (alle Arten).
