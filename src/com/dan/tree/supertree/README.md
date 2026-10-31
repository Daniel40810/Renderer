# com.dan.tree.supertree – Super-Baum

Laubbaum aus „Super Mobile Tree Packs (100 triangles)“ (`Assets/Trees/super_tree_model.zip`), 25 m hoch, mit 410 Dreiecken: wenige große Zweigkarten und ein einfacher Stamm, ohne Skelett. Schon Stufe 0 ist so leicht wie die Hülle anderer Bäume, deshalb eignet er sich für sehr große Bestände. Die Art gibt das Pack nicht an.

| Datei | Höhe | Karten | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|---|
| `supertree.dtree` (0,01 MB), `supertree_bark.jpg`, `supertree_leaf.png` | 25,0 m | 120 | 410 / 233 / 105 / 118 |

Stufe 3 (die Hülle) ist hier nicht leichter als Stufe 2. Wer will, setzt in `TreeGl.lodDistance` die letzte Grenze sehr hoch.

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo laubbaum-400 [ansicht variante stufe tag]`: Vorschau auf dem Prozessor. Ansicht 0 Einzelstück, 1 Hain mit Stufen nach Entfernung, 2 alle Stufen nebeneinander. Ohne Fenster: `TreeDemo laubbaum-400 --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo laubbaum-400 [bäume] [fläche m] [tag]`: Bestand mit JOGL. Kamera, Abstand und Fläche (14 Höhen) richten sich nach der Höhe. Ohne Fenster: `TreeGlDemo laubbaum-400 --bild datei.png [bäume] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.TreeCheck laubbaum-400`: Selbsttest ohne Fenster, endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh` (alle Arten).
