# com.dan.render.bau – die Häuser

**Stand: P12a (19.11.2026), Version 0.1** (F124–F128 A; Daniel: „grundsätzlich rote Backsteinbauten mit Reetdach“).
Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, Abschnitt 12.13). Häuser als Körper aus
`core.szene.Bauten` (Ablage `bau/gebaeude.bin` des Gebiets, Schritt `gebaeude` der Aufbereitung) im selben Licht wie
Gelände und Wasser, dazu der Schatten der Häuser für alle, die ihn empfangen.

## Schicht

```
com.dan.render.bau        core + core.gpu + LWJGL      Hausbild (Bildteil), Shader
com.dan.render.bau.demo   dazu FStyle                  BauCheck
```

Liest `Himmel/Werte`, `Himmel/Durchlaessigkeit`, `Himmel/Ansicht` und die Zahlen `Himmel/Sonne`, `Himmel/Mond`
(render.air legt sie beim Vorbereiten ab). Legt beim Zeichnen `Bau/Schatten` (Tiefe, drei Karten nebeneinander) und
`Bau/Schattenwerte` ab; Gelände und Wasser lesen sie über `core/gpu/bauschatten.glsl`. Darum steht der Teil im Rahmen
**vor** dem Gelände: `List.of(haeuser, gelaende, wasser, himmel, kimmung)` (so baut es `lauf.Buehne`).

## Shader

| Teil | Aufgabe |
|---|---|
| `bau.glsl` | Bindungen, Kopf, Lage relativ zum Auge (Auge in zwei floats, Erdkrümmung wie das Gelände) |
| `licht.comp` | Himmel und Boden aus `Himmel/Ansicht` als 9 Glieder nach Kugelflächenfunktionen (64 × 32 Richtungen je Hälfte) |
| `kugel.glsl` | Kugelflächenfunktionen bis Grad 2, Bestrahlung nach Ramamoorthi und Hanrahan (2001) |
| `schatten.vert/.frag` | Häuser in die Karten des Schattens (Parallelprojektion entlang der Quelle) |
| `haus.vert/.frag` | Licht wie `gelaende.frag` (Lambert, Sonne und Mond mit der Durchlässigkeit der Luft, Himmel und Boden je Normale), Schatten der Häuser; Material |

## Festlegungen [G]

- **Material:** Backstein im Läuferverband (NF 240 × 71 mm, Fuge 10 mm), Mittel mit Fugen = Albedo der Wand
  (0,15 / 0,045 / 0,035 linear, ±8 % je Haus; bis P12a Lauf A 0,22 / 0,065 / 0,045); Reet mit Halmen die Falllinie hinab, Moos auf der Nordseite (18 %),
  First 0,35 m dunkel; Ziegel in Reihen von 0,33 m; flache Dächer Pappe. Alle Muster blenden zum Mittel über, sobald
  ein Bildpunkt sie nicht mehr auflöst.
- **Fenster:** 0,95 × 1,20 m, Brüstung 0,85 m, bis 2,4 m Abstand, weiße Rahmen 7 cm, Sprossen 3,5 cm (sechs Scheiben);
  Giebelfenster, wenn der Giebel höher als 3,2 m ist; eine Tür (dunkelgrün) in der Mitte langer Wände nach Süden oder
  Osten. Glas spiegelt den Himmel (Fresnel, n = 1,52).
- **Fensterlicht:** ab Sonne −1°, voll ab −6°; Anteil nach Ortszeit (15–22 Uhr 65 %, bis 23:30 auf 12 %, nachts 6 %,
  morgens 35 %); 40 cd/m², warm.
- **Schatten:** drei Karten zu 2048², halbe Seiten 120 / 500 / 2 500 m (Texel 0,12 / 0,49 / 2,4 m), Mitte vor dem Auge in
  Blickrichtung, am Raster der Texel festgehalten; 4 × 4 Proben bilinear; Abstand der Tiefe 1 Texel plus 2 · tan θ;
  Versatz längs der Normale 0,25 Texel. Nachts wirft der Mond die Schatten, wenn er über 2° steht.

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.bau.demo.BauCheck
```

An einem erzeugten Friesenhaus auf einer Platte, Himmel als Ersatz mit bekannten Werten: 1 Ablage, 2 Himmelslicht
(Glieder gegen Java), 3 Licht auf der Wand (Prüfstein 4: Süd- und Nordwand gegen a/π · E in Java, 1 ± 0,03),
4 Schatten (Prüfstein 5: Kante des Schattens des Firsts gegen die Geometrie, ± 1 Texel), 5 Fensterlicht, Schalter,
7 Aufräumen. Attest `docs/status/P12a_Bau.md`, Bilder `docs/status/P12a_Haus_*.png`. In der Szene:
`com.dan.lauf.demo.HausCheck` (Prüfsteine 6, 9, 10).

## Grenzen

- Das Gelände wirft keinen Schatten auf die Häuser (die Warft auf ihr Haus bei tiefer Sonne); die Häuser verdecken
  kein Himmelslicht (keine Verdeckung in Ecken).
- Keine Dachüberstände, Gauben und Zwerchgiebel, wenn LoD2 sie nicht hat; keine Schornsteine.
- Die Referenz kennt die Häuser noch nicht (P12c).

## Seit P12b: Körper der Gestalt (Hausbild 0.2)

`Hausbild.aus` lädt neben `bau/gebaeude.bin` die Körper aus `bau/koerper.bin` (Schritt `gestalt`: Lahnungen als zwei
Pfahlreihen mit Buschwerk, Steinbuhnen, Anleger aus Beton, Stege aus Holz) und hängt sie hinter die Häuser
(`Bauten.zusammen`). Gezeichnet werden Bereiche: Häuser `[0, zahlHaeuser)` nach `an`, Körper dahinter nach
`koerperAn` (Vorgabe an; `-Dlauf.gestalt=aus`), im Schatten wie im Bild. `haus.frag` gibt den Körpern ihr Material
[G]: Holz (0,10/0,085/0,065, längs gemasert), Stein (Schüttung nach Voronoi, 0,17/0,16/0,15), Beton (0,30/0,29/0,27),
Buschwerk (0,065/0,055/0,04); unter 1,4 m NHN nass und dunkler, unter 0,9 m mit Algen.

Kosten (Lauf B3): Die Körper kosteten rund 1,3 ms je Bild, fast alles für die 14 308 Pfähle (143 000 Dreiecke). Darum
liegen die Pfähle als Gruppen „Pfähle …“ (je Lahnung eine) am Ende von `koerper.bin`. `Hausbild` zeichnet eine Gruppe
nur, wenn ihre Hülle näher als `pfahlWeite` (400 m [G]) am Auge liegt, und Schatten werfen die Pfähle nur in der
nächsten Kaskade. Buschwerk, Buhnen und Anleger werfen Schatten in den Kaskaden 0 und 1 (`KOERPER_KASKADEN`). In der
Ferne bleibt so das Buschwerk jeder Lahnung als dunkle Linie.
