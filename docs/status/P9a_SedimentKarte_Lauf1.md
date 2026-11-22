# P9a – SedimentKarteCheck (Karte gegen Referenz, Zeiten)

Attest für `com.dan.physics.sediment.gpu`, Version 0.1. Stand 17.11.2026 16:41, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

9 Prüfsteine, 2 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 10 Karte gegen Referenz: Bilanzfall | Teilschritte | Referenz 1338, Karte 1338 |  | – |
| 10 Karte gegen Referenz: Bilanzfall | Bilanzfall (2 h): Werte der Karte endlich | ja | ja | ok |
| 10 Karte gegen Referenz: Bilanzfall | Prüfstein 10, Bilanzfall (2 h): sed.c, größte relative Abweichung (347 Zellen, größter Wert 9,80 kg/m³, im Mittel 2,3e-06) | 7,25e-06 | ≤ 0,00100 | ok |
| 10 Karte gegen Referenz: Bilanzfall | Prüfstein 10, Bilanzfall (2 h): Bodenänderung, größte relative Abweichung (166 Zellen, größter Wert 0,284 m, im Mittel 7,6e-06) | 8,10e-05 | ≤ 0,00100 | ok |
| 10 Karte gegen Referenz: Bilanzfall | Rippelhöhe, größte relative Abweichung | 4,1e-05 (2364 Zellen) |  | – |
| 10 Karte gegen Referenz: Graben | Teilschritte | Referenz 46080, Karte 46080 |  | – |
| 10 Karte gegen Referenz: Graben | Graben (2 h): Werte der Karte endlich | ja | ja | ok |
| 10 Karte gegen Referenz: Graben | Prüfstein 10, Graben (2 h): sed.c, größte relative Abweichung (300 Zellen, größter Wert 0,179 kg/m³, im Mittel 1,0e-05) | 3,49e-05 | ≤ 0,00100 | ok |
| 10 Karte gegen Referenz: Graben | Prüfstein 10, Graben (2 h): Bodenänderung, größte relative Abweichung (159 Zellen, größter Wert 0,0636 m, im Mittel 8,2e-06) | 2,04e-05 | ≤ 0,00100 | ok |
| 10 Karte gegen Referenz: Wolke | Wolke (400 s): Werte der Karte endlich | ja | ja | ok |
| 10 Karte gegen Referenz: Wolke | Prüfstein 10, Wolke (400 s): sed.c, größte relative Abweichung (357 Zellen, größter Wert 0,797 kg/m³, im Mittel 4,6e-05) | 0,00158 | ≤ 0,00100 | **fehlt** |
| 10 Karte gegen Referenz: Wolke | Prüfstein 10, Wolke (400 s): Bodenänderung, größte relative Abweichung (299 Zellen, größter Wert 1,33e-18 m, im Mittel 1,1e+10) | 6,19e+10 | ≤ 0,00100 | **fehlt** |
| 11 Zeiten auf der Karte | 500 × 400 Zellen | 22,8 ns je Zelle und Teilschritt (400 Teilschritte in 1,82 s, mit Böschungsrutsch); je 20 min Szenenzeit bei 150 Teilschritten [S] 0,68 s |  | – |
| 11 Zeiten auf der Karte | hydro je 20 min Szenenzeit (P8c) | 3,3 s |  | – |
| 11 Zeiten auf der Karte | Prüfstein 11, Vorgabe (höchstens halb so viel wie hydro) | hält: 0,68 s ≤ 1,63 s |  | – |
| 11 Zeiten auf der Karte | 700 × 400 Zellen | 23,2 ns je Zelle und Teilschritt (400 Teilschritte in 2,59 s, mit Böschungsrutsch); je 20 min Szenenzeit bei 150 Teilschritten [S] 0,97 s |  | – |
| 11 Zeiten auf der Karte | Rechenzeit | 24 s |  | – |

2 Verstöße.
