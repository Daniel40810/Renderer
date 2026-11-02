# com.dan.impact – das Nahfeld des Eintauchens

**Stand: gebaut, Abnahme mit Hinweis (LituyaBay1958 Phase 10): Die Höhe im Laborfall liegt rund 30 % über FRITZ01, die Ankunft stimmt; Wasser durch den ganzen Gleitkörper folgt als Phase 10b.** Ein Gleitkörper aus Granulat taucht mit großer Geschwindigkeit in Wasser. Gerechnet wird ein Schnitt (2D, je Meter Breite) mit schwach kompressiblem SPH: Wasser und Granulat als zwei Phasen, Luft als Hohlraum. Das Package kennt keine App und kein anderes Package; der Kern braucht nur Java 21, `demo/` zusätzlich `FStyle.jar`. Später für Glacier (Kalbung), Edersee, Stapellauf. Der Würfel in 3D und die Rechnung auf der Grafikkarte kommen mit der Welle (Phase 11 und 13).

Zum Herauskopieren reicht `src/com/dan/impact/`.

## Was es rechnet

- **SPH:** Wendland C2, h = √2 dx; Dichte aus der Kontinuitätsgleichung in Volumenform; Tait (γ 7) je Phase mit eigenem ρ₀ und gemeinsamem c₀; Zug abgeschnitten (Hohlraum statt Kleben); Druckkraft nach Hu und Adams (Dichtesprung 1 : 1,6 bis 1 : 2,7); künstliche Zähigkeit nach Monaghan; δ-SPH nur innerhalb einer Phase, ohne hydrostatischen Anteil (Fourtakis). Zeitschritt: erst Dichte, dann Druck, dann Geschwindigkeit, dann Ort.
- **Granulat** (`Granulate`) nach Mohr-Coulomb, viskoplastisch und implizit (Jacobi): η = (c + p′ tan φ)/(2|D| + ε), am Grund mit tan δ; p′ ist der SPH-Druck ohne den Wasserdruck unter dem Ruhespiegel [G]; 16 Jacobi-Schritte, ε = 1e-5. Schalter: `capLoad` (höchstens die Auflast), `dynamicBed` (Grundreibung mit dem Wanddruck), `grainAlpha` (unelastischer Stoß).
- **Poren** (`Granulate.porosity`, `Impact.poreTime`): Unter Wasser füllen sich die Poren des Granulats. Ein Wasserteilchen, das Körnern mit freiem Porenraum näher als 1,5 dx kommt, geht ganz in sie über (Masse und Impuls, nach freiem Raum verteilt); die Körner werden schwerer, ihr Volumen bleibt. So verdrängt der Körper unter Wasser nur noch sein Kornvolumen. Vorgabe: sofort [G]; FRITZ01 39 %. Die Masse bleibt genau (Wasser + Porenwasser + hinaus = Anfang).
- **Grund** als Linienzug mit drei Lagen Wandpartikeln (Druck nach Adami) und einem harten Schutz am Linienzug.
- **Zulauf** (`Inflow`, `Impact.feed`): Mächtigkeit s(t) und Geschwindigkeit v(t) je Meter Breite an der Uferlinie; `Inflow.fritz01()` ist das Profil des Versuchs (98,5 · 10³ t je Meter bei 1,61 t/m³, bis 134 m, 110 m/s, 6,8 s). Der ganze Körper liegt zu Beginn frei auf einer Rampe hangauf (Hangabtrieb aufgehoben, keine Reibung, wie der Schlitten im Versuch); er hat nur seinen eigenen Impuls. Ein geführter Puffer wirkte wie ein Stempel mit unbegrenzter Kraft.
- **Messen:** Pegel (oberer Rand der zusammenhängenden Säule aus Wasser und Granulat), Rand für das Fernfeld (`Impact.Far`: Wasserstand und Durchfluss je Randzelle), Kaverne (Luft unter dem Ruhespiegel unter Wasser oder Granulat), Energie der führenden Welle ρ g ∫ η² dx gegen ½ m v², Auflauf.
- **`Channel`:** Flachwasser in 1D mit HLL, übernimmt die Welle über `Impact.Far` (Empfänger für die Prüfung, bis `wave` in Phase 11 HLL bekommt); `fromEta` nimmt nur den Wasserstand und setzt den Durchfluss der rechtslaufenden Welle.

## Benutzung

```java
Impact s = Presets.gilbertInlet1958(Impact.Quality.LOW);          // Laborfall FRITZ01 in Naturmaßen
Channel ch = new Channel(3000, 1, Presets.DEPTH);
s.far(885, ch);                                                   // Rand des Nahfelds
s.run(24);
Impact.Gauge g = s.gauges().get(1);                              // 885 m
double a = g.max(), t = g.timeOfMax();
Impact.Frame f = s.frame();                                       // Partikel für die Grafik

// eigener Zufluss, etwa aus einem Strom je Schritt
Inflow in = new Inflow("Strom", zeiten, maechtigkeit, geschwindigkeit);
Impact r = Presets.gilbertInlet1958(Impact.Quality.LOW, in, Granulate.rock());
```

## Prüfung (`demo/ImpactCheck`, Laborfall mit 5 m), Stand 02.11.2026

| Prüfung | Ergebnis | Soll |
|---|---|---|
| Wendland C2 normiert | 1,0063 | 1 |
| Zufluss FRITZ01 | 61 090 m² je Meter, bis 134 m, 6,8 s | 61 180 m² |
| Becken 40 m, Druck am Grund (Mittel 2–6 s) | 405 kPa | 402 kPa |
| Einzelwelle 8 m auf 40 m | 21,7 m/s; Höhe 6,8 → 5,5 m (2 m Abstand) | √(g(h + H)) = 21,7 m/s |
| Schüttung 25° / 55° (φ 35°) | steht (24,3°) / 34,5° | steht / 35° |
| Kanal, Puls 10 m | Volumen genau, 8,5 m nach 1 km | erste Ordnung dämpft |
| **Laborfall 885 m** | **205 m nach 15,8 s** | **152 m nach 16 s; geprüft: Ankunft ±2 s, Höhe −20 % bis +50 % (Abnahme mit Hinweis)** |
| Laborfall 600 m | über 200 m ab 9,6 s | über 200 m bei 11,1 s |
| Kaverne | 2 900 m² | F > 1 |
| Energieanteil der Welle | 13,6 % | 2–30 % |
| Masse (Wasser + Poren), Gleitkörper | genau, 98,4 · 10⁶ kg/m | 98,5 · 10⁶ |
| Übergabe an den Kanal bei 1 100 m | 190 gegen 180 m | ±30 % |
| zwei Läufe | gleich | |

Laborfall bei 885 m, von Hand gerechnet: ohne Poren 229 / 206 / 221 m (8 / 5 / 4 m, konvergiert); mit Poren 188 m (8 m), 167 m (5 m, noch mit 2 % der Masse auf der Rampe), 205 m (5 m, Rampe rutscht nach). Kleine Änderungen verschieben den Pegel um ±20 %. Siehe Werkbuch LituyaBay1958, Abschnitt 19, Phase 10.

## Demo

`demo/ImpactDemo.java` → Run File: Schnitt mit Partikeln, Pegel über die Zeit mit den Werten aus FRITZ01 und dem Kanal, Zufluss und Kaverne, Energie. Ohne Fenster: `ImpactDemo --bild datei.png [t] [LOW|MEDIUM|HIGH]`.
