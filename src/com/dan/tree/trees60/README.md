# com.dan.tree.trees60 – Trees60

Zehn Laubbäume aus `Assets/Trees/trees_60_fbx.zip`: FBX mit Stamm und Laubkarten, Rinden- und Laubtextur, ohne Skelett. Umgewandelt mit `tools/tree/fbx2dtree.py`. Die Art gibt das Pack nicht an. Die Laubtextur ist schon herbstlich rot-orange.

| Variante | Höhe | Karten | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|---|
| 1 | 13,9 m | 200 | 6 610 / 2 930 / 980 / 271 |
| 2 | 17,8 m | 200 | 6 630 / 2 364 / 635 / 241 |
| 3 | 20,4 m | 240 | 6 730 / 2 241 / 578 / 216 |
| 4 | 18,0 m | 200 | 6 634 / 2 452 / 716 / 275 |
| 5 | 21,7 m | 472 | 7 443 / 2 495 / 862 / 296 |
| 6 | 23,5 m | 472 | 7 337 / 2 824 / 904 / 241 |
| 7 | 20,5 m | 400 | 7 116 / 2 951 / 1 035 / 301 |
| 8 | 25,2 m | 472 | 7 890 / 2 391 / 789 / 265 |
| 9 | 19,1 m | 480 | 7 830 / 2 924 / 1 006 / 269 |
| 10 | 27,3 m | 472 | 7 836 / 2 237 / 643 / 193 |

Dateien: `trees60_1.dtree` … `trees60_10.dtree` (je etwa 0,1 MB). Alle teilen sich `trees60_bark.jpg` und `trees60_leaf.png`.

Jahreszeit (`TreeSpecies.seasonFromLeafTexture`): Das Sommergrün ist die mittlere Farbe der Laubtextur, im Sommer bleibt das Laub also orange wie in der Vorlage. Der Austrieb ist heller, der Herbst wärmer und dunkler. Laubfall ist ab Tag 300, kahl ab Tag 315. Wer grünes Laub will, setzt `season.summer` auf ein Grün. Die Textur wird dann mit Grün / Orange getönt.

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo laubbaum [ansicht variante stufe tag]`: Vorschau auf dem Prozessor. Ansicht 0 Einzelstück, 1 Hain mit Stufen nach Entfernung, 2 alle Stufen nebeneinander. Ohne Fenster: `Trees60Demo --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo laubbaum [bäume] [fläche m] [tag]`: Bestand mit JOGL. Kamera, Abstand und Fläche (14 Höhen) richten sich nach der Höhe. Ohne Fenster: `TreeGlDemo laubbaum --bild datei.png [bäume] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.TreeCheck laubbaum`: Selbsttest ohne Fenster, endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh` (alle Arten).
