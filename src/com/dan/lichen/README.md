# com.dan.lichen – Bartflechten

**Stand: LituyaBay1958 W6 Runde 7.** Bartflechten als Fäden und als Bild für Karten: gefiederte *Usnea*, haarige *Alectoria* und strauchige Büschel. Die Farbart ist an Fotos gemessen. Wuchs, Längen und die Höhe der Albedo sind gestaltet [G] nach den Fotos und nach Brodo, Sharnoff & Sharnoff, *Lichens of North America*. Das Package kennt keine Szene. Zum Herauskopieren reicht `src/com/dan/lichen/`. Der Kern braucht nur Java 21, `gl/` braucht JOGL (OpenGL 4.2).

## Wuchs (`BeardLichen.grow(look, seed)`)

| Schritt | Wie |
|---|---|
| Achsen | `axesMin`–`axesMax` vom Ansatz aus; Länge `lengthMin`–`lengthMax`, öfter kurz; Anfangsrichtung nach der Schwere (`gravity` 1 hängt, 0 steht auf) |
| Lauf | in Schritten ≤ 4 mm: die Schwere zieht nach unten, `tangle` dreht zufällig (verworren) |
| Verzweigung | je m `branchPerM`, Zweige verzweigen halb so oft; Winkel `branchAngle`, Länge 45–95 % des Rests |
| Fasern | alle `fibrilSpacing` (±40 %) eine Faser, `fibrilMin`–`fibrilMax` lang, unter `fibrilAngle`, um die Achse gedreht, leicht gekrümmt (Fischgräte bei *Usnea*) |
| Farbe | vom Ansatz (`base`) über `mid` zur Spitze (`tip`), je Faden ±`jitter` |
| Ausgabe | `strands` (Punkte, Dicke, Farbe, Stufe 0 Achse, 1 Zweig, 2 Faser), `box`, `lengths()` |

## Formen

| `LichenLook` | nach | Achsen, Länge | Farbart R G B je Leuchtdichte (gemessen, mittleres Drittel) |
|---|---|---|---|
| `usnea()` | Bartflechten2: gelbgrün, gefiedert, hängend | 8–14, 6–28 cm | 1,04 1,05 0,42 |
| `alectoria()` | Bartflechten1: blass graugrün, haarig, verworren | 22–38, 8–40 cm | 1,08 1,01 0,67 |
| `longissima()` | wie Bartflechten2, sehr lang (*Usnea longissima*, an der Küste Alaskas über 1 m) | 3–6, 35–120 cm | 1,03 1,05 0,47 [G] |
| `shrub()` | Bartflechten3: graugrün, aufrecht, buschig | 6–11, 3–7 cm | 0,87 1,05 0,87 |

Albedo [G]: *Usnea* 0,20–0,36, *Alectoria* 0,16–0,29, Strauch 0,17–0,30.

## Karte (`LichenCard.bake(büschel, w, h)`)

- **Projektion:** von vorn, senkrecht, dreifach überabgetastet.
- **Deckung:** Fäden dünner als ein Bildpunkt decken nach ihrer echten Dicke, und die Deckung sammelt sich. Vorn liegende Fäden geben die Farbe.
- **Verdeckung im Inneren:** −28 % [G].
- **Rand:** leere Bildpunkte bekommen die Farbe ihrer Nachbarn, damit es in den Mip-Stufen keinen Saum gibt.
- **Maße:** `widthM`, `heightM`, `attachV` (Ansatz, Anteil der Höhe von oben), `meanColor`, `meanAlpha`.
- **`gl/LichenGl.upload(gl, karten…)`:** GL_TEXTURE_2D_ARRAY SRGB8_ALPHA8. Die Mip-Stufen werden auf dem Prozessor gerechnet: Farbe mit der Deckung gewichtet, Deckung gemittelt. So bleibt die Deckung über die Entfernung gleich, wenn der Leser Alpha würfelt (Kantenglättung über die Zeit).

## Einbau mit `com.dan.forest`

```java
LichenCard[] k = new LichenCard[6];
for (int i = 0; i < 6; i++) k[i] = LichenCard.bake(BeardLichen.grow(i % 2 == 0 ? LichenLook.usnea() : LichenLook.alectoria(), 7000 + i), 256, 512);
wald.beardLichen = true;
wald.beardCards = new TreeMesh.Cards(breiten, höhen, ansätze);   // aus k[i].widthM, heightM, attachV
wald.beardTexture = LichenGl.upload(gl, k);
```

## Prüfung

`LichenCheck` prüft je Form (vier Formen; `longissima` gegen die Farbart von Bartflechten2):
- gleicher Samen ergibt dasselbe Büschel;
- es hängt oder steht auf;
- Fasern je m Achse passen zu Abstand und Länge;
- verzweigt;
- Karte: Ansatz oben, oberste Zeile leer, Deckung 1–40 %;
- die Farbart der Karte wie das Foto (±0,12);
- die Mip-Stufe erhält die Deckung.

`LichenVorlage` misst Fotos wie `com.dan.moss.demo.MossVorlage`, ohne Grünmaske; WebP vorher als PNG speichern.

## Grenzen

- Karten sind flach: Aus der Nähe und von unten sieht man zwei gekreuzte Bilder. Die Fäden selbst (`strands`) eignen sich für eine echte Geometrie, sind aber pro Büschel einige Tausend Punkte.
- Keine Krustenflechten (die zeichnet `com.dan.forest` im Rindenmuster) und keine Blattflechten (*Lobaria*).
- Nässe: nass werden Bartflechten grüner und durchscheinender; das macht der Leser nicht.
