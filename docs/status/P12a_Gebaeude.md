# P12a – GebaeudeCheck

Attest für `com.dan.aufbereitung (gebaeude)`, Version 0.1. Stand 19.11.2026 20:01, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

11 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Leser | Häuser (Building; BuildingPart gehört zum Haus) | 2 | 2 | ok |
| 1 Leser | Merkmale und Flächen des ersten Hauses | DESH_TEST_1, Friesenhaus, Dach 3100, 8,0 m; Wand 4, Dach 3, Grund 1; innere Ringe 1; EPSG:25832 | 4 Wände, 3 Dächer, 1 Grund, 1 innerer Ring | ok |
| 1 Leser | Flächen ohne Thema nach der Neigung | Grund 1, Wand 1, Dach 1 | je 1 | ok |
| 1 Leser | Häuser gebaut: Reet als Regel, flaches Dach erkannt; Boden, Traufe, First | Reet und flach; Boden 4,00, Traufe 6,60, First 12,00 m | Reet, flach; 4 / 6,6 / 12 m | ok |
| 1 Leser | Dreiecke des ersten Hauses: Fläche gleich der Fläche der Ringe | 423,535 m² gegen 423,535 m² (Sockel nicht mitgerechnet: ohne Gelände kein Sockel) | relativ < 1e-4 | ok |
| 1 Leser | Normalen: Wände nach außen, Dächer nach oben | ja | ja | ok |
| 1 Leser | Zwerchgiebel über der Mitte der Südtraufe (F129 A) | 1 gebaut; Giebelwand bis 9,91 m in der Mitte (E 8), Soll 9,91 m; 75 statt 48 Scheitel | 1; First des Giebels ± 1 mm | ok |
| 2 Häuser im Gebiet | bau/gebaeude.bin | 137 Häuser, 4375 Dreiecke, Ursprung 454000,0 / 6057000,0 | vorhanden | ok |
| 2 Häuser im Gebiet | Bericht des Schritts | 137 Häuser aus 6 Dateien (137 im Gebiet gelesen), 4375 Dreiecke, 0 Flächen verworfen, 0 innere Ringe weggelassen; OSM: 105 von 110 Gebäuden getroffen; Ecken gegen Gelände: Mittel 0.37 m, größte 1.93 m; Dächer: 19 flach, 3 Reet ohne Messung (Vorgabe), 0 der Farbe nach Ziegel (als Reet geführt, Daniel 19.11.); 15 Zwerchgiebel |  | – |
| 2 Häuser im Gebiet | Prüfstein 1: Gebäude der OSM mit Gegenstück in LoD2 | 105 von 110 (95,5 %) | ≥ 95 % [G] | ok |
| 2 Häuser im Gebiet | Prüfstein 2: Boden gegen das Gelände an den Ecken der Grundfläche (größte Abweichung je Haus) | 95 % der Häuser ≤ 1,26 m; Median 0,42 m, größte 1,93 m | ≤ 0,3 m [G] | **fehlt** |
| 2 Häuser im Gebiet | Boden aus LoD2 gegen das Gelände, mit Vorzeichen (Median der Ecken je Haus) | 5 %: -0,95 m, Median -0,21 m, 95 %: -0,03 m; über dem Gelände (schwebend > 0,1 m): 1 von 137 Häusern |  | – |
| 2 Häuser im Gebiet | Prüfstein 3: Albedo des Dachs gegen die Farbe im DOP20 (geeicht), wo gemessen | ΔE₀₀ im Mittel 1,44 über 136 Dächer | ≤ 5 [G] | ok |
| 2 Häuser im Gebiet | Dächer | 118 Reet, 19 flach, 0 Ziegel |  | – |

1 Verstoß.
