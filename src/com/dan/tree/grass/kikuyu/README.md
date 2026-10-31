# com.dan.tree.grass.kikuyu – Kikuyugras

Kikuyugras (*Cenchrus clandestinus*) aus Megascans „Kikuyu Grass“: acht dichte Büschel des kriechenden, immergrünen Rasengrases, 0,2 bis 0,5 m, je drei Stufen aus dem Asset.

| Variante | Höhe | Dreiecke Stufe 0 / 1 / 2 / 3 |
|---|---|---|
| A | 0,52 m | 6 092 / 2 007 / 1 252 / 368 |
| B | 0,47 m | 4 253 / 1 425 / 905 / 234 |
| C | 0,42 m | 15 877 / 5 291 / 3 026 / 723 |
| D | 0,33 m | 7 999 / 2 676 / 1 524 / 376 |
| E | 0,33 m | 8 447 / 2 805 / 1 674 / 498 |
| F | 0,33 m | 13 365 / 4 552 / 2 491 / 560 |
| G | 0,32 m | 13 909 / 4 712 / 2 441 / 585 |
| H | 0,23 m | 7 186 / 2 630 / 1 479 / 408 |

Wiese: 8 Büschel je m². Die Büschel sind breit und haben viele Dreiecke, deshalb wird in der Ferne stärker ausgedünnt (`lodKeep` 20 / 8 / 4 %). Im Winter wird das Gras nur etwas fahler (`seasonFromTexture(…, 0,6)`).

## Demo und Selbsttest

- `com.dan.tree.demo.TreeDemo kikuyu [ansicht variante stufe tag]`: Vorschau auf dem Prozessor, auf dem Rasenboden. Ansicht 0 Büschel, 1 Wiese (Kreis von 30 Höhen, Stufe nach Entfernung, in der Ferne ausgedünnt), 2 alle Stufen. Ohne Fenster: `TreeDemo kikuyu --bild datei.png [einzel|wiese|stufen] [variante] [stufe|auto] [tag] [wind m/s] [breite] [höhe] [zeit s]`.
- `com.dan.tree.demo.TreeGlDemo kikuyu [dichte je m²] [fläche m] [tag]`: Wiese mit JOGL. Ohne Fenster: `TreeGlDemo kikuyu --bild datei.png [dichte] [fläche m] [tag] [wind m/s] [breite] [höhe]`.
- `com.dan.tree.demo.GrassCheck kikuyu`: prüft die Wiese (gleicher Startwert, Dichte, Ausschnitt, Maske) und die Art (Format, Stufen, Wind, Jahreszeit). Endet mit „Alles in Ordnung.“

Neu umwandeln: `tools/tree/convert_all.sh`.
