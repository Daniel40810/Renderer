# com.dan.render.bewuchs – Gehölze und Halme

**Stand: P12c2 (19.11.2026), Version 0.1** (F135–F138 A). Bildmodul der Schicht `render` (Werkbuch HalligHooge,
Teil A, Abschnitt 12.13, Datei 75). Gehölze aus `core.szene.Gehoelz` (Ablage `bau/gehoelz.bin` des Gebiets, Schritt
`gehoelz` der Aufbereitung: Kronen aus bDOM minus DGM1 und NDVI) als Bäume und Sträucher im Wind, mit Laub nach dem
Tag des Jahres; Halme als Büschel in Ringen um das Auge, die in der Ferne in das Material des Geländes übergehen.

## Schicht

```
com.dan.render.bewuchs        core + core.gpu + LWJGL      Bewuchsbild (Bildteil), Shader
com.dan.render.bewuchs.baum   (Java)                       Erzeuger der Bäume, aus dem Element forest kopiert
```

`baum` ist eine Kopie aus `forest` (Species, TreeGenerator, TreeModel, TreeMesh, Noise2, Season; nur das Paket
umbenannt), weil die Schicht ein Element nicht einbinden darf (wie `clipmap` im Gelände). Neu dort: `ash()`,
`goatWillow()`, `elder()`, `dogRose()`; Laub der Hallig-Arten dichter (`cluster`, `leafDensity`).

Liest `Himmel/*` wie `render.bau` und, wenn vorhanden, `Bau/Schatten` und `Bau/Schattenwerte`: die Gehölze schreiben
ihre Tiefe in dieselben Karten (nach den Häusern, vor dem Gelände) und empfangen den Schatten der Häuser und ihren
eigenen. Darum steht der Teil im Rahmen **nach** den Häusern und **vor** dem Gelände:
`haeuser, bewuchs, gelaende, wasser, himmel, kimmung` (so baut es `lauf.Buehne`).

## Shader

| Teil | Aufgabe |
|---|---|
| `bewuchs.glsl` | Bindungen, Kopf (Matrix, Auge in zwei floats, Wind), `welt()`: Maß, Lage, Drehung, Biegung im Wind (Mitte, Böen, Schwanken, Flattern der Blätter) |
| `licht.comp` | Himmel und Boden als 9 Glieder (wie `render.bau`) |
| `baum.vert/.frag` | Gehölze (instanziert, eine Liste je Stufe); Licht zweiseitig, Blätter durchscheinend (0,35), Schatten aus `bauschatten.glsl` |
| `halm.vert` | Halme: Gestalt im Scheitelshader aus den Büscheln (8 Halme zu 15 Punkten), Höhe nach der Art des Bodens; Fragment wie `baum.frag` |
| `schatten.vert/.frag` | Gehölze in die Karten des Schattens der Häuser |

## Festlegungen [G]

- **Arten** (Regeln in der Aufbereitung nach Höhe und Nässe): Esche, Schwarzerle, Eberesche, Salweide, Holunder,
  Hundsrose; je zwei Varianten. Maß des Netzes nach der gemessenen Höhe und den Halbmessern der Krone in vier
  Richtungen (Halbmesser des Netzes = 90 % der Laubpunkte um den Stamm).
- **Stufen:** bis 120 m nah (volles Netz), bis 450 m mittel, darüber Silhouette; Schatten bis 250 m mit dem mittleren
  Netz, darüber mit der Silhouette.
- **Laub:** `Season` nach dem Tag des Jahres (Farbe, Fall); das Netz wird neu gebaut, wenn der Tag wechselt.
- **Wind:** aus der Physik der Luft am Boden (`lauf.Buehne` gibt ihn je Bild), Böigkeit 0,3; Biegung der Mitte
  min(0,0009 U², 0,08).
- **Halme:** Ringe 6 / 14 / 30 m mit Büscheln zu 0,25 / 0,45 / 0,9 m und 8 / 7 / 6 Halmen, höchstens 16 000 Büschel,
  neu gelegt, wenn das Auge mehr als 0,5 m wandert; Höhe Fenne 0,20, Salzwiese und Deich 0,12, Warft 0,08,
  Grasweg 0,06 m (je ×0,6 … 1,4); Albedo des Bodens; Übergang in das Material zwischen 22 und 30 m.
- **Aus:** `an = false` (`-Dlauf.bewuchs=aus`) zeichnet nichts; das Bild ist bitgleich zu dem ohne den Teil.

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.BewuchsCheck
java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BewuchsLaufCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen
```

`BewuchsCheck` mit den Daten des Gebiets und einem Ersatzhimmel: Netze und Laub; Prüfstein 17 (Bild: Kronen von
oben ≥ 70 % bedeckt, Höhe Median ± 1 m); Prüfstein 16 (Halme gegen Boden ± 5 %); Prüfstein 9 (aus bitgleich).
`BewuchsLaufCheck` in der Szene: Prüfstein 18 (Kosten ≤ 3 ms, ≥ 30 Bilder/s) und die Bilder
`docs/status/P12c_Bewuchs.png`.

## Grenzen

- Die Gehölze werfen keinen Schatten auf die Halme jenseits der Karten der Häuser; keine Verdeckung des Himmelslichts
  in der Krone (innen so hell wie außen, bis auf den eigenen Schatten der Sonne).
- Halme nur um das Auge; weiter weg trägt das Material des Geländes das Gras.
- Arten nach Regeln, nicht kartiert; Stamm und Äste nach dem Erzeuger, nicht gemessen.

**Zonen der Salzwiese (P13b).** `Bewuchsbild` pflanzt Halme auch auf den Arten 6–9 (Höhe und Breite je Zone in `halm.vert`); die Jahreszeit kommt aus dem Tag (vierter Wert der Kachel): Queller rot von Tag 240 bis 335, Strandflieder lila von 185 bis 255, im Winter fahler. Ohne Zonen nimmt er die Arten aus `Zonen.ohneZone`.

**Kosten der Zonen (F157 A).** Je Büschel einer Zone weniger Halme, so dass Breite × Höhe im Mittel nicht über der Klasse Salzwiese (0,006 × 0,12 m) liegt (`flaechenFaktor` nach dem Tag im Jahr, höchstens 1). Art 13 (Schlick, F158 A) trägt keine Halme.
