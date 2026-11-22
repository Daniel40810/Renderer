# P6b – TerrainCheck

Attest für `com.dan.render.terrain`, Version 0.3. Stand 19.11.2026 20:56, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

13 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet | D:\Daten\HalligHooge\gebiet |  | – |
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)) |  | – |
| 1 Material | Albedo trocken (linear, aus DOP20 geeicht gegen Sentinel-2) | Watt 0,091/0,093/0,068; Sand 0,082/0,086/0,073; Fenne 0,081/0,090/0,059; Salzwiese 0,084/0,091/0,066; Deich 0,087/0,095/0,068; Warft 0,069/0,077/0,055; Wasser 0,069/0,071/0,052; Deckwerk 0,077/0,079/0,061;  |  | – |
| 1 Material | Albedo aller Klassen im Bereich natürlicher Böden | siehe oben | 0,02 … 0,5 [G] | ok |
| 1 Material | Stapel | 1m: Zelle 1 m, 24 × 32 Kacheln zu 1000 ab O 454000, N 6025000 · 10m: Zelle 10 m, 8 × 9 Kacheln zu 1000 ab O 427995, N 6007005 · 100m: Zelle 100 m, 3 × 3 Kacheln zu 1000 ab O 299000, N 5888000 · Mindesthöhe mindesthoehe: Zelle 1 m, 24 × 32 Kacheln zu 1000 ab O 454000, N 6025000 |  | – |
| 1 Material | Clipmap | 11 Stufen à 257², 1 m bis 1024 m, reicht 131 km; Ring 264² |  | – |
| 1 Material | Auge | Hanswarft O 470900 N 6046615, Boden 4,32 m NHN, Auge 6,02 m NHN |  | – |
| 2 Gelände gleich Physik (Java) | Laden aller fünf Clipmaps | 0,7 s (Höhe und Material, alle Stufen) |  | – |
| 2 Gelände gleich Physik (Java) | Prüfstein 2: Höhe jedes Punkts jeder Stufe gleich Gelaende.hoehe am selben Ort mit derselben Zellweite | 726539 Punkte, 0 anders | bitgleich | ok |
| 2 Gelände gleich Physik (Java) | Prüfstein 2, Naht (Java): jeder Randscheitel auf der Kante der gröberen Stufe, Rand auf ihren Gitterlinien | 10280 Randscheitel, größte Abweichung 1,91e-06 m, 0 Ränder nicht auf Gitterlinien | ≤ 1e-5 m [G], 0 | ok |
| 3 Auf der Karte | Gehen und Springen | 13 Bilder: 10 Schritte zu 44 m, ein Sprung um 5,8 km, 300 m, zurück; je Bild 148420 bis 2296380 Werte hochgeladen |  | – |
| 3 Auf der Karte | Daten auf der Karte gleich den Ringen der fünf Clipmaps (nach jedem Bild) | 47225035 Werte, 0 anders | bitgleich | ok |
| 3 Auf der Karte | Prüfstein 2: Höhe jedes Scheitels auf der Karte (mit Übergang) gegen Clipmap.vertexHeight | größte Abweichung 3,81e-06 m (726539 Scheitel je Bild) | ±1e-4 m [G] | ok |
| 3 Auf der Karte | Prüfstein 2, Naht auf der Karte: Randscheitel auf der Kante der gröberen Stufe | größte Abweichung 1,91e-06 m | ±1e-4 m [G] | ok |
| 4 Dicht | Prüfstein 3: Hanswarft nach Westen – Himmel unter der Kimm; NaN oder unendlich | 0 Bildpunkte; 0 (Gelände auf 64795 von 129600 Bildpunkten; 198 von 704 Blöcken gezeichnet, 452 außer Sicht, 54 bedeckt) | 0; 0 | ok |
| 4 Dicht | Prüfstein 3: Hanswarft nach Osten, Sonne im Rücken – Himmel unter der Kimm; NaN oder unendlich | 0 Bildpunkte; 0 (Gelände auf 69600 von 129600 Bildpunkten; 198 von 704 Blöcken gezeichnet, 452 außer Sicht, 54 bedeckt) | 0; 0 | ok |
| 4 Dicht | Prüfstein 3: Vogelblick aus 300 m – Himmel unter der Kimm; NaN oder unendlich | 0 Bildpunkte; 0 (Gelände auf 128054 von 129600 Bildpunkten; 181 von 704 Blöcken gezeichnet, 470 außer Sicht, 53 bedeckt) | 0; 0 | ok |
| 5 Licht | Himmelslicht auf die waagerechte Fläche bei gleichmäßigem Himmel: π · L oben und unten | 12566,4 / 12566,4 lx gegen 12566,4 lx (± 2,7e-07) | relativ ≤ 1e-3 [G] | ok |
| 5 Licht | Nässe 1 (water.wetAge = 0) auf reinem Watt: Albedo mal (1 − 0,45) | 8 Bildpunkte dunkler, kleinstes Verhältnis 0,550, Median 0,550 | 0,55 ± 0,01 [G] | ok |
| 5 Licht | Nässe nach einer Halbwertszeit (2 h): Restnässe + halber Rest | kleinstes Verhältnis 0,707 | 0,708 ± 0,01 [G] | ok |
| 5 Licht | Schatten bei 3° Sonne aus WSW | 9,2 % der Ringplätze der Stufen 0–5 im Schatten |  | – |
| 6 Aufräumen | Speicherkonto nach allen Teilen | TerrainCheck 0,0 MB von 512,0 MB, Spitze 158,3 MB | 0 belegt | ok |
| 6 Aufräumen | Validierungsschicht | nicht eingeschaltet (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
