# com.dan.render.terrain – das Gelände

**Stand: P6b (14.11.2026), Version 0.1, nachgezogen mit P8c (16.11.2026): seit F61 am Gelände selbst nichts geändert; das direkte Licht der Sonne hat seit P7k Stärke und Farbe aus `core.szene.Lichtquellen.SONNE_FARBE`, über die Werte des Himmels (`render.air`); seit P7a (F52 A) stehen Albedo, Klassen und Struktur in `core.szene.Boden`, `Material` legt nur die vier Quellen der Clipmaps darüber.** Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, Abschnitte 8.1 und 12.7). Clipmap aus dem Bestand `clipmap` über dem Kachelstapel `core.gitter.Gelaende`, den auch die Physik liest (Leitidee 2), mit Material nach F41 (Albedo je Rauheitsklasse aus dem Luftbild, Struktur aus dem Luftbild, Nässe aus `water.wetAge`), Licht von Sonne, Mond und Himmel und Schatten der Sonne aus den Stufen.

## Schicht

```
com.dan.render.terrain           core + core.gpu + LWJGL      Gelaendebild (Bildteil), Material, Shader
com.dan.render.terrain.clipmap   (nur java.*)                 Kopie von clipmap.Clipmap und clipmap.Source (Kern unverändert)
com.dan.render.terrain.demo      dazu FStyle                  TerrainCheck, scheitel.comp
```

Liest aus dem Bildziel, was `render.air` ablegt (`Himmel/Durchlaessigkeit`, `Himmel/Ansicht`, `Himmel/Werte`), über `luft.glsl` im Kern. Der Himmel muss vorbereitet sein, bevor das Gelände zeichnet, und zeichnet nach ihm (Luftperspektive): Reihenfolge im Rahmen `List.of(gelaende, …, himmel)`.

## Klassen und Shader

| Teil | Aufgabe |
|---|---|
| `Gelaendebild` | Bildteil. Fünf Clipmaps gleicher Lage (Höhe; Albedo R, G, B; Anteil Watt), ein Ladefaden für alle, Hochladen nur der geänderten Zeilenstücke über einen Zwischenpuffer (drei Plätze), Stufen und Kopf mit `vkCmdUpdateBuffer`, Auswahl der Blöcke (8 × 8 je Stufe) nach Sichtfeld und Bedeckung, `naesse(Gitter, wetAge)` |
| `Material` | Albedo je Klasse aus `luftbild/albedo.txt`, Klassen aus `rauheit/1m` und `10m`, Struktur aus `luftbild/1m`; bis 4 m Zelle das Mittel der 1-m-Pixel, gröber 4 × 4 Proben |
| `gelaende.glsl` | Ringe, Scheitel mit Übergang (wie `clipmap.glsl`), Höhe an einem Ort, Bedeckung |
| `gelaende.vert` / `.frag` | Scheitel relativ zum Auge mit Erdkrümmung; Lambert mit Sonne, Mond (Durchlässigkeit der Luft), Himmel und Boden, Nässe, Schatten; Ausgabe cd/m² mal Vorbelichtung |
| `sicht.comp` | Sicht der Sonne je Scheitel der Stufen 0–5 [G]: Strahl in wachsenden Schritten bis 3 km [G], weich über die Scheibe |
| `licht.comp` | Licht des Himmels auf die waagerechte Fläche (64 × 32 Richtungen gleicher Fläche) und vom Boden |
| `demo.TerrainCheck` | Prüfung ohne Fenster |

## Festlegungen

- **Mindesthöhe (seit P6e):** `Gelaendebild.aus(gebiet)` liest den Stapel mit der Mindesthöhe der Bauwerke, wenn das Gebiet eine hat (`Gebiet.mindesthoehe`, `Gelaende.mitMindesthoehe`): Deichpixel, die im DGM1 tiefer liegen als die Krone, stehen im Bild so hoch, wie hydro sie bäckt. Prüfstein 2 vergleicht weiter gegen `t.gelaende`, also gegen denselben Stapel.
- **Raum:** x = Ost − e0, z = n0 − Nord mit (e0, n0) der Nordwestecke der 1-m-Stufe; der feinste Gitterpunkt liegt auf den Pixelmitten. Alle Lagen der Gitterpunkte sind ganze Vielfache von Zweierpotenzen plus 0,5: in double genau, darum ist die Höhe jedes Punkts bitgleich mit `Gelaende.hoehe`.
- **Stufen:** 257² Punkte, 1 m bis 1 024 m (11 Stufen), Reichweite 131 km; außerhalb des Stapels −30 m NHN [G].
- **Naht:** wie `clipmap` (Übergang im Band am Rand); die gröbere Stufe läuft eine halbe Zelle der feineren unter deren Rand durch [G], sonst fiel an der Naht durch Rundung der Lage in float ein Bildpunkt auf 130 000.
- **Nässe:** n = Rest + (1 − Rest) · 2^(−t / 2 h) mit Rest 0,3 [G], t = `water.wetAge`; Albedo · (1 − 0,45 · n · Anteil Watt) wie `mudflat`. Ohne Nässe gilt alles als trocken.

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.terrain.demo.TerrainCheck [--gebiet D:\Daten\HalligHooge\gebiet]
```

Teile: 1 Material; 2 Gelände gleich Physik in Java (Prüfstein 2: jeder Punkt jeder Stufe bitgleich mit `Gelaende.hoehe`, Naht); 3 auf der Karte (Daten bitgleich mit den Ringen, Scheitel gegen `vertexHeight` ±1 · 10⁻⁴ m, Naht; über zehn Schritte, einen Sprung und zurück); 4 dicht (Prüfstein 3 in drei Ansichten); 5 Licht (Himmelslicht π · L, Nässe nass und nach einer Halbwertszeit, Schatten); 6 Aufräumen, Validierung. Der Himmel ist dort ein Ersatz mit bekannten Werten. Attest `docs/status/P6_Gelaende.md`.

## Grenzen

- Struktur außerhalb des Luftbilds aus `surface` (F41) fehlt noch: dort gilt die Albedo der Klasse ohne Struktur.
- Normalen und Material je Scheitel (am Auge 1 m); keine Feinform unter 1 m (`mudflat.Relief`), kein Glanz nasser Flächen.
- Die Albedo ist gegen das TCI von Sentinel-2 geeicht, dessen Maßstab [S] ist (Werkbuch 12.7, Stand P6b).
- Bei Bewegung lädt der Faden ganze Streifen; bei einem Sprung rund 9 MB je Ebene.

## Quellen

- Werkbuch HalligHooge, Teil A, Abschnitte 8.1, 10.4, 12.7; F41.
- F. Losasso, H. Hoppe, „Geometry Clipmaps“, SIGGRAPH 2004 (über `clipmap`).

## Boden aus dem Sediment (P9b)

`Gelaendebild.boden(Gitter, werte)` nimmt einen Stand von `core.szene.Bodennetz` (Rippelhöhe, Rippellänge, Richtung,
Schlickanteil je Zelle des Gitters des Sediments; Textur an Bindung 7 von `gelaende.frag`, Lage im Kopf der Stufen:
`bodLage`, `bodN`, `bod`). Im Watt (Anteil Watt der Klasse) kippen die Rippel die Normale: Kämme quer zur Richtung, in
die der formende Strom lief, Länge und Höhe der Zelle, Neigung bis π H/λ, leicht geschwungen [G]; jede der vier Zellen
um den Ort trägt ihr Muster, dazwischen wird gemischt. Rippel, die ein Bildpunkt nicht auflöst (λ unter zwei
Bildpunkten), bleiben weg. Schlick dunkelt das Watt ab (bis 30 % bei 30 % Schlickanteil [G]; `schlickDunkel`,
`schlickVoll`, `relief`). Das Gelände zeigt den jüngsten Stand des Bodens; ohne Boden ist alles wie zuvor.

## Seit P9c: Das Gelände geht mit dem Boden

`Gelaendebild.boden(gitter, werte)` nimmt aus dem sechsten Feld `bed.dz` und legt es als eigene Textur (R32F, Bindung 8) an; `gelaende.vert` hebt und senkt jeden Scheitel um die Bodenänderung am Ort (bilinear zwischen den Mitten der Zellen des Sediments, außerhalb 0). Normale und Schatten bleiben, wie die Clipmap sie gibt (eine Zelle des Sediments ist 20 m breit). `bodenHoehe` schaltet es ab; `lauf.Bild` setzt es nur, wenn die Quelle sagt, dass hydro mit dem bewegten Boden gerechnet hat (`Bild.Quelle.bodenBewegt()`), sonst passten Wasser und Gelände nicht zusammen (Archive aus P9b).


## Seit P10b: Nässe aus surf.wet, Fernglas

- **Nässe aus `surf.wet`** (F92 A): `Gelaendebild.nassAlsSaettigung = true` – das Bild der Nässe (`naesse(gitter, werte)`) trägt dann die Sättigung des obersten Zentimeters (0 … 1) aus `physics.atmos` statt `water.wetAge`; `gelaende.frag` nimmt sie unmittelbar (`nass.w` = 1), ohne Halbwertszeit und Restnässe. Wie bisher wirkt sie nur auf Watt und Sand (Anteil Watt je Scheitel). Bei Sättigung 1 ist das Bild dasselbe wie mit `wetAge` = 0.
- **Fernglas** (F95 A): `Gelaendebild.fokus(Blick)` – unter 27° Bildwinkel rückt die Mitte der Clipmap vom Auge zum Zielpunkt der Bildmitte (wo die Blickachse die gesenkte Ebene in 0 m NHN trifft, sonst an der Kimm), 11° darunter ganz, höchstens 8 km weit [G] (wie `Clipmap.focusCenter`). So ist das Gelände dort fein, wo ein enges Bild hinsieht; am Auge wird es dafür grob.

## Seit P10c: Sandfegen (Gelaendebild 0.2; F96 A)

`Gelaendebild.flugsand(Gitter, q)` nimmt `aeol.q` (je Zelle der Fluss nach Ost und nach Nord, kg/(m s)) als Textur
(Bindung 9 in `gelaende.frag`). Wo Sand fliegt, liegt ein Schleier aus Körnern über dem Boden:

- **Deckung** 1 − exp(−τ / sin β): τ = q · 3 / (2 ρs d V) ist die senkrechte optische Dicke (Masse in der Luft Φ = q / V,
  Querschnitt je Masse 3 / (2 ρs d)), β der Winkel des Blicks zur Fläche am Ort. `flugKorn` 0,17 mm [G: Median des
  Japsands], `flugDichte` 2 650 kg/m³, `flugTempo` 2 m/s [G: Die Quellen nennen die Geschwindigkeit der Körner
  unabhängig vom Wind (Martin und Kok 2017, Kok u. a. 2012), aber ohne Zahl für das Mittel].
- **Licht:** Körner in der Luft sind Kugeln im Licht, keine waagerechte Fläche. Eine Lambert-Kugel zeigt 2/3 der
  Leuchtdichte einer zur Sonne gewandten Fläche, mal der Phase (sin α + (π − α) cos α) / π; vom Himmel und vom Boden je
  die Hälfte. Bei tiefer Sonne ist der Schleier darum heller als der Boden, am hellsten mit der Sonne im Rücken.
- **Streifen** längs des Winds (0,6 m breit, rund 9 m lang, geschwungen [G]), die mit `flugZug` = 2 m/s ziehen
  (Laufzeit, nicht Szenenzeit: `flugZeit`); sie verschwinden, wo ein Bildpunkt quer zum Wind breiter ist als sie.
  `flugStreifen` = 0 nimmt sie weg (für die Prüfung).

Der Kopf der Stufen (`gelaende.glsl`) trägt dazu `flugLage`, `flugN`, `flug`, `flugFarbe` (Albedo der Klasse Sand).
Geprüft in `com.dan.lauf.demo.SandfegenKarteCheck` (Prüfstein 17 von P10c). Die Referenz kennt das Sandfegen nicht.

## Seit P12b: Gestalt des Geländes (Gelaendebild 0.3; F132–F134 A)

`Gelaendebild.aus` lädt `gebiet/gestalt/` (`core.szene.Gestalt`, Schritt `gestalt` der Aufbereitung), wenn es da ist:
Kacheln zu 500 m, 0,5 m je Bildpunkt, RG8 (Art, Wasserstand w → w/50 − 1 m NHN), als ein Atlas (8 Kacheln je Zeile)
mit einer Tafel der Kacheln (Bindungen 12 und 13 von `gelaende.frag`, Kopf 64 Byte je Bild); Bindung 14 ist die
Himmelsansicht. Im Shader, nach dem Licht und vor dem Sandfegen, bilinear über vier Texel:

- **Wasser** (Fething immer; Graben und Priel, wo das Gelände unter dem Stand liegt, F134 A): Spiegel nach Fresnel
  (n = 1,33) mit Kräuselung, Himmel aus der Ansicht, Glanz der Sonne (Beckmann, m² 0,006 [G], Schatten der Häuser),
  trüber Körper (Albedo 0,03 [G]); blendet mit dem Fuß des Bildpunkts zwischen 4 und 8 m aus (`gestaltWasserFuss`).
  Im Landunter liegt das Wasser aus hydro darüber.
- **Wege** nach Belag (Asphalt, Klinker, Schotter, Betonspur, Grasweg, Holz), **Deckwerk** (Steine) und **Gras** auf
  Fenne, Salzwiese, Deich und Warft: Muster mit Mittel 1, die mit dem Fuß zu ihrem Mittel ausblenden (keine
  flimmernden Fugen); ab `gestaltBelagFuss` (1 m) bis 2 m weich aus.

`gestaltAn` (Vorgabe an; `-Dlauf.gestalt=aus`) schaltet ab: Der Kopf meldet dann „aus“, der Zweig im Shader läuft nicht,
das Bild ist wie vor P12b. Zur Prüfung: `gestaltKraeusel` und `gestaltMuster` (0 = aus). `flugZeit` gibt auch die Zeit
der Kräuselung; NaN: die Uhr. Prüfung: `demo.GestaltBildCheck --gebiet <ordner>` (Prüfsteine 12, 13, 9 von P12b:
Spiegelung gegen Fresnel · L_Himmel in Java, Muster im Mittel ± 2 %, außerhalb der Gestalt bitgleich).

**Zonen der Salzwiese (P13b).** `Gelaendebild` legt die Zonen des Gebiets (`core.szene.Zonen.aufGestalt`) in die Gestalt: Arten 6 Pionierzone (Queller, lückig, Schlick dazwischen), 7 untere Salzwiese (Andel, kurz, graugrün), 8 obere (Rotschwingel, Strandflieder), 9 Fenne unten. Aus (`zonenAn`, Eigenschaft `lauf.vegetation`, `gInfo.w` = 0) führt der Shader 6–8 auf Salzwiese und 9 auf Fenne zurück; das Bild ist dann bitgleich mit dem ohne Zonen (Prüfstein 7).

**Schlick (F158 A).** Art 13 zeichnet das Gelände ohne Gras (wie Art 0); mit ausgeschalteter Salzwiese gilt sie als Salzwiese.

## Nässe bilinear (seit C2b, Werkbuch HalligHooge 138, A52)

`gelaende.frag` liest die Nässe (`water.wetAge` oder `surf.wet`) bilinear zwischen den vier Zellmitten um den Ort; vorher galt die Zelle am Ort, und das Watt war in Kacheln der Zellgröße dunkler (unter flachem Wasser sichtbar, Xaver). Die Referenz (`render.reference.Gelaendeszene.naesse`) rechnet dieselbe Regel.
