# com.dan.moss – Moos als kachelbare Textur

**Stand: LituyaBay1958 W6 Runde 7.** Moospolster aus einzelnen Trieben mit Blättchen, von oben gesehen. Das Ergebnis ist eine nahtlose Kachel mit Albedo, Höhe, Verdeckung und Normale, gedacht für Rinde, Fels, Boden und Holz. Die Farbart (R : G : B zur Leuchtdichte) ist an Fotos gemessen. Form, Größen und die Höhe der Albedo sind gestaltet [G]. Das Package kennt keine Szene. Zum Herauskopieren reicht `src/com/dan/moss/`. Der Kern braucht nur Java 21, `gl/` braucht JOGL (OpenGL 4.2).

## Rechnung

| Schritt | Wie |
|---|---|
| Polster | Kuppeln mit Halbmesser `clumpMin`–`clumpMax` und Höhe `clumpHeight`, sie decken `clumpCover` der Fläche; dazu große Buckel aus kachelbarem Wertrauschen (`cushion`) |
| Triebe | `shootsPerM2` · Kachel², zwei Drittel in den Polstern (nach außen geneigt), ein Drittel als Unterschicht überall. Jeder Trieb ist ein gebogener Stängel, Länge `shootMin`–`shootMax`, die Spitze ragt auf |
| Blättchen | `leavesPerShoot` Ellipsen links und rechts im Wechsel, zur Spitze kleiner. Der Rand ist heller (dünn, durchscheinend), die Mitte dunkler |
| Farbe | entlang des Triebs von `base` über `mid` zu `tip`, je Trieb ±`jitter` in Helligkeit und Richtung Gelb/Blau |
| Zeichnen | Tiefenpuffer in doppelter Auflösung (höhere Triebe decken tiefere), alles über den Rand hinweg (nahtlos), dann 2 × 2 gemittelt |
| Verdeckung | Höhle gegen die Umgebung in zwei Halbmessern, dazu die Tiefe im Polster; mindestens 0,12 |
| Ausgabe | `albedo` (linear RGB), `height` 0..1 über `heightRange` m, `ao`, `normal` (x, y), `meanAlbedo`, `meanSeen` (Albedo · Verdeckung, die Farbe aus der Ferne), `coverage` |

## Moose

| `MossLook` | nach | Kachel | Triebe | Farbart dunkel / mittel / hell (R G B je Leuchtdichte, gemessen) |
|---|---|---|---|---|
| `feather()` | gefiedertes Astmoos an Stämmen (etwa *Hylocomium*, *Isothecium*); Fotos Moos1–Moos3 | 10 cm | 4–12 mm, 20 Blättchen | 0,50 1,23 0,20 · 0,60 1,20 0,18 · 0,78 1,15 0,16 |
| `cushion()` | Polstermoos auf Fels und Rinde (etwa *Hypnum*, *Grimmia*); Fotos Moos2–Moos4 | 6 cm | 1,8–4,5 mm, 8 Blättchen | 0,55 1,21 0,27 · 0,66 1,18 0,24 · 0,85 1,13 0,20 |

Die Höhe der Albedo ist gestaltet [G]: Grund 0,03, Mitte 0,085–0,09, Spitze 0,155–0,19 (Runde 7b: 1,4-fach, an Sitka-Stämmen war das Moos sonst dunkler als die Rinde). Fotos tragen keine absolute Helligkeit.

## Klassen

| Klasse | Aufgabe |
|---|---|
| `MossLook` | Aussehen: Farben, Größen, Dichte, Polster, Kachel |
| `MossTexture` | `bake(look, n, seed)`: die Kachel n × n; `seenLuma()` |
| `gl/MossGl` | `upload(gl, texturen…)`: GL_TEXTURE_2D_ARRAY RGBA16F, rgb = Albedo · Verdeckung, a = Höhe; Mip-Stufen, wiederholt, anisotrop |
| `demo/MossVorlage` | misst Fotos: nur scharfe, grüne Bildpunkte (Laplace-Energie über dem 55. Perzentil, G > 1,05 R und G > 1,3 B), linear; Perzentile der Leuchtdichte und Farbart je Drittel. WebP kann ImageIO nicht lesen: vorher als PNG speichern |
| `demo/MossCheck` | Prüfung ohne Grafikkarte |

## Einbau

```java
MossTexture fe = MossTexture.bake(MossLook.feather(), 512, 1958);   // rund 1,5 s, im Hintergrund
int tex = MossGl.upload(gl, fe, MossTexture.bake(MossLook.cushion(), 512, 1959));
// com.dan.forest / com.dan.tree:
licht.mossTexture = tex;  licht.mossLayer = 0;  licht.mossTile = fe.tile;
licht.mossMean = new double[]{fe.meanSeen[0], fe.meanSeen[1], fe.meanSeen[2]};
```

In `forest.frag` und `tree.frag` (Block RINDE) wird die Textur dreifach projiziert im Ort des Baums gelesen. Die Gewichte kommen aus der Fläche, die Ableitungen sind explizit (`textureGrad`). Gelesen wird nur nah, bis eine Kachel auf rund 6 Bildpunkte fällt; weiter weg gilt `mossMean`. Der Rand des Polsters folgt der Höhe der Triebe: hohe ragen hinaus.

## Prüfung

`MossCheck` prüft beide Moose:
- gleicher Samen ergibt dieselbe Textur;
- nahtlos (der Sprung an der Kante ist wie im Inneren);
- Deckung über 95 %;
- die Farbart je Drittel liegt in der Spanne der vier Fotos (±10 %);
- Kontrast P75/P25 ohne Licht zwischen 1,6 und 8 (die Fotos mit Licht: 4,4–7,7);
- gesehene Albedo 0,035–0,15;
- Höhe der Polster passend zu den Trieben;
- Normalen gültig.

## Grenzen

- Von oben gesehen: An einer Kante, die man streifend sieht, steht das Moos nicht als Körper ab, nur die Normale und der Rand folgen der Höhe.
- Kein Wasser in den Polstern (nass sieht Moos dunkler und satter aus) und keine Sporenkapseln.
- Die Fotos (templates/) sind Vorlagen für die Messung, sie stecken nicht in der Textur.
