# P9a – SedimentCheck (Referenz)

Attest für `com.dan.physics.sediment`, Version 0.3. Stand 17.11.2026 23:06, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

24 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Absinkgeschwindigkeit und Schwelle (Soulsby 1997) | Prüfstein 1: Absinkgeschwindigkeit, größte Abweichung über d = 0,063–2 mm | 4,20e-08 % | ≤ 1,00 % | ok |
| 1 Absinkgeschwindigkeit und Schwelle (Soulsby 1997) | Prüfstein 1: D* und kritische Schubspannung, größte Abweichung | 4,37e-08 % | ≤ 1,00 % | ok |
| 1 Absinkgeschwindigkeit und Schwelle (Soulsby 1997) | Werte | 0,063 mm: 2,29 mm/s, τ_cr 0,120 N/m²; 0,100 mm: 5,68 mm/s, τ_cr 0,143 N/m²; 0,200 mm: 20,25 mm/s, τ_cr 0,176 N/m²; 0,500 mm: 66,43 mm/s, τ_cr 0,262 N/m²; 1,000 mm: 114,41 mm/s, τ_cr 0,481 N/m²; 2,000 mm: 173,73 mm/s, τ_cr 1,168 N/m²;  |  | – |
| 2 Schubspannung aus Strom und Welle (Soulsby 1997) | Prüfstein 2: τ_m und τ_max im Schritt des Modells gegen die Formeln, größte Abweichung über 6 Fälle | 4,16e-08 % | ≤ 2,00 % | ok |
| 2 Schubspannung aus Strom und Welle (Soulsby 1997) | Werte (N/m²) | U 1,0, h 5, u_bot 0,0, φ 0°: τ_m 1,216, τ_max 1,216; U 0,5, h 2, u_bot 0,3, φ 0°: τ_m 0,468, τ_max 1,140; U 0,5, h 2, u_bot 0,3, φ 45°: τ_m 0,468, τ_max 1,056; U 0,5, h 2, u_bot 0,3, φ 90°: τ_m 0,468, τ_max 0,819; U 0,2, h 10, u_bot 0,5, φ 30°: τ_m 0,107, τ_max 2,078; U 0,0, h 3, u_bot 0,4, φ 0°: τ_m 0,000, τ_max 1,172;  |  | – |
| 3 Schwebstoff: Anpassung an das Gleichgewicht | Fall | d 0,1 mm, h 5 m, U 1,0 m/s: c_gl 0,5324 kg/m³, Anpassungslänge L = U T_s = 425 m (43 Zellen) |  | – |
| 3 Schwebstoff: Anpassung an das Gleichgewicht | Prüfstein 3: Konzentration längs des Gerinnes gegen c_gl (1 − e^(−x/L)), größte Abweichung | 0,645 % | ≤ 5,00 % | ok |
| 3 Schwebstoff: Anpassung an das Gleichgewicht | Werte | x = 0,5 L: 0,2127 statt 0,2113 kg/m³; x = 1,0 L: 0,3364 statt 0,3365 kg/m³; x = 2,0 L: 0,4605 statt 0,4611 kg/m³; x = 3,0 L: 0,5054 statt 0,5058 kg/m³;  |  | – |
| 3 Schwebstoff: Advektion einer Wolke | Prüfstein 3: Masse der Wolke (im Gebiet und über den Rand) gegen den Anfang, relativ | 9,44e-13 | ≤ 1,00e-06 | ok |
| 3 Schwebstoff: Advektion einer Wolke | Kleinste Konzentration | 0,00 kg/m³ | ≥ 0 | ok |
| 3 Schwebstoff: Advektion einer Wolke | Weg des Schwerpunkts gegen U · t (in Zellen) | 0,0294 | ≤ 1,00 | ok |
| 3 Schwebstoff: Advektion einer Wolke | Über den Rand | -2,9e-08 der Masse; 40 Teilschritte |  | – |
| 4 Schlick: Absetzsäule (Krone) und Erosion (Partheniades) | Prüfstein 4: Absetzsäule über 8 000 s (2 h/w_s) gegen c0 e^(−w_s t/h), größte Abweichung | 0,500 % | ≤ 2,00 % | ok |
| 4 Schlick: Absetzsäule (Krone) und Erosion (Partheniades) | Abgesetzt gegen den Verlust im Wasser, relativ | 1,92e-14 | ≤ 1,00e-09 | ok |
| 4 Schlick: Absetzsäule (Krone) und Erosion (Partheniades) | Prüfstein 4: Erosion bei τ = 1,33 N/m² gegen M (τ/τ_e − 1) t / h, Abweichung | 7,95e-06 % | ≤ 2,00 % | ok |
| 4 Schlick: Absetzsäule (Krone) und Erosion (Partheniades) | Konzentration nach 600 s | 0,1575 kg/m³ |  | – |
| 5 Wanderung eines flachen Hügels nach Exner | Fall | Tiefe 2 m, U 1,0 m/s, d 0,3 mm, Hügel 2 cm hoch: Geschiebe 0,0597 kg/(s m), Geschwindigkeit der Sohlform 5,87 m/Tag |  | – |
| 5 Wanderung eines flachen Hügels nach Exner | Prüfstein 5: Weg des Schwerpunkts nach 6,8 Tagen gegen c · t (40,6 statt 40,0 m), Abweichung | 1,51 % | ≤ 5,00 % | ok |
| 5 Wanderung eines flachen Hügels nach Exner | Volumen des Hügels gegen den Anfang (mit dem Fluss über den Rand), relativ | -7,43e-14 | ≤ 1,00e-06 | ok |
| 5 Wanderung eines flachen Hügels nach Exner | Höhe am Ende | 1,8 von 2,0 cm (aufwind erster Ordnung verbreitert den Hügel) |  | – |
| 6 Grabenwanderung (Laborfall von van Rijn 1987): Auskunft | Versuch | h 0,39 m, U 0,51 m/s, d50 0,16 mm, Graben 0,15 m tief, Böschungen 1:10, 15 h; Süßwasser, ν 1,0 · 10⁻⁶ m²/s |  | – |
| 6 Grabenwanderung (Laborfall von van Rijn 1987): Auskunft | Zulauf im Gleichgewicht | Schwebstoff 0,179 kg/m³ (im Versuch nachgerechnet mit 0,14 kg/m³ Zugabe), 0,0356 kg/(s m) in Schwebe, Geschiebe 0,0072 kg/(s m); Rippel 1,5 cm hoch, 12 cm lang |  | – |
| 6 Grabenwanderung (Laborfall von van Rijn 1987): Auskunft | Nach 15 h | Querschnitt des Grabens 64 % des Anfangs; Schwerpunkt 10,32 m stromab gewandert; tiefste Stelle 0,029 m bei x = 20,3 m (Anfang: Sohle 9,5–12,5 m) |  | – |
| 6 Grabenwanderung (Laborfall von van Rijn 1987): Auskunft | Einordnung | im Versuch blieb der Graben nach 15 h deutlich erhalten (veröffentlichte Abbildungen, hier nicht nachgemessen); das Modell verfüllt und verschmiert ihn schneller |  | – |
| 6 Grabenwanderung (Laborfall von van Rijn 1987): Auskunft | Teilschritte | 362400 |  | – |
| 7 Böschungsrutsch | Prüfstein 7: steilste Kante nach 3 000 Teilschritten (vorher 60,0°) | 30,00° | ≤ 30,5° [G] | ok |
| 7 Böschungsrutsch | Prüfstein 7: Volumen gegen den Anfang, relativ | 0,00 | ≤ 1,00e-12 | ok |
| 8 Rippel (Soulsby und Whitehouse 2005) | Prüfstein 8: Höhe und Länge im Gleichgewicht gegen die Formeln, größte Abweichung | 3,83e-08 % | ≤ 2,00 % | ok |
| 8 Rippel (Soulsby und Whitehouse 2005) | Werte | d 0,1 mm: 13,6 mm hoch, 115 mm lang; d 0,2 mm: 18,6 mm hoch, 146 mm lang; d 0,4 mm: 25,3 mm hoch, 232 mm lang;  |  | – |
| 8 Rippel (Soulsby und Whitehouse 2005) | Prüfstein 8: Richtung folgt dem Strom (erst +x, dann +y) | 0,0°, dann 90,0° | 0° und 90° ± 2° | ok |
| 8 Rippel (Soulsby und Whitehouse 2005) | Unter der Schwelle (U 0,1 m/s) bleiben die Rippel stehen | 18,57 mm | unverändert | ok |
| 8 Rippel (Soulsby und Whitehouse 2005) | Über der Grenze zum Schichtfluss (U 1,5 m/s) verschwinden sie | 0,000 mm | < 1 % der Höhe | ok |
| 9 Massenbilanz | Fall | 60 × 40 Zellen zu 20 m, Tide 0,9 m in 1 h über 2 h, Seegang u_bot 0,2 m/s; im Mittel 1 % der Zellen trocken; 1338 Teilschritte |  | – |
| 9 Massenbilanz | Umsatz | Sand 45669 t am Boden bewegt, -144,7 t über den Rand; Schlick 538 t, -22,3 t über den Rand; größte Bodenänderung 283,7 mm, Schwebstoff bis 9,80 kg/m³ |  | – |
| 9 Massenbilanz | Prüfstein 9: Sand (Wasser, Boden, Rand) gegen den Anfang, relativ zum Umsatz | 5,85e-17 | ≤ 1,00e-06 | ok |
| 9 Massenbilanz | Prüfstein 9: Schlick (Wasser, Boden, Rand) gegen den Anfang, relativ zum Umsatz | -4,16e-14 | ≤ 1,00e-06 | ok |
| 9 Massenbilanz | Alle Werte endlich, Konzentrationen nicht negativ | ja | ja | ok |
| 9 Massenbilanz mit benetztem Anteil und Porosität je Zelle (seit 0.2) | Fall | 60 × 40 Zellen zu 20 m, Tide 0,9 m in 1 h über 2 h, Seegang u_bot 0,2 m/s; im Mittel 1 % der Zellen trocken; 1338 Teilschritte |  | – |
| 9 Massenbilanz mit benetztem Anteil und Porosität je Zelle (seit 0.2) | Umsatz | Sand 38124 t am Boden bewegt, -73,6 t über den Rand; Schlick 330 t, -14,2 t über den Rand; größte Bodenänderung 961,3 mm, Schwebstoff bis 8,19 kg/m³ |  | – |
| 9 Massenbilanz mit benetztem Anteil und Porosität je Zelle (seit 0.2) | Prüfstein 9: Sand (Wasser, Boden, Rand) gegen den Anfang, relativ zum Umsatz | -1,42e-17 | ≤ 1,00e-06 | ok |
| 9 Massenbilanz mit benetztem Anteil und Porosität je Zelle (seit 0.2) | Prüfstein 9: Schlick (Wasser, Boden, Rand) gegen den Anfang, relativ zum Umsatz | 7,90e-14 | ≤ 1,00e-06 | ok |
| 9 Massenbilanz mit benetztem Anteil und Porosität je Zelle (seit 0.2) | Alle Werte endlich, Konzentrationen nicht negativ | ja | ja | ok |
| 11 Zeiten der Referenz auf dem Prozessor (Auskunft) | Referenz, ein Faden | 1160 ns je Zelle und Teilschritt (250 × 200 Zellen) |  | – |
| Offen | Prüfsteine 1, 2, 8 | gegen unabhängig gerechnete Werte der Formeln (Python); die Beispiele aus Soulsby (1997) lagen nicht vor |  | – |
| Offen | Prüfstein 6 (Grabenwanderung) | Auskunft: das gemessene Sohlprofil nach 15 h liegt nur als Abbildung vor |  | – |
| Offen | Prüfsteine 10 und 11 (Karte gegen Referenz, Zeiten auf der 680M) | SedimentKarteCheck, Attest P9a_SedimentKarte.md |  | – |
| Offen | Rechenzeit | 141 s |  | – |

Alles in Ordnung.
