# P11c2 – render.analysis ohne Gebiet

Attest für `com.dan.render.analysis`, Version 0.1. Stand 19.11.2026 15:15, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

13 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Isolinien (Prüfstein 18) | Ebene: Abstand der Punkte von der wahren Linie | größter 3,44e-05 Zellen; 11 Linien, 8319 Punkte | ≤ 1 · 10⁻³ Zelle [G]; 11 Linien | ok |
| 1 Isolinien (Prüfstein 18) | Kegel: Punkte gegen den linearen Schnitt auf ihrer Kante | größter 3,04e-05 Zellen, 6086 Punkte | ≤ 1 · 10⁻³ Zelle [G] | ok |
| 1 Isolinien (Prüfstein 18) | Kegel: Punkte gegen den Kreis, Halbmesser ab 150 Zellen | r = 20: 5,99e-03 (h²/8r = 6,25e-03); r = 50: 2,32e-03 (h²/8r = 2,50e-03); r = 100: 1,24e-03 (h²/8r = 1,25e-03); r = 150: 8,27e-04 (h²/8r = 8,33e-04); r = 200: 6,04e-04 (h²/8r = 6,25e-04); r = 240: 5,09e-04 (h²/8r = 5,21e-04) | ≤ 1 · 10⁻³ Zelle ab r = 150 [G]; darunter zur Auskunft | ok |
| 1 Isolinien (Prüfstein 18) | Kegel: je Stufe genau eine geschlossene Linie | ja | ja | ok |
| 1 Isolinien (Prüfstein 18) | Lücke aus NaN quer durch drei Kreise | 3 offene, 0 geschlossene Linien | 3 offen, 0 geschlossen | ok |
| 1 Isolinien (Prüfstein 18) | Douglas und Peucker: Abstand jedes Punkts zur vereinfachten Linie | größter 0,095 Zellen; 6086 → 634 Punkte | ≤ Toleranz 0,1 Zellen | ok |
| 1 Isolinien (Prüfstein 18) | Zeit der Isolinien (Prozessor, ein Faden) | G3 700 × 400, 8 Stufen der Tiefe, verrauschtes Feld: 88 ms, 128754 Strecken |  | – |
| 3 Querschnitt (P11c3) | Durchfluss durch die Treppe bei gleichförmigem Fluss gegen q · n · L, 50 Strecken in alle Richtungen | größte relative Abweichung 9,1e-07; 8050 Kanten | ≤ 1 · 10⁻⁵ (q liegt wie im Archiv als float vor) | ok |
| 3 Querschnitt (P11c3) | Geschlossener Weg um 70 × 50 Zellen, q nach Ost wächst je Spalte: Fluss hinaus | 700,000048 m³/s gegen 700,000000 | ≤ 1 · 10⁻⁶ relativ | ok |
| 3 Querschnitt (P11c3) | Profil: Strömung 0,8 m/s nach Ost, Schnitt nach Nord, quer nach links | größte Abweichung von −0,8 m/s 1,2e-08; 321 Punkte | ≤ 1 · 10⁻⁶; 321 Punkte | ok |
| 3 Querschnitt (P11c3) | Profil über die Wasserkante (Spalte 150) | 20 von 41 Punkten trocken |  | – |
| 3 Querschnitt (P11c3) | Bilanz einer Sinuswelle (1 000 m³/s, 12 h 25 min), zwei Perioden | links 2,846e+07 m³ (Formel 2,846e+07), rechts 2,846e+07 m³, netto 40,5 m³ | links ±0,1 %; netto ≤ 1 % von links | ok |
| 2 Partikel auf der Karte (Prüfstein 17) | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)) |  | – |
| 2 Partikel auf der Karte (Prüfstein 17) | Gleichförmige Strömung (0,8; −0,3 m/s), 1 000 s in 100 Schritten | größte Abweichung 8,74e-04 Zellen bei 42,7 Zellen Weg: 2,05e-05 | ≤ 0,1 % des Wegs [G] | ok |
| 2 Partikel auf der Karte (Prüfstein 17) | Starrer Wirbel, ein Umlauf (3 600 s, 360 Schritte, ω Δt = 0,0175) | größte Abweichung vom Ausgang 3,23e-04 des Halbmessers (radial 9,29e-06) | ≤ 0,1 % je Umlauf [G] | ok |
| 2 Partikel auf der Karte (Prüfstein 17) | Karte gegen Runge-Kutta 2 in double | größte Abweichung 4,44e-04 Zellen |  | – |
| 2 Partikel auf der Karte (Prüfstein 17) | Neu gesetzte Partikel nur auf Wasser (Westhälfte trocken), drei Schritte | 1744 von 2000 leben, davon 0 auf trockenen Zellen | 0 auf trockenen; mehr als die Hälfte lebt | ok |

## Befund

- 1 Isolinien (Prüfstein 18): Gegen den wahren Kreis kann ein linearer Schnitt zwischen den Mitten nicht genauer sein als die Krümmung erlaubt: rund h²/(8 r) mit h = 1 Zelle. Unter r = 125 Zellen liegt das über 10⁻³; die Linien treffen die Schranke, der Fehler ist der der Abtastung, nicht des Verfahrens.

Alles in Ordnung.
