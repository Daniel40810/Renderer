# P12c – BewuchsCheck

Attest für `com.dan.render.bewuchs`, Version 0.1. Stand 19.11.2026 22:33, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

7 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet | D:\Daten\HalligHooge\gebiet |  | – |
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)) |  | – |
|  | Gehölze im Gebiet (bau/gehoelz.bin) | 554 Gehölze | vorhanden | ok |
| 1 Netze und Laub | Dreiecke je Stufe (alle Arten, zwei Varianten) | Tag 200: nah 119568, mittel 8884, Silhouette 1248; Tag 355: nah 28176, mittel 6024, Silhouette 96 |  | – |
| 1 Netze und Laub | Laub nach dem Tag: im Winter kahl (nur Rinde), im Sommer belaubt | nahe Stufe im Winter 24 % der Dreiecke des Sommers | < 60 %, > 0 | ok |
| 2 Kronen im Bild (Prüfstein 17) | Gehölz außerhalb der gemessenen Kronen | 22,6 % der Bildpunkte mit Gehölz (Lage am Boden unter der Fläche, also auch Schräge der Sicht) |  | – |
| 2 Kronen im Bild (Prüfstein 17) | Blick | 150 m über O 470950 N 6046530 (dichteste Zelle zu 60 m, 1795 m² Krone), Sommer (Tag 200), ohne Wind |  | – |
| 2 Kronen im Bild (Prüfstein 17) | Prüfstein 17 (Bild): Fläche der gemessenen Kronen, die Gehölze im Bild bedecken | 78,8 % von 75122 Bildpunkten | ≥ 70 % [G] | ok |
| 2 Kronen im Bild (Prüfstein 17) | Prüfstein 17 (Bild): Höhe der höchsten Fläche je Gehölz gegen die gemessene (Median) | +0,03 m über 65 Gehölze (10 %: -0,99, 90 %: +7,80) | ± 1 m | ok |
| 2 Kronen im Bild (Prüfstein 17) | Bild | docs\status\P12c_Kronen_oben.png |  | – |
| 3 Halme am Auge (Prüfstein 16) | Halme | 6786 Büschel um das Auge; im Ring 12 … 20 m sind 11 % der Bildpunkte Halme |  | – |
| 3 Halme am Auge (Prüfstein 16) | Prüfstein 16: mittlere Leuchtdichte mit Halmen gegen ohne im Ring 12 … 20 m (nur Himmelslicht) | 1,0310 über 15043 Bildpunkte | 1 ± 0,05 [G] | ok |
| 3 Halme am Auge (Prüfstein 16) | Bild | docs\status\P12c_Halme.png |  | – |
| 4 Bewuchs aus (Prüfstein 9) | Prüfstein 9 (Teil): Bewuchs aus gegen das Bild ohne den Teil | 0 Werte anders (mit Bewuchs: 126320) | 0, und der Bewuchs ändert etwas | ok |
| 4 Bewuchs aus (Prüfstein 9) | Bild | docs\status\P12c_Gehoelz.png |  | – |
| 5 Aufräumen | Speicherkonto nach allen Teilen | BewuchsCheck 0,0 MB von 3,0 GB, Spitze 251,0 MB | 0 belegt | ok |
| 5 Aufräumen | Validierungsschicht | nicht eingeschaltet (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
