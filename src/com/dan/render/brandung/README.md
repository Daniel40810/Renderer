# com.dan.render.brandung – die Brandung an den Böschungen

**Stand: P13c (20.11.2026), Version 0.1; seit S9f (28.11.2026) 0.2.** Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, 12.14; F160 A, F162 A).
Brecher, Auflauf und Gischt an den Kanten des Gebiets (`brandung/kanten.txt`, Schritt `brandung` der Aufbereitung).

## Schicht

```
com.dan.render.brandung        core + core.gpu + LWJGL      Brandungsbild (Bildteil), Shader
com.dan.render.brandung.demo   dazu FStyle                  BrandungCheck (Kern, ohne Karte)
```

Die Rechnung liegt im Kern (`core.szene.Brandung`, `Brecherform`, `Kanten`), weil Schichten keine Element-Packages kennen:
`Brecherform` ist `com.dan.surf.BreakerShape` (Sylt Phase 6) unverändert, die Tropfen rechnet `gischt.vert` wie
`com.dan.spray.Parcel.particle`. Das Wasser kennt dieser Teil nicht; `lauf.Buehne` setzt die `Quelle` (Wasserstand aus dem
Spiegelbild des Wassers, Seegang aus seinen Netzen, Wind, Zeit).

## Ablauf je Bild

| Schritt | Was |
|---|---|
| Kern (Prozessor) | Abschnitte im Umkreis 1,2 km und im Blickfeld (± halber Winkel + 15°), an denen Wasser steht und auf die die See zuläuft; je Abschnitt Welle, Typ nach Battjes, Form, Auflauf nach EurOtop, Bündel der Gischt |
| `licht.comp` | Himmel und Boden als 9 Kugelflächenfunktionen (wie render.bau) |
| `brecher.vert/.frag` | Brecher und Wasserfilm: Fresnel mal Himmel, Licht aus dem trüben Wasser, durchscheinende Lippe, Schaum nach Lambert |
| `gischt.vert/.frag` | Tropfen als Scheiben zum Auge, Streulicht nach Henyey-Greenstein, gemischt ohne Tiefe zu schreiben |

Im Rahmen nach dem Wasser: `gelaende, wasser, brandung, himmel, kimmung`. Aus (`an = false`, `-Dlauf.brandung=aus`) zeichnet er nichts.

## Grenzen

- Phasengemittelt (F147 A): Lage, Höhe, Typ und Auflauf gerechnet, Form gestaltet.
- Die Brecher an den Böschungen haben ihre eigene Wellenfolge; die langen Wellen des Wassers laufen ohne Übergang bis an den Hang.
- Kein Rückwirken auf hydro oder sediment; Überlauf über die Krone nur gezeigt.
- Die Referenz kennt Brecher und Gischt nicht.
- Seit S3c-5 (SF28 A, Seegang Werkbuch 19/20): Wo das Wasser das Netz der Clipmap zeichnet (`Wasser/Netz` in `z.werte`),
  zeichnet der Kern keine Brecher und keinen Wasserfilm (`Brandung.netzBereich`); die Gischt bleibt. Je Abschnitt ein
  starres Stück von 5,2 m mit eigener Wasserlinie und Phase gab dort Kacheln (B45). S5/S6 bringen Brecher und Auflauf
  neu aus der Fläche.
- Seit S5b-4 (Seegang Werkbuch 46, SF51 A): `Brandungsbild.fuesse` – im Netz nimmt der Kern seine Wellen aus den
  Kämmen der Fläche am Fuß (`core.szene.Fusswellen`); Gischt am Hang und Überlauf hängen an den Kämmen, die man vor der
  Kante kommen sieht. Jenseits der Clipmap bleibt die eigene Wellenfolge.
- Seit S5b-5 (Seegang Werkbuch 50): im Netz wieder Brecher, aber nur die Lippen der Sturz- und Reflexionsbrecher, mit
  Zeit und Höhe aus den Kämmen der Fläche und mit Enden, die sich Nachbarn teilen (`core.szene.Brandung.lippen`); die
  Bänder laufen durch dasselbe Netz wie bisher (Schaum, Dicke).
- Seit S9f (Seegang Werkbuch 114/115, SF197 A), Version 0.2: Solange die erste Fläche des Seegangs im Bau ist, zeichnet das
  Wasser noch kein Netz (rund 30 s nach dem Öffnen, bei Xaver rund 1000 Bilder). Dann gilt für die Brandung der künftige
  Bereich des Netzes um das Auge als Netz (`Brandungsbild.netzErwartet`, von `lauf.Buehne` gesetzt: 1984 m, wie
  `Seegangbild.netzWerte`): keine starren Brecher und kein Wasserfilm, die Gischt bleibt. Vorher gab das dort die Kacheln
  aus B45. Ein Standbild ohne geführten Wart und ein Szenario ohne Fläche erwarten kein Netz (wie bisher).
  `-Dlauf.brandung.warten=aus` schaltet das ab (Gegenprobe). Prüfung: `lauf.demo.BrandungBildCheck --teil anlauf`
  (Prüfstein 21).
