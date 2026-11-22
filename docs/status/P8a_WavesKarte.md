# P8a – WavesKarteCheck (Karte gegen Referenz, Zeiten)

Attest für `com.dan.physics.waves.gpu`, Version 0.2. Stand 16.11.2026 15:34, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 6 Referenz (double) gegen Karte (float) | Strand schräg (100 × 24 Zellen, nur Ausbreitung) | 100 × 24 Zellen, 28 × 36 Stützstellen, 60 Schritte zu 10,0 s (300 Teilschritte), Hs bis 0,999 m, 2397 Zellen verglichen; Prozessor 3,5 s, Karte 1,3 s |  | – |
| 6 Referenz (double) gegen Karte (float) | Strand schräg (100 × 24 Zellen, nur Ausbreitung): Hs, größte relative Abweichung | 5,27e-07 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | Strand schräg (100 × 24 Zellen, nur Ausbreitung): Ort; Tm01; Richtung | Zelle 99, 16: 0,05014 gegen 0,05014 m; Tm01 bis 1,5e-07 relativ; Richtung bis 0,000° |  | – |
| 6 Referenz (double) gegen Karte (float) | 2 Refraktion, 30° | 100 × 1 Zellen, 5 × 36 Stützstellen, 400 Schritte zu 10,0 s (1200 Teilschritte), Hs bis 1,566 m, 100 Zellen verglichen; Prozessor 0,3 s, Karte 0,2 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 2 Refraktion, 30°: Hs, größte relative Abweichung | 1,76e-06 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 2 Refraktion, 30°: Ort; Tm01; Richtung | Zelle 99, 0: 1,56577 gegen 1,56578 m; Tm01 bis 2,5e-08 relativ; Richtung bis 0,000° |  | – |
| 6 Referenz (double) gegen Karte (float) | 3 Brechen | 150 × 1 Zellen, 14 × 36 Stützstellen, 600 Schritte zu 5,0 s (4200 Teilschritte), Hs bis 2,115 m, 150 Zellen verglichen; Prozessor 1,9 s, Karte 0,4 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 3 Brechen: Hs, größte relative Abweichung | 6,86e-07 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 3 Brechen: Ort; Tm01; Richtung | Zelle 66, 0: 2,03081 gegen 2,03081 m; Tm01 bis 7,1e-07 relativ; Richtung bis 0,000° |  | – |
| 6 Referenz (double) gegen Karte (float) | 4 Untiefe B1 | 80 × 137 Zellen, 16 × 36 Stützstellen, 90 Schritte zu 0,5 s (810 Teilschritte), Hs bis 0,103 m, 10960 Zellen verglichen; Prozessor 28,6 s, Karte 7,7 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 4 Untiefe B1: Hs, größte relative Abweichung | 2,22e-06 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 4 Untiefe B1: Ort; Tm01; Richtung | Zelle 70, 115: 0,07728 gegen 0,07728 m; Tm01 bis 1,0e-06 relativ; Richtung bis 0,000° |  | – |
| 6 Referenz (double) gegen Karte (float) | 5 Gegenstrom | 100 × 1 Zellen, 28 × 36 Stützstellen, 600 Schritte zu 5,0 s (1800 Teilschritte), Hs bis 1,289 m, 100 Zellen verglichen; Prozessor 2,1 s, Karte 0,3 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 5 Gegenstrom: Hs, größte relative Abweichung | 5,93e-06 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 5 Gegenstrom: Ort; Tm01; Richtung | Zelle 99, 0: 1,28867 gegen 1,28868 m; Tm01 bis 7,6e-07 relativ; Richtung bis 0,000° |  | – |
| 6 Referenz (double) gegen Karte (float) | 7 Untiefe, Strömung, Wind, alle Quellen | 30 × 20 Zellen, 28 × 36 Stützstellen, 120 Schritte zu 30,0 s (1800 Teilschritte), Hs bis 1,645 m, 588 Zellen verglichen; Prozessor 17,3 s, Karte 2,2 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 7 Untiefe, Strömung, Wind, alle Quellen: Hs, größte relative Abweichung | 0,000766 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 7 Untiefe, Strömung, Wind, alle Quellen: Ort; Tm01; Richtung | Zelle 17, 19: 1,61281 gegen 1,61158 m; Tm01 bis 1,5e-03 relativ; Richtung bis 0,046° |  | – |
| 6 Referenz (double) gegen Karte (float) | 7 Untiefe, Strömung, Wind, alle Quellen: Empfindlichkeit der Referenz | einmal um 1 · 10⁻⁷ gestört (nach Schritt 10), am Ende in Hs bis 5,33e-04: so weit trägt das Verfahren selbst eine Störung in der Größe der Rundung von float |  | – |
| 6 Referenz (double) gegen Karte (float) | 1 Wachstum 10 m/s | 101 × 1 Zellen, 28 × 36 Stützstellen, 240 Schritte zu 30,0 s (720 Teilschritte), Hs bis 0,836 m, 100 Zellen verglichen; Prozessor 2,6 s, Karte 0,2 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 1 Wachstum 10 m/s: Hs, größte relative Abweichung | 3,32e-06 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 1 Wachstum 10 m/s: Ort; Tm01; Richtung | Zelle 5, 0: 0,30975 gegen 0,30975 m; Tm01 bis 1,1e-06 relativ; Richtung bis 0,000° |  | – |
| 6 Referenz (double) gegen Karte (float) | 1 Wachstum 20 m/s | 101 × 1 Zellen, 28 × 36 Stützstellen, 240 Schritte zu 30,0 s (720 Teilschritte), Hs bis 2,661 m, 101 Zellen verglichen; Prozessor 1,0 s, Karte 0,2 s |  | – |
| 6 Referenz (double) gegen Karte (float) | 1 Wachstum 20 m/s: Hs, größte relative Abweichung | 0,000572 | ≤ 0,00100 | ok |
| 6 Referenz (double) gegen Karte (float) | 1 Wachstum 20 m/s: Ort; Tm01; Richtung | Zelle 17, 0: 1,23524 gegen 1,23595 m; Tm01 bis 9,9e-04 relativ; Richtung bis 0,009° |  | – |
| 6 Referenz (double) gegen Karte (float) | Alle Fälle: größte relative Abweichung in Hs | 7,66e-04 (7 Untiefe, Strömung, Wind, alle Quellen) |  | – |
| 8 Zeiten auf der Karte mit Strömung und allen Quellen | 28 × 36 Stützstellen | Ausbreitung 2,52 ns je Wert und Teilschritt, Quellen 2,02 ns je Wert und Schritt (38,7 Mio. Werte); G2 + G3 je 20 min Szenenzeit: 47,9 s |  | – |
| 8 Zeiten auf der Karte mit Strömung und allen Quellen | 24 × 24 Stützstellen | Ausbreitung 1,60 ns je Wert und Teilschritt, Quellen 2,30 ns je Wert und Schritt (22,1 Mio. Werte); G2 + G3 je 20 min Szenenzeit: 18,5 s |  | – |
| 8 Zeiten auf der Karte mit Strömung und allen Quellen | 20 × 18 Stützstellen | Ausbreitung 0,97 ns je Wert und Teilschritt, Quellen 2,50 ns je Wert und Schritt (13,8 Mio. Werte); G2 + G3 je 20 min Szenenzeit: 7,8 s |  | – |
| 8 Zeiten auf der Karte mit Strömung und allen Quellen | 28 × 72 Stützstellen (Auskunft, W2) | Ausbreitung 2,78 ns je Wert und Teilschritt, Quellen 2,32 ns je Wert und Schritt (77,4 Mio. Werte); G2 + G3 je 20 min Szenenzeit: 106,0 s |  | – |
| 8 Vorgabe für die Auflösung (12.9) | Maßstab | hydro G2 → G3 im Archiv 48 s je 20 min Szenenzeit (P4d, Fall 14, Zeitraffer 25); live für Physik und Bild zusammen 8,8 s (Zeitraffer 136), ein Fünftel davon 1,8 s |  | – |
| 8 Vorgabe für die Auflösung (12.9) | Archiv: feinste Auflösung, die höchstens so viel kostet wie hydro | 28 × 36 (48 s) |  | – |
| 8 Vorgabe für die Auflösung (12.9) | Live: feinste Auflösung, die höchstens ein Fünftel kostet | keine – live bleibt der Seegang aus dem Wind (wie bisher) |  | – |
| 8 Vorgabe für die Auflösung (12.9) | 72 statt 36 Richtungen (W2) | 2,2-fache Rechenzeit (28 × 72: 106 s gegen 28 × 36: 48 s) |  | – |
| 8 Vorgabe für die Auflösung (12.9) | Rechenzeit | 139 s |  | – |

Alles in Ordnung.
