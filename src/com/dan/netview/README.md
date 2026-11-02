# com.dan.netview – Netzblick

Zeichnet, was ein Netz oder eine Laufzeitrechnung zu zeigen hat, über eine 3D-Szene: Linien, Pfeile, Punkte, Isolinien mit Wert, die mit der Zeit erscheinen (laufende Isochronen), Strahlen, die mit der Front wachsen, Messstrecken mit Werten als Farbband und Graphen aus Knoten und Kanten. In der Ferne ausgedünnt (Douglas-Peucker, drei Stufen). Kennt keine Datenbank und keine Netzbibliothek: die App füllt die Ebenen aus Oracle NDM, aus `com.dan.route` oder aus Dateien.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/netview/`.

**Stand: LituyaBay1958 Phase 7** (Werkbuch 5.12). In der App ab Phase 7a als Netzblick (Bild im Bild).

## Was es kann

- **Ebenen (`NetLayer`):** `line`, `arrow` (Spitze am Ende), `points` (Kreise; mit Werten über die Rampe gefärbt, Farbe −1), `isoline(xyz, wert, ...)` (sichtbar, sobald die Zeit des Blicks den Wert erreicht; die vorderste leuchtet `front` Sekunden), `ray(xyz, zeiten, ...)` (wächst von der Quelle her), `band(xyz, werte, breite)` (Messstrecke als Farbband), `graph(knoten, kanten, ...)`; `isolines(feld, w, h, ox, oz, schritt, y, werte, ...)` macht aus einem Rasterfeld Isolinien (Marching Squares, `Contour`). Je Ebene `visible`, `ramp`, `lodTolerance` {0, 4, 25} m, `lodDistance` {800, 4 000} m.
- **Breiten in Bildpunkten:** jede Strecke ist ein Viereck um die Weltlage; die Breite bleibt gleich, egal wie weit weg. Tiefentest gegen die Szene mit kleinem Versatz zum Auge, Tiefe wird nicht geschrieben, Mischung an.
- **Stufen:** je Stück drei Stufen beim Einfügen (`Simplify.keep`, Douglas-Peucker, geschlossene Züge bleiben geschlossen); je Bild nach dem Abstand vom Auge zum Rand der Hülle.
- **Rampen (`Ramp`):** `TIME` (0 bis 600 s, gelb über Rot zu Violett), `HEIGHT` (0 bis 524 m, blau bis rot); `copy(min, max)`.
- **Zeichnen (`gl/NetviewGl`):** `gain` (Faktor auf die Farben für HDR-Puffer, seit Phase 7a), je Ebene ein Puffer mit allen Stufen (16 Werte je Instanz), je Bild ein `glMultiDrawArraysIndirect` mit einem Befehl je Stück in seiner Stufe; `draw(gl, viewProj, auge, w, h, zeit)`, `drawn()`, `info()`.

## Benutzung

```java
NetLayer iso = new NetLayer("Isochronen");
iso.isolines(ankunft, w, h, x0, z0, zelle, 1.5f, new double[]{60, 120, 180}, 0xFFE6A0, 2.5f);
NetLayer spur = new NetLayer("Spurlinie");
spur.band(uferXyz, hoehen, 8);
spur.ramp = Ramp.HEIGHT.copy(0, 200);

NetviewGl view = new NetviewGl();
view.add(spur); view.add(iso);
view.init(gl);
// je Bild nach der Szene:
view.draw(gl, viewProj, auge, breite, hoehe, zeitDerWelle);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `NetLayer` | Ebene mit Stücken (Art, Punkte, Werte, Farbe, Breite, Stufen) |
| `Contour` | Isolinien eines Rasterfelds (Marching Squares, zu Zügen verbunden, NaN wird ausgelassen) |
| `Simplify` | Douglas-Peucker, größte Abweichung |
| `Ramp` | Farbrampen für Werte |
| `gl/NetviewGl`, `gl/netview.vert`, `gl/netview.frag` | Zeichnen mit JOGL: Vierecke in Bildpunkten, Pfeilspitzen, runde Punkte, laufende Isolinien und Strahlen |
| `demo/NetviewDemo` | Gerechnete Bucht 12 × 5,2 km mit Insel; Laufzeit selbst gerechnet (16 Nachbarn, 40 m). Fenster mit Zeit, Abstand und Ebenen; `--bild datei.png` zeigt vier Ansichten |
| `demo/NetviewCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Prüfung

`NetviewCheck` prüft:
- **Kreisfeld:** f = Abstand zur Mitte auf 10 m: die Isolinien zu 55, 250 und 480 m sind ein geschlossener Zug und liegen höchstens step²/(8 r) neben dem Kreis (lineare Deutung), etwa 0,035 m bei 250 m.
- **NaN-Rand:** ein angeschnittener Kreis gibt offene Züge, keine Linie ins Leere.
- **Douglas-Peucker:** Enden bleiben, Abweichung ≤ Toleranz; ein Kreis von 500 m bleibt bei 4 m ein Ring (33 von 401 Punkten).
- **Stufen** wechseln bei 800 m und 4 km ab dem Rand der Hülle; falsche Kanten und falsche Zahl der Werte werden abgewiesen.
- **Rampe:** Enden geklemmt, Stützfarben getroffen.
- **Instanzen:** Strecken, Pfeil mit Spitze, Punkte, Graph (Kanten und Knoten), Band mit Werten; ein Zickzack von 2 m wird fern zu einer Strecke.

Phase 7 hat `NetviewDemo` außerdem mit Mesa (llvmpipe) gezeichnet (`docs/status/Vorschau_Phase7_Netzblick_Mesa.png` in LituyaBay1958).

## Grenzen

- Linien sind Vierecke je Strecke ohne Gehrung; an spitzen Knicken bleiben kleine Lücken oder Überlappungen (bei Breiten bis etwa 4 px nicht zu sehen).
- Keine Beschriftung im Bild; Zahlen und Namen schreibt die App (Swing über dem Bild).
- Die Stufe gilt je Stück: ein sehr langes Stück wird ganz nach seinem nächsten Punkt gezeichnet. Lange Linien deshalb in Stücke teilen (Isolinien sind es von selbst).
- Isolinien liegen auf fester Höhe y; dem Gelände folgen sie nur, wenn die App y je Punkt setzt.

## Seit welcher Phase

LituyaBay1958 Phase 7 (Werkbuch 5.12): neu. Phase 7a: `gain` für den Netzblick der App (Linien im HDR-Puffer vor der Belichtung).
