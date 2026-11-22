# P6a – FrameCheck

Attest für `com.dan.render.frame`, Version 0.3. Stand 19.11.2026 14:54, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

27 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Blick | Lage relativ zum Auge in float, Punkte bis 30 km (Auge bei N 6 046 615 m) | größter Fehler 9,76e-04 m | ≤ 1 mm [G] | ok |
| 1 Blick | Projektion in float gegen Bildpunkt in double (4354 Punkte im Bild) | größter Abstand 7,35e-05 Bildpunkte | ≤ 0,01 Bildpunkte [G] | ok |
| 1 Blick | Tiefe umgekehrt: nah / Abstand längs der Achse | größter relativer Fehler 2,56e-08 | ≤ 1e-5 [G] | ok |
| 1 Blick | Strahl durch den Bildpunkt trifft den Punkt | größter Winkel 4,27e-16 rad | ≤ 1e-9 [G] | ok |
| 1 Blick | Die Blickachse liegt in der Bildmitte | (960,000000, 540,000000) | (960, 540) | ok |
| 1 Blick | Oben in der Szene ist oben im Bild (y nach unten) | y = 423,7 | < 540 | ok |
| 2 Filmkurve | Kurve | Kodak Vision3 250D auf Kodak Vision Print 2383, Tabelle 2048 Werte je Schicht über log10 H von -7 bis 3 |  | – |
| 2 Filmkurve | Tabelle mit Matrix und Projektion gegen Film.develop (20 000 Farben, −12 bis +8 Blenden um die Graukarte) | größte Abweichung 7,24e-06 | ≤ 1e-4 [G] | ok |
| 2 Filmkurve | Graukarte | Szene 0,18 → Bildschirm linear 0,1952, 0,1952, 0,1952 |  | – |
| 3 Ausgabe auf der Karte | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)) |  | – |
| 3 Ausgabe auf der Karte | Ablauf | 6 Durchgänge, 5 Sperren für 10 Betriebsmittel |  | – |
| 3 Ausgabe auf der Karte | Ausgabe auf der Karte (RGBA32F) gegen dieselbe Rechnung in double (15360 Bildpunkte, −10 bis +6 Blenden) | größte Abweichung 9,02e-07 | ≤ 2e-4 [G] | ok |
| 3 Ausgabe auf der Karte | Ausgabe auf der Karte gegen Film.develop | größte Abweichung 7,56e-06 | ≤ 5e-4 [G] | ok |
| 3 Ausgabe auf der Karte | Kein NaN in der Ausgabe | 0 Werte | 0 | ok |
| 3 Ausgabe auf der Karte | Ausgabe in sRGB (RGBA8, von der Karte verschlüsselt) gegen die lineare Ausgabe | 0 Stufen | ≤ 1 Stufe | ok |
| 4 Histogramm und Belichtung | Histogramm auf der Karte: Summe der Gewichte wie in Java (Schritt 4, zur Mitte schwerer) | 1717 gegen 1717 | gleich | ok |
| 4 Histogramm und Belichtung | Histogramm auf der Karte: Fächer wie in Java (Rundung von log2 an den Grenzen der Fächer) | 0 von 1717 Gewichten in einem anderen Fach | ≤ 0,1 % [G] | ok |
| 4 Histogramm und Belichtung | Ohne Tiefe im Bild (Testbild ohne Gelände): kein Bildpunkt mit Tiefe, es gilt das ganze Bild (F49 A) | 0 mit Tiefe von 960 gezählten | 0; ganzes Bild | ok |
| 4 Histogramm und Belichtung | Belichtung nach der ersten Messung (sofort): log2(Bezug / Mittel der Szene) | -0,734 Blenden, Mittel 0,1996 | genau | ok |
| 4 Histogramm und Belichtung | Vorbelichtung im dritten Bild | 0,6011 (das HDR-Bild trägt L · Vorbelichtung; hier legt das Testbild L ohne sie hinein) |  | – |
| 4 Histogramm und Belichtung | Augenanpassung ins Dunkle: nach 3 s um 1 − e^(−3/τ) des Wegs (τ = 3 s) | +0,934603 gegen +0,934603 Blenden | genau | ok |
| 5 Messen auf Gelände und Wasser (F49 A) | Histogramm auf der Karte, Fall 1, Gelände auf der unteren Hälfte: gezählte Bildpunkte mit Tiefe und alle wie in Java | 480 und 960 gegen 480 und 960 | gleich | ok |
| 5 Messen auf Gelände und Wasser (F49 A) | Histogramm auf der Karte, Fall 1, Gelände auf der unteren Hälfte: Fächer mit Tiefe und über alle wie in Java | 0 von 852 und 0 von 1717 Gewichten in einem anderen Fach | ≤ 0,1 % [G] | ok |
| 5 Messen auf Gelände und Wasser (F49 A) | Belichtung, Fall 1, Gelände auf der unteren Hälfte: gemessen auf dem Gelände | Gelände, Anteil mit Tiefe 50,0 %, +6,316 Blenden gegen +6,316 | Gelände; genau | ok |
| 5 Messen auf Gelände und Wasser (F49 A) | Fall 1: Belichtung über das ganze Bild zum Vergleich | -3,07 Blenden; auf dem Gelände +6,32, also 9,4 Blenden heller |  | – |
| 5 Messen auf Gelände und Wasser (F49 A) | Histogramm auf der Karte, Fall 2, Gelände auf den untersten 6 Zeilen: gezählte Bildpunkte mit Tiefe und alle wie in Java | 80 und 960 gegen 80 und 960 | gleich | ok |
| 5 Messen auf Gelände und Wasser (F49 A) | Histogramm auf der Karte, Fall 2, Gelände auf den untersten 6 Zeilen: Fächer mit Tiefe und über alle wie in Java | 0 von 80 und 0 von 1717 Gewichten in einem anderen Fach | ≤ 0,1 % [G] | ok |
| 5 Messen auf Gelände und Wasser (F49 A) | Belichtung, Fall 2, Gelände auf den untersten 6 Zeilen: unter 10 % [G] über das ganze Bild | ganzes Bild, Anteil mit Tiefe 8,3 %, -5,590 Blenden gegen -5,590 | ganzes Bild; genau | ok |
| 6 Sehen bei Nacht (F50 A) | Bezug nach Krawczyk bei der Leuchtdichte des Bezugs (1 000 cd/m²) gleich 0,12 | 0,120000 | 0,12 genau [G] | ok |
| 6 Sehen bei Nacht (F50 A) | Bezug nach Krawczyk (Gl. 11) | Mondnacht 0,0053 cd/m² (Landunter 23:45): -2,36 Blenden; Tag 2 780 cd/m² (Hochwasser 12:20): +0,03 Blenden; σ bei 0,005 cd/m²: 0,889 |  | – |
| 6 Sehen bei Nacht (F50 A) | Mondnacht im Bild (nach der Filmkurve) zum Tag wie bei Krawczyk: α/(1+α) bei 0,0053 gegen 1 000 cd/m² | 0,07814 gegen 0,07814; Mittel im Bild 0,0085 (sRGB 23 von 255), Tag 0,1092 (sRGB 93) | relativ ≤ 1e-6 | ok |
| 6 Sehen bei Nacht (F50 A) | Bezug steigt mit der Leuchtdichte; σ(K) = 1/2 | ja; 0,5 | ja; 0,5 | ok |
| 6 Sehen bei Nacht (F50 A) | Ausgabe mit Stäbchen auf der Karte gegen Belichtung.staebchen und die Kurve in double (15360 Bildpunkte, 10⁻⁴ bis 10² cd/m²) | größte Abweichung 8,66e-07 | ≤ 2e-4 [G] | ok |
| 6 Sehen bei Nacht (F50 A) | Anteil der Stäbchen σ(Y) | 10⁻⁴ cd/m²: 0,998; 10⁻² : 0,800; 1: 0,038; 100: 0,00040 |  | – |
| 7 Aufräumen | Speicherkonto nach allen Teilen | FrameCheck 0,0 MB von 256,0 MB, Spitze 0,6 MB | 0 belegt | ok |
| 7 Aufräumen | Validierung | nicht eingeschaltet (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
