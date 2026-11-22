# com.dan.render.brandung – die Brandung an den Böschungen

**Stand: P13c (20.11.2026), Version 0.1.** Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, 12.14; F160 A, F162 A).
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
