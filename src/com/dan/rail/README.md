# com.dan.rail – Schienenweg und Züge

Ein Gleis nach seiner Achse und Züge darauf. Achse, Höhen und Fahrplan kommen von außen; das Package kennt keine Strecke. Es braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`.

Neu seit Brocken Phase 1 (F10 A) als Hülle; **seit Brocken Phase 12 das Gleis:** Achse aus Flächen, Gradiente mit Grenze der Steigung, Bahnhöfe, Überhöhung, Bahnübergänge, Bahnkörper (Damm und Einschnitt) und ein Netz aus Schotter, Schienen und Schwellen. **Seit Brocken Phase 13 die Züge:** Fahrdynamik, Halte mit Abfahrtszeit und Signal, Pfiffe, Auspuffschläge, Ausweichgleise, Lage der Fahrzeuge auf dem Gleis und eine Dampflok mit Wagen als Netz. Fahrplan, Kreuzungen und Dampf macht die App (Dampf etwa mit `com.dan.smoke`).

Zum Herauskopieren reicht `src/com/dan/rail/`.

## Was es kann

- **Gleis:** `Track` aus einer Achse {x, y, z}, gleichmäßig nachgetastet (`Quality` 10 bis 1 m).
  - `at(s)` gibt Ort, Richtung (Grad von Nord), Steigung und Krümmung (über ±20 m gemittelt).
  - Dazu `length()`, `maxGradient(basis)`, `windungen()` (+ rechts herum).
- **Spurweiten:** `Track.METERSPUR` (1,000 m) und `Track.NORMALSPUR` (1,435 m).
- **Achse aus Flächen** (`Corridor`, seit Phase 12): für Strecken, von denen nur Flächen vorliegen (etwa ALKIS „Bahnverkehr“).
  - Die Flächen werden auf ein dünnes Raster gelegt (nur belegte Zellen) und nach Zhang und Suen (1984) zur Mittellinie verdünnt.
  - Gesucht wird der kürzeste Weg zwischen zwei Punkten (Dijkstra). Lücken bis `gap` m, etwa an Bahnübergängen, werden überbrückt.
  - Zum Schluss wird geglättet und nachgetastet.
- **Gradiente** (`Track.profile`): eine Höhe je Stelle zu den Geländehöhen, deren Steigung nirgends über der Grenze liegt; in Bahnhöfen gilt eine kleinere Grenze.
  - Gerechnet aus den beiden Hüllen mit dieser Grenze, unter und über dem Gelände.
  - Die Gradiente liegt dazwischen beim Anteil `fill` (0 = nur Einschnitt, 0,5 = kleinste größte Abweichung), danach geglättet.
- **Bahnhöfe** (`Track.Station`, `stations`, `stationAt`); seit Phase 14 mit der Seite des Bahnsteigs (`side`: +1 rechts, −1 links, 0 ohne Bahnsteig; der Konstruktor mit drei Angaben legt ihn rechts).
- **Überhöhung** (`cant(s)`): das Gleichgewicht (Spur + 0,067 m) · v² / (g·R) für `designSpeed`, zu 60 % ausgeführt, höchstens `maxCant` [G].
- **Umläufe** (`turnsAround(cx, cz, s0, s1)`): wie oft das Gleis um einen Punkt läuft.
- **Bahnübergänge** (`crossings(flächen)`): wo die Achse eine Fläche quert, als {s0, s1}.
- **Bahnkörper** (`Bed`): die Änderung des Geländes auf einem Raster.
  - Planum 4 m, 0,55 m unter der Schienenoberkante; Böschung 1 : 1,5 bis 3 m Höhe.
  - Was darüber hinausgeht, gleicht das Gelände flach an (1 : 6), für Höhenmodelle, die neben dem Gleis zu hoch oder zu tief liegen.
  - Gibt je Zelle die Änderung, den Abstand zur Achse, `rgba()` mit Schotter und Planum, `earthwork()`.
- **Schwellen:** `schwellen()`, `schwelle(i)` alle 0,65 m [G] mit kleinem Versatz aus `seed`.
- **Probe:** `Track.spirale(...)` legt eine Spirale an.
- **Zug** (`Train`, seit Phase 13 mit Fahrdynamik):
  - Zugkraft aus der Leistung am Rad (`leistung`), höchstens Haftreibwert mal Gewicht auf den Treibachsen (`haftung`, `reibMasse`); Zuschlag für drehende Massen.
  - Widerstände: Steigung, Rollen und Luft (`grundWiderstand` ‰, `luftWiderstand` ‰ je (km/h)²), Bogen nach Röckl für Schmalspur (400 / (R − 20) ‰) [G].
  - Sollgeschwindigkeit: `vMax`, die Bogengrenze voraus (Querbeschleunigung höchstens `querbeschleunigung`, mit Bremsweg davor) und die Bremskurve zum nächsten Halt; geregelt auf 5 s, bremst mit `bremsen` (zum Halten bis 25 % mehr).
  - Halte (`Halt`: Stelle, Aufenthalt, frühste Abfahrt in der Zeit des Zuges, Name); `Signal` sagt, ob der Zug weiterfahren darf (eingleisige Strecke), `AmHalt` meldet das Halten, `Ankunft` das Ende.
  - Richtung `richtung` (+1 oder −1), seitlicher `versatz` je Stelle (Ausweichgleis, nur für die Zeichnung).
  - Pfiffe (`Pfiff`) beim Abfahren und an `pfiffe`; `last()` 0..1, `schlaege()` Auspuffschläge je s (Treibrad, 4 je Umdrehung), `zustand()`, `fahrzeuge()` (Mitte und Länge jedes Fahrzeugs).
  - Preset `Train.brockenzug(name, wagen)`: 99.23–24 (64,5 t, 430 kW am Rad, 40 km/h) mit Wagen zu 22 t und 14 m [G]; ohne Dynamik (`dynamik = false`) wie die Hülle.
- **Fahrzeuge** (`Fahrzeug`, seit Phase 13): Lage zwischen den Drehpunkten (je 35 % der Länge von der Mitte), Neigung, Überhöhung, Versatz; Schornstein (`schlot`), Führerstand zum Hinauslehnen (`fuehrerstand`), Stampfen und Rollen (`neigung`).
- **Ausweichgleis** (`Track.parallel(name, s0, s1, versatz, weiche)`, seit Phase 13): ein Gleis neben dem Hauptgleis, an den Enden weich angeschlossen; `Track.offset` gibt den Versatz je Stelle. `Bed.build(List<Track>, …)` legt den Bahnkörper für mehrere Gleise.
- **Grafik (gl):** `RailGl` zeichnet das Gleis als Netz.
  - Schotterbett mit Böschung, Schienen (Kopf blank, Flanken rostig), Schwellen als Exemplare (Holz oder Beton); in Bögen überhöht.
  - Belag an Bahnübergängen, flache Bahnsteige an Bahnhöfen auf ihrer Seite.
  - In Stücken zu 250 m, gezeichnet bis `range` m; Schwellen bis 500 m.
  - Licht: Sonne, Himmel, Dunst; Schnee und Nässe.
- **Grafik der Züge** (`gl/TrainGl`, seit Phase 13): Lok nach der Baureihe 99.23–24 (Kessel, Rauchkammer, Schornstein, Dome, Wasserkästen, Führerhaus mit Fenstern, Kohlenkasten; Rahmen, Räder und Pufferbohlen rot; fünf gekuppelte Achsen, Laufachsen, Zylinder, Laternen) und Wagen (offene Bühnen, Drehgestelle, Fensterband, gewölbtes Dach; Farben [G]) aus Quadern und Zylindern; Lack glänzt, Glas spiegelt den Himmel, die Laternen leuchten in Fahrtrichtung. Licht wie `RailGl`.

## Benutzung

```java
Corridor c = Corridor.of(flaechen, 2);                       // Flächen der Trasse, Raster 2 m
double[][] xz = c.path(x0, z0, x1, z1, 30, 2);              // Mittellinie, über 30 m geglättet, alle 2 m
double[] g = …;                                             // Gelände je Stelle
double[] y = Track.profile(g, 2, 0.033, 120, bahnhoefe, 0.004, 0.3);
Track gleis = new Track("Brockenbahn", achse(xz, y), Track.METERSPUR, Track.Quality.HIGH);
gleis.stations.add(new Track.Station("Schierke", 2914, 120));
List<double[]> ue = gleis.crossings(strassen);
Bed bett = new Bed().build(gleis, gelaende::height, x0, z0, 2, nx, nz);   // bett.deltaAt(x, z) ins Gelände

RailGl gl = new RailGl();
gl.init(gl4);
gl.upload(gl4, gleis, ue);
gl.draw(gl4, view, light);

// seit Phase 13: ein Zug mit Halt, Signal und Dampf
Train zug = Train.brockenzug("Bergfahrt", 5);
zug.halte.add(new Train.Halt(2959, 60, abfahrt, "Schierke"));
zug.signal = (z, h) -> abschnittFrei(z, h);
zug.onPfiff = (z, s, grund) -> meldung(grund);
zug.step(gleis, dt);                                        // je Schritt
double[] schlot = Fahrzeug.schlot(gleis, zug);              // Dampfquelle, Last zug.last()
TrainGl zg = new TrainGl();
zg.init(gl4);
zg.draw(gl4, view, light, gleis, List.of(zug));
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Track` | Kern: Achse, Lage, Steigung, Krümmung, Windungen, Schwellen, Spirale; Gradiente, Bahnhöfe, Überhöhung, Umläufe, Übergänge |
| `Corridor` | Achse aus Flächen: Raster, Verdünnen, kürzester Weg, Glätten |
| `Bed` | Bahnkörper als Änderung des Geländes |
| `Train` | Kern: Fahrdynamik, Halte, Signal, Pfiffe, Auspuffschläge, Fahrzeuge |
| `Fahrzeug` | Kern: Lage der Fahrzeuge auf dem Gleis, Schornstein, Führerstand |
| `gl/RailGl` | Gleis als Netz (`rail.vert`, `rail.frag`) |
| `gl/TrainGl` | Lok und Wagen als Netz (`train.vert`, `train.frag`) |
| `demo/RailDemo` | Spirale auf welligem Gelände von oben, Längsschnitt mit Gradiente und Bahnhof, ein Zug fährt hinauf und hält in der Mitte (Last, Auspuffschläge); `--bild datei.png` |
| `demo/RailCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Grenzen

- Keine Übergangsbögen nach Klothoide: die Krümmung ändert sich nur so weich, wie die Glättung der Achse es hergibt. Keine Weichen, nur ein Gleis je Strecke.
- Die Achse aus Flächen ist so gut wie die Flächen: in Bahnhöfen mit mehreren Gleisen liegt sie in der Mitte des Bahnhofs.
- Der Bahnkörper ist eine Änderung der Höhe, keine Kunstbauten (Brücken, Mauern, Durchlässe).
- Der Zug ist ein Massepunkt mit der Steigung an seiner Mitte; Kupplungen, Schleudern und Gleitschutz fehlen. Die Regelung ist einfach (Soll in 5 s), kein Fahrplan im Package: Abfahrtszeiten und Signale gibt die App.
- Die Lok schaut immer zu wachsendem s (bergab Kohlenkasten voran); Umsetzen an den Endbahnhöfen und Weichen als Bauteile gibt es nicht, das Ausweichgleis ist ein eigenes Gleis neben dem Hauptgleis.
- Die Modelle sind einfach (Quader und Zylinder): keine Gestänge, die Räder drehen nicht; die Züge werfen keinen Schatten.
