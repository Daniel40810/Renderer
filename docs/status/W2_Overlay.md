# W2 – render.overlay ohne Gebiet

Attest für `com.dan.render.overlay`, Version 0.1. Stand 20.11.2026 18:31, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Lage am Rand | Bildpunkte der Kachel oben rechts (16 × 8, Abstand 10, 20) | 128 rot, 0 daneben | 128, 0 | ok |
| 1 Lage am Rand | hochgeladen im ersten Bild | 1024 Byte | > 0 | ok |
| 1 Lage am Rand | hochgeladen im zweiten Bild ohne Änderung | 0 Byte | 0 | ok |
| 2 Lage in der Welt und Verdecken | vor der Wand (200 m): Lage gegen Blick.bildpunkt | 81 Bildpunkte, Abstand 0,71 px | 81, ≤ 1 px | ok |
| 2 Lage in der Welt und Verdecken | hinter der Wand (800 m, Wand 500 m): aus | 0 Bildpunkte bei (448, 162) | 0 | ok |
| 2 Lage in der Welt und Verdecken | dieselbe hinter einer fernen Wand (2000 m): an | 81 Bildpunkte | 81 | ok |
| 3 Deckung und Drehung | Weiß mit Deckung ½ über Schwarz (sRGB) | 188,0 | 188 ± 2 | ok |
| 3 Deckung und Drehung | Balken 40 × 6, gedreht um ¼ | 6 × 40 | 6 × 40 ± 1 | ok |
| 5 Kosten (Auskunft) | Einblendung je Bild (40 Kacheln, 1920 × 1080) | 0,072 ms |  | – |
| 5 Kosten (Auskunft) | Einblendung, hochladen je Bild (Mittel, Uhr jedes vierte Bild) | 0,042 ms |  | – |
| 5 Kosten (Auskunft) | gezeichnet zuletzt | 40 Kacheln |  | – |

Alles in Ordnung.
