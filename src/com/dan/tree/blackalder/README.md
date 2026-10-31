# com.dan.tree.blackalder – Schwarzerle

Schwarzerle (*Alnus glutinosa*) aus dem Procedural Vegetation Editor von Unreal (`Assets/Trees/tree_black_alder_01_usd.zip`), umgewandelt mit `tools/tree/usd2dtree.py`. Das Paket ist eine Voreinstellung für den Kern `com.dan.tree` (siehe dort).

| Variante | Datei | Höhe | Gelenke | Zweige | Blätter | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|---|---|---|---|
| A breit, mehrstämmig | `alder_a.dtree` (1,6 MB) | 16,2 m | 1 233 | 2 963 | 224 000 | 2,8 Mio. / 177 000 / 50 000 / 344 |
| B jung | `alder_b.dtree` (0,9 MB) | 11,3 m | 399 | 670 | 51 000 | 674 000 / 41 000 / 12 000 / 164 |
| C schlank | `alder_c.dtree` (0,4 MB) | 14,6 m | 227 | 324 | 25 000 | 321 000 / 20 000 / 5 700 / 272 |
| D groß | `alder_d.dtree` (1,5 MB) | 17,6 m | 742 | 1 820 | 138 000 | 1,7 Mio. / 110 000 / 31 000 / 458 |

Jahreszeit und Farben wie bei `com.dan.forest.Species.alder()`: Austrieb ab Tag 110, Verfärbung ab Tag 290 (die Erle färbt sich kaum), kahl ab Tag 322. Die Rinde ist dunkel graubraun. Biegsamkeit: Stamm 0,7, Äste 0,9, Zittern 0,9.

```java
TreeSpecies erle = BlackAlder.species();      // jeder Aufruf eine frische, änderbare Art
TreeMesh m = erle.bake(2, 1);                 // C schlank, Stufe 1
```

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo schwarzerle [ansicht variante stufe tag]`: Vorschau auf dem Prozessor. Ansicht 0 Einzelbaum, 1 Hain aus 14 Erlen mit Stufen nach Entfernung, 2 alle Stufen nebeneinander. Regler für Wind, Böen, Richtung, Tag und Schnee.
  Ohne Fenster: `TreeDemo schwarzerle --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo schwarzerle [bäume] [fläche m] [tag]`: Bestand mit JOGL, voreingestellt 150 Erlen auf 220 m × 220 m.
  Ohne Fenster: `TreeGlDemo schwarzerle --bild datei.png [bäume] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.TreeCheck schwarzerle`: prüft Format, Skelett, alle Stufen aller Varianten, Wind (Stille, Fuß fest, oben mehr, Richtung, Drehung, Sturm ohne Risse), Jahreszeit und Reproduzierbarkeit. Endet mit „Alles in Ordnung.“

## Neu umwandeln

```
unzip Assets/Trees/tree_black_alder_01_usd.zip -d /tmp/erle
for v in a b c d; do
  V=$(echo $v | tr a-z A-Z)
  python3 tools/tree/usd2dtree.py /tmp/erle/Tree_Black_Alder_01_$V.usd src/com/dan/tree/blackalder/alder_$v.dtree --name "Schwarzerle $V"
done
```
