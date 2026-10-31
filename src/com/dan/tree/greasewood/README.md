# com.dan.tree.greasewood – Greasewood

Greasewood (*Sarcobatus vermiculatus*), ein dorniger Strauch der Salzsteppen im Westen Nordamerikas. Er stammt aus dem Procedural Vegetation Editor von Unreal (`Assets/Trees/shrub_greasewood_01_usd.zip`) und wurde mit `tools/tree/usd2dtree.py` umgewandelt. Das Paket ist eine Voreinstellung für den Kern `com.dan.tree`.

| Variante | Datei | Höhe | Gelenke | Zweige | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|---|---|---|
| A groß | `greasewood_a.dtree` (2,0 MB) | 1,7 m | 9 905 | 3 941 | 1,9 Mio. / 188 000 / 43 000 / 86 |
| B | `greasewood_b.dtree` (1,4 MB) | 1,3 m | 4 652 | 1 534 | 765 000 / 75 000 / 16 000 / 80 |
| C | `greasewood_c.dtree` (0,8 MB) | 1,1 m | 2 559 | 901 | 438 000 / 40 000 / 8 700 / 80 |
| D klein | `greasewood_d.dtree` (0,5 MB) | 0,6 m | 784 | 235 | 149 000 / 11 000 / 2 500 / 80 |

Rund 40 % der Gelenke im USD sind Platzhalter im Ursprung, je einer pro Zweig. Der Konverter legt sie auf ihr Elternteil und bindet die betroffenen Zweige an das nächste echte Gelenk.

Der Strauch ist sommergrün. Er treibt ab Tag 100 aus, die fleischigen Blätter sind hellgrün und werden ab Tag 275 gelblich, ab Tag 315 steht er kahl. Die Rinde ist hellgrau. Stamm 0,3, Äste 1,2, Zittern 0,5: Die Stämmchen sind steif, die Ruten biegsam.

Einen durchgehenden Stamm hat der Strauch nicht. Der Selbsttest prüft den Stamm deshalb erst bei Höhen über 5 m und erlaubt am Fuß 2 % der Höhe Bewegung, weil Ruten bis zum Boden reichen.

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo greasewood [ansicht variante stufe tag]`: Vorschau auf dem Prozessor. Ansicht 0 Einzelstück, 1 Hain mit Stufen nach Entfernung, 2 alle Stufen nebeneinander. Ohne Fenster: `TreeDemo greasewood --bild datei.png [einzel|hain|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo greasewood [sträucher] [fläche m] [tag]`: Bestand mit JOGL. Kamera, Abstand und Fläche (14 Höhen) richten sich nach der Höhe. Ohne Fenster: `TreeGlDemo greasewood --bild datei.png [sträucher] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.TreeCheck greasewood`: Selbsttest ohne Fenster, endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh` (alle Arten).
