# P12b – GestaltCheck

Attest für `com.dan.aufbereitung (gestalt)`, Version 0.1. Stand 19.11.2026 21:23, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

13 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Leser und Schreiber | Gestaltkarte geschrieben und gelesen | 2 Kacheln, bitgleich | bitgleich | ok |
| 1 Leser und Schreiber | Art und Wasserstand an bekannten Orten (Zeile 0 im Norden) | Graben [10, 85], Fenne [1, 0], Klinker [21, 0], Warft [4, 0], außerhalb [0, 0] | wie gesetzt | ok |
| 1 Leser und Schreiber | Wasserstand als Byte (−0,98 … 4,0 m NHN) | größter Fehler 0,0100 m | ≤ 0,01 m | ok |
| 2 Gestalt im Gebiet | Gebiet | D:\Daten\HalligHooge\gebiet |  | – |
| 2 Gestalt im Gebiet | Gestaltkarte (gestalt/gestalt.txt und Kacheln) | 46 Kacheln zu 500 m, Zelle 0,50 m | vorhanden | ok |
| 2 Gestalt im Gebiet | Flächen je Art | Fenne 472,3 ha, Salzwiese 17,0 ha, Deich 19,3 ha, Warft 7,5 ha, Deckwerk 1,7 ha, Graben 16,2 ha, Priel 5,7 ha, Fething 10,3 ha, Asphalt 2,4 ha, Klinker 2,5 ha, Schotter 0,3 ha, Beton 0,5 ha, Grasweg 1,3 ha, Holz 0,0 ha |  | – |
| 2 Gestalt im Gebiet | Nur bekannte Arten in der Karte | ja | ja | ok |
| 2 Gestalt im Gebiet | Wasser hat einen Wasserstand | 0 von 1288654 Wasserpunkten ohne | 0 | ok |
| 2 Gestalt im Gebiet | Werte des Schritts (gestalt/pruefung.txt) | vorhanden | vorhanden | ok |
| 2 Gestalt im Gebiet | Aus OSM | 131 Fethinge, 426 Gräben, 117 Priele, 323 Wege; 46 Lahnungen (14308 Pfähle), 45 Steinbuhnen, 9 Anleger und Stege |  | – |
| 2 Gestalt im Gebiet | Prüfstein 7: Mittellinie der Gräben aus OSM (alle 1 m) mit Wasser im Umkreis von 1 m in der Karte | 99,9 % der Punkte | ≥ 95 % [G] | ok |
| 2 Gestalt im Gebiet | Prüfstein 11: jeder Fething (Fläche aus OSM) zu ≥ 90 % Wasser in der Karte | im Mittel 100,0 %; unter 90 %: 0 | 0 unter 90 % | ok |
| 2 Gestalt im Gebiet | Prüfstein 14: Fuß der Pfähle unter dem Watt (Mittel); Pfähle liegen auf den Linien der OSM | 0,30 m | ≤ 0,3 m | ok |
| 2 Gestalt im Gebiet | Körper (bau/koerper.bin) | 146 Körper, 198058 Dreiecke | vorhanden | ok |
| 2 Gestalt im Gebiet | Körper je Art | {Anleger=5, Lahnung=46, Pfähle=46, Steg=4, Steinbuhne=45} |  | – |
| 2 Gestalt im Gebiet | Körper in der Datei wie im Schritt | 198058 Dreiecke, im Schritt 198058 | gleich | ok |
| 2 Gestalt im Gebiet | Körper in plausibler Höhe (Boden ≥ −3 m, oben ≤ 6 m NHN) | alle | alle | ok |

Alles in Ordnung.
