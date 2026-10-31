# com.dan.route – Wegenetze

Ein Graph mit Knoten und Kanten (Länge, Steigung, Art, Breite, Einbahn, erlaubte Geschwindigkeit), kürzeste Wege (Dijkstra und A*), Umkreise nach Kosten, nächste Knoten über ein Raster, Ströme von Fußgängern und Fahrzeugen mit Zielen, Dichte und Stau (`Crowd`) und Linien mit Fahrplan (`Line`).

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/route/`.

**Stand: Timmendorf Phase 12** (route II, in Phase 12 Ziele mit veränderlichem Gewicht für Zuschauerströme): A*, Raster für `nearest`, Kapazität, `Crowd` (Fußgänger nach Weidmann mit Dichte im Fenster vor jedem, Fahrzeuge nach Greenshields, Ziele mit Gewicht, Verweildauer und Blickpunkt, Schwerkraftmodell, Stau an Engstellen) und `Line` (Fahrplan ohne Zustand, Anfahren und Bremsen). Hülle seit Phase 1.

## Was es kann

- **Graph:** `node(id, x, y, z)` (gleiche Nummer, derselbe Knoten), `edge(a, b, Art, Breite, Einbahn)`; Arten FOOTWAY, PROMENADE, PIER, STEPS, BEACH, ROAD, BUS, FAIRWAY.
- **Wege:** `shortest(a, b, kosten)` (Dijkstra, Einbahn nur vorwärts), `within(a, kosten, max)` (Umkreis), `nearest(x, z)`; Kosten `walking()` (Gehzeit) oder `distance()`.
- **Prüfung von Netzen:** `danglingCount()` (lose Enden), `totalLength()`.
- **Fußgänger nach Weidmann 1993:** `weidmann(ρ)` mit v₀ = 1,34 m/s, ρ_max = 5,4 P/m², γ = 1,913 m⁻²; `flow(ρ)`.
- **Paketvertrag:** offene Felder, `seed`, Qualitätsstufen `LOW` bis `ULTRA`, `strength()` für das Platzhalterbild.

- **Seit Phase 10:** `astar(a, b, kosten, jeMeter)` (gleiches Ergebnis wie Dijkstra, besucht im Prüfnetz ein Fünftel der Knoten), `Path.edges()`, `nearest(x, z, Arten)` über ein Raster zu 50 m, `driving()` (Fahrzeit mit `Edge.speed`), `capacity(e)`.
- **Ströme (`Crowd`):** `target(n)` bringt die Zahl der Agenten auf n (neue meist schon unterwegs, überzählige biegen nach 150 m oder am nächsten Ziel ab), `step(dt)`. Fußgänger: freie Geschwindigkeit je Person N(1,34; 0,26) m/s, Dichte im Fenster von 4 m vor ihnen (Gegenverkehr halb, über das Ende der Kante hinaus) nach Weidmann, Treppen halb so schnell; ein Stau löst sich von vorn. Fahrzeuge: erlaubte Geschwindigkeit, Dichte im eigenen Fahrstreifen 60 m voraus, Greenshields bis zur Staudichte 1/7 m, Rechtsverkehr 1,75 m neben der Mitte. Ziele (`Goal`): Knoten, Gewicht, Verweildauer, Blickpunkt (Zuschauer drehen sich dorthin); Wahl nach Gewicht/(1 + d/reach). `density(e)`, `moving()` (unterwegs, im Stau), `near(x, z, r)`, gleicher Startwert, gleicher Lauf.
- **Linien (`Line`):** Halte, Reisegeschwindigkeit, Halt, Takt, erste und letzte Abfahrt; `vehicles(t)` und `at(τ)` rechnen die Lage aus der Uhrzeit allein (Zeitraffer und Sprünge gehen), `nextDeparture(halt, t)`, Fahrzeit mit 1 m/s² Anfahren und Bremsen.

## Benutzung

```java
Route g = new Route();                  // die App füllt ihn aus Oracle NDM oder aus daten/netz
int a = g.node(4711, x1, y1, z1), b = g.node(4712, x2, y2, z2);
g.edge(a, b, Route.Kind.PROMENADE, 8, false);
Route.Path p = g.shortest(a, b, g.walking());
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Route` | Kern: Graph, Wege (Dijkstra, A*), Umkreis, Raster, Weidmann, Kapazität |
| `Crowd` | Ströme: Fußgänger und Fahrzeuge mit Zielen, Dichte, Stau (seit Phase 10) |
| `Line` | Linie mit Fahrplan (seit Phase 10) |
| `gl/RouteGl` | Darstellung mit JOGL, Shader `route.vert` und `route.frag` als Ressourcen im Package (Hülle: Platzhalterbild) |
| `demo/RouteDemo` | Fenster mit Regler; `--bild datei.png` schreibt ein Standbild |
| `demo/RouteCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Grenzen

- Je Netz ein Graph; Umsteigen zwischen Netzen (Fuß zum Bus) kennt das Package nicht, das macht die App.
- Die Dichte gilt je Kante und Fenster, nicht in der Fläche: Plätze sind Kanten mit ihrer Breite; Ausweichen ist ein fester seitlicher Versatz je Person.
- Fahrzeuge kennen keine Kreuzungsregeln und Ampeln, nur den Abstand nach vorn.

## Seit welcher Phase

- **Timmendorf Phase 1:** Hülle nach `muster`.
- **Timmendorf Phase 10:** A*, Raster, Geschwindigkeit je Kante, Kapazität, `Crowd`, `Line`; `RouteCheck` mit Fundamentaldiagramm (Rundweg bei 0,1 bis 2 P/m² gegen Weidmann), Stau vor einer Engstelle, Zielen, Zuschauern, Fahrzeugen und Fahrplan.
- **Timmendorf Phase 12:** veränderliche Gewichte der Ziele (`Crowd.weight(ziel, w)`: Zuschauer strömen zum Feuerwerk), `hurry` (alle an einem Ziel brechen in kurzer Zeit auf), `dwelling`; wer zu einem Ziel mit Gewicht 0 kommt, bleibt nur kurz.
