# P6d – WaterCheck

Attest für `com.dan.render.water`, Version 0.1. Stand 18.11.2026 18:30, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

15 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet | D:\Daten\HalligHooge\gebiet; Auszug des Wassers D:\Daten\HalligHooge\_p6\wasser |  | – |
|  | Wirksame Fläche (P7g) | aus: geprüft werden Fresnel und Spiegel an der Normalen; die wirksame Fläche prüfen FlaecheCheck (Tafel) und ReferenzCheck (Bild) |  | – |
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| 1 Daten | Gitter | G3 UTM 32, O 454000–478000, N 6025000–6057000, Zelle 20 m, 1200 × 1600; Fenster G3 UTM 32, O 470000–472000, N 6045000–6047000, Zelle 20 m, 100 × 100 (ab Zelle 800, 1000), fein 20; G2 UTM 32, O 428000–502000, N 6011000–6097000, Zelle 200 m, 370 × 430 |  | – |
| 1 Daten | Bauwerke von G3 | 2430 Kanten, 42 Ringe; 14014 Zellen in Ringen (vom Rand ohne Bauwerk nicht erreichbar) |  | – |
| 1 Daten | Stände (ruhender Spiegel mit Gefälle 1e-4 nach Ost) | η₀ 0,0 m: 1574887 Zellen von G3 nass, im Fenster 2628; η₀ 0,4 m: 1650962 Zellen von G3 nass, im Fenster 3743; η₀ 1,9 m: 1816540 Zellen von G3 nass, im Fenster 4099; gebaut in 0,4 s |  | – |
| 1 Daten | Bauwerke im Fenster | 252 Kanten; Schwellen aus der Packung (F47 A) an 20000 Kanten |  | – |
| 2 Wasserlinie gleich Physik | η_Bild auf der Karte gegen Java, η₀ 0,0 m: alle Pixelmitten des Fensters | 4000000 Orte, 0 anders, größte Abweichung 0,0e+00 m | bitgleich [G] | ok |
| 2 Wasserlinie gleich Physik | Prüfstein 5, η₀ 0,0 m: benetzter Anteil im Bild gegen die Pixel unter η der Zelle je teilweise nasser Zelle (F46 A) | 403 Zellen; im Mittel 0,32 %, 95 % der Zellen ≤ 1,00 %, größte 3,0 %; Bild 53,0 % gegen 53,0 % der Fläche; Tabelle gegen dieselben Pixel im Mittel 0,49 % | ≤ 1 %, ≤ 5 % [G] | ok |
| 2 Wasserlinie gleich Physik | η_Bild auf der Karte gegen Java, η₀ 0,4 m: alle Pixelmitten des Fensters | 4000000 Orte, 0 anders, größte Abweichung 0,0e+00 m | bitgleich [G] | ok |
| 2 Wasserlinie gleich Physik | Prüfstein 5, η₀ 0,4 m: benetzter Anteil im Bild gegen die Pixel unter η der Zelle je teilweise nasser Zelle (F46 A) | 199 Zellen; im Mittel 0,18 %, 95 % der Zellen ≤ 0,75 %, größte 2,5 %; Bild 52,6 % gegen 52,6 % der Fläche; Tabelle gegen dieselben Pixel im Mittel 0,51 % | ≤ 1 %, ≤ 5 % [G] | ok |
| 2 Wasserlinie gleich Physik | Prüfstein 5, η₀ 1,9 m (Landunter): Wasser über die Deichkante in einen trockenen Ring | 0 Pixel (Ringzellen im Fenster: 5880, davon trocken 5880; Pixel außen nass 1613024) | 0 Pixel | ok |
| 3 Zeit | Prüfstein 6: η_Bild zur Zeit t auf der Karte gegen die lineare Interpolation der zwei Stände (Java, Bus.lies) | 1005692 Orte zu 5 Zeiten zwischen den Ständen 0 und 300 s; größte Abweichung 0 float-Schritte | ±1 float-Schritt | ok |
| 3 Zeit | Mitte der Zeit gegen das Mittel der Stände (Orte, die in beiden Ständen nass sind) | größte Abweichung 3,33e-02 m (Nachbarn, die nur in einem Stand nass sind, dürfen abweichen) |  | – |
| 3 Zeit | Prüfstein 6: Wechsel des Intervalls (Stand bei 600 s kommt an, t = 300 s) | 1497200 Orte, Sprung höchstens 6,0e-08 m | ≤ 1e-6 m | ok |
| 4 Optik | Prüfstein 7: Fresnel bei 0°, 53° und 85° gegen die Formel (n = 1,34, unpolarisiert) | 0°: 0,021112 (Formel 0,021112); 53°: 0,039940 (Formel 0,039940); 85°: 0,585329 (Formel 0,585329); größte Abweichung 9,7e-09 | ≤ 1e-4 | ok |
| 4 Optik | Prüfstein 7: Transmission durch 1 m reines Wasser gegen Pope und Fry (1997) | 680 nm 0,62814; 550 nm 0,94507; 440 nm 0,99367; relativ 3,2e-08 | relativ ≤ 1e-3 | ok |
| 4 Optik | Dämpfung a + b_b bei 30 mg/l Schwebstoff | R 0,890, G 0,738, B 1,611 je m (Grund in 1 m Tiefe senkrecht: 17 %, 23 %, 4 %) |  | – |
| 4 Optik | Tiefes Wasser, Sonne im Zenit, senkrecht gesehen: Reflexion der Säule π L / E | R 0,055, G 0,066, B 0,030 (Einfachstreuung) |  | – |
| 4 Optik | Kräuselung: Neigungsvarianz der 48 Wellen gleich Cox und Munk (U₁₀ = 5 m/s) | 0,028600 gegen 0,028600 | gleich | ok |
| 5 Dicht | Prüfstein 3 (Wasser): Hanswarft nach Westen – Himmel unter der Kimm des Wassers; NaN; Wasser im trockenen Viereck | 0; 0; 0 Bildpunkte (Wasser auf 67189, Grund im Viereck auf 0 von 129600) | 0; 0; 0 | ok |
| 5 Dicht | Prüfstein 3 (Wasser): Hanswarft nach Nordwesten über das trockene Viereck – Himmel unter der Kimm des Wassers; NaN; Wasser im trockenen Viereck | 0; 0; 0 Bildpunkte (Wasser auf 69008, Grund im Viereck auf 399 von 129600) | 0; 0; 0 | ok |
| 5 Dicht | Prüfstein 3 (Wasser): Vogelblick aus 300 m – Himmel unter der Kimm des Wassers; NaN; Wasser im trockenen Viereck | 0; 0; 0 Bildpunkte (Wasser auf 125005, Grund im Viereck auf 1335 von 129600) | 0; 0; 0 | ok |
| 5 Dicht | Spiegelung des Geländes (F45 A): Spiegelbild einer dunklen Wand (200 m, bis 5 m NHN) im glatten Wasser, mittlere Spalte | Unterkante Zeile 108 gegen 109 nach der Spiegelebene; 6 gegen 7 Zeilen | ±2 Bildpunkte [G] | ok |
| 6 Aufräumen | Speicherkonto nach allen Teilen | WaterCheck 0,0 MB von 768,0 MB, Spitze 90,7 MB | 0 belegt | ok |
| 6 Aufräumen | Validierungsschicht | nicht eingeschaltet (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
