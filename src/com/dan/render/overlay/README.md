# com.dan.render.overlay – Einblendungen über dem Bild

**Stand: W2 (20.11.2026), Version 0.1.** Bildmodul der Schicht `render` (Werkbuch HalligHooge 113, F186 A). Es legt
Kacheln über das fertige Bild: Uhr, Kompass, Maßstab, Minikarte, Ortsnamen, Legende – was immer eine Anwendung in
Java2D malt. Es ist eine `core.gpu.Auflage` wie der Messblick: render.frame ruft sie nach der Filmkurve, die Farben
bleiben, wie hell die Szene auch belichtet ist.

## Schicht

```
com.dan.render.overlay        core + core.gpu + LWJGL (dazu java.awt für das Malen)   Kachel, Einblendung
com.dan.render.overlay.demo   dazu FStyle                                            OverlayCheck
```

Das Modul kennt weder Bühne noch Szene. In `lauf` gibt `Buehne.einblendung()` die Einblendung der Bühne (mit der
Krümmung des Geländes); die Bühne legt sie im Fenster und in `bildzeiten` auf, nie im Foto und nie im Standbild (außer
`standbild(blick, t, true)` für Prüfungen).

## Klassen und Shader

| Datei | Aufgabe |
|---|---|
| `Kachel` | ein Bild fester Größe; `male(g -> …)` malt in Java2D (Kantenglättung an) und rechnet in linear vormultipliziertes float16 um; Lage am Rand (`imBild(Ecke, dx, dy)`) oder in der Welt (`inWelt(ost, nord, h, bx, by, verdecken)`); `sichtbar`, `deckung`, `drehung`, `nachNorden` (dreht mit dem Blick), `weite`, `ausweichen`, `rang` |
| `Einblendung` | die Auflage: je Kachel ein Bild (RGBA16F) und drei Zwischenpuffer im Wechsel; hochgeladen wird im Ablauf des Bilds und nur, was neu gemalt ist; Lage in der Welt mit `Blick.bildpunkt` und der Senke d²/2R; Kacheln mit `ausweichen` überdecken einander und die Kacheln am Rand nicht (Rang, dann Reihenfolge); Kacheln am Rand liegen über denen der Welt |
| `kachel.vert` | zwei Dreiecke je Kachel, gedreht um die Mitte; mit Anker in der Welt fünf Zugriffe auf die Tiefe der Szene (Mitte und ± 3 px), Deckung nach dem Anteil, an dem die Szene nicht näher ist als der Anker (Toleranz 5 m oder 2 % [G]) |
| `kachel.frag` | ein linearer Zugriff auf die Kachel, mal Deckung; gemischt vormultipliziert |
| `voll.vert`, `blende.frag` | seit W3: das gemerkte Bild über das ganze Ziel, mal `blende` (vormultipliziert) |
| `demo.OverlayCheck` | ohne Gebiet: Lage am Rand auf den Bildpunkt, Farbe, Lage in der Welt gegen `Blick.bildpunkt`, Verdecken vor und hinter einer Wand, Deckung ½, Drehung, Hochladen nur bei Änderung, Kosten mit 40 Kacheln |

Stoppuhr: Abschnitte „Einblendung, hochladen“ und „Einblendung“, seit W3 „Einblendung, merken“ und „Einblendung, Blende“.

## Seit W3: Blende und sichtbare Fläche

- **Blende (Überblendung bei Schnitten):** `merken()` kopiert mit dem nächsten Bild das Ziel (vor den Kacheln) in ein
  eigenes Bild; `gemerkt()` zählt, wie oft. `blende` (0 … 1, volatile) legt dieses Bild danach über jedes neue Bild,
  vor den Kacheln; 0 kostet nichts. `merkenMoeglich()` ist falsch, wenn das Ziel kein `TRANSFER_SRC` kann (die Swapchain
  einer Karte ohne diese Nutzung): dann bleibt der Schnitt hart. Benutzer: `com.dan.steuerhaus` (Regisseur, Kino).
- **Sichtbare Fläche:** `Kachel.zeigeBreite`, `zeigeHoehe` (0: das ganze Ziel) – die Lage am Rand bezieht sich auf
  diese Fläche; das Steuerhaus setzt die Kacheln am Rand stattdessen um die Balken des Kinos nach innen.

## Warum so

- **Java2D statt Schrift auf der Karte:** Schrift und Zeichnung wie in der Oberfläche (FStyle, Thema), wenig Code auf der
  Karte; Kacheln ändern sich selten (die Uhr einmal je Szenenminute, der Kompass dreht nur).
- **Linear vormultipliziert:** die Karte filtert die Kachel linear (gedrehter Kompass) ohne dunkle Säume, und das Mischen
  ins sRGB-Ziel ist linear wie bei jeder Auflage. Bei ganzzahliger Lage ohne Drehung trifft jeder Bildpunkt genau einen
  Bildpunkt der Kachel.
- **Hochladen im Ablauf:** kein eigenes Abschicken mit Warten je Kachel; die Sperren rechnet der Ablauf.

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.overlay.demo.OverlayCheck   # docs/status/W2_Overlay.md
```

## Grenzen

- Größe einer Kachel fest; wer die Größe ändern will, legt eine neue an und nimmt die alte weg.
- Das Verdecken prüft fünf Stellen um den Anker, nicht die ganze Fläche: eine Kachel blendet als Ganzes aus.
- Ausweichen ist gierig (Rang, Reihenfolge), keine Suche nach freien Plätzen.
