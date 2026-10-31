# com.dan.tree.grass.footpath – Fußweg im Gras

Gelände „Dusty foot path way in grass garden“ (glTF, Fotogrammetrie): 33 m × 42 m, 3 m Höhenunterschied, 120 000 Dreiecke, eine Farbtextur (8K → 2048²). Umgewandelt mit `tools/tree/gltf2dtree.py --terrain`. Form `GROUND`: statisch, bewegt sich nicht, kein Laub. Die Stufen kommen aus Rasterzellen nach dem halben Durchmesser (bis 7 900 Dreiecke in Stufe 3).

`FootPath.ground()` liefert die Höhe am Ort (Raster 0,25 m). Damit lässt sich eine `GrassField`-Wiese auf das Gelände streuen (siehe `com.dan.tree.grass`).

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo fussweg [ansicht]`: Vorschau auf dem Prozessor. Ansicht 0 schräg, 1 von oben, 2 Stufen. Ohne Fenster: `TreeDemo fussweg --bild datei.png [einzel|oben|stufen] …`.
- `com.dan.tree.demo.TreeGlDemo fussweg`: Gelände mit JOGL. Ohne Fenster: `TreeGlDemo fussweg --bild datei.png`.
- `com.dan.tree.demo.TreeCheck fussweg`: prüft Netz, Textur, Stufen, Stillstand im Sturm und die Höhe am Ort. Endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh`.
