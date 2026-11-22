# P12a – BauCheck

Attest für `com.dan.render.bau`, Version 0.2. Stand 19.11.2026 22:33, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

10 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)) |  | – |
| 1 Ablage | Bauten schreiben und lesen | 2 Häuser, 25 Dreiecke, 3813 Byte | bitgleich | ok |
| 1 Ablage | Haus | 16 × 9 m, Traufe 2,6 m, First 7,96 m über dem Boden, Reet |  | – |
| 2 Himmelslicht | Glieder bei gleichmäßigem Himmel: L₀₀ = L · Y₀₀ · 4π, alle anderen 0 | L₀₀ 14179,6 gegen 14179,6, größtes anderes 2,4e-04 relativ | relativ ≤ 1e-3 [G] | ok |
| 3 Licht auf der Wand (Prüfstein 4) | Südwand, Kanal G: Mittel gegen a/π · (E · cos i + π · L_Himmel) in Java | 1693,0 gegen 1693,5 cd/m² (1,000; 2016 Bildpunkte) | 1 ± 0,03 [G] | ok |
| 3 Licht auf der Wand (Prüfstein 4) | Nordwand im Eigenschatten, Kanal G: nur Himmel und Boden | 260,0 gegen 260,0 cd/m² (1,000; 2016 Bildpunkte) | 1 ± 0,03 [G] | ok |
| 4 Schatten (Prüfstein 5) | Kante des Schattens des Firsts, Sonne 30° aus Süden | 13,69 m nördlich der Firstlinie, Geometrie 13,79 m (Abweichung 0,10 m; Texel 0,12 m, Bildpunkt 0,09 m) | ± 1 Texel + 1 Bildpunkt | ok |
| 4 Schatten (Prüfstein 5) | Boden in der Sonne und im Schatten, Kanal G | Sonne 1505,0 gegen 1505,9, Schatten 359,8 gegen 360,0 cd/m² | ± 2 % / ± 5 % [G] | ok |
| 4 Schatten (Prüfstein 5) | Bild | docs\status\P12a_Haus_oben.png |  | – |
| 5 Fensterlicht | Abends (18:30, Sonne −12°): Fenster mit Licht | Anteil 0,65, 1587 Bildpunkte über 5 cd/m² | Anteil 0,65 [G], Bildpunkte > 50 | ok |
| 5 Fensterlicht | Bild | docs\status\P12a_Haus_abend.png |  | – |
| 5 Fensterlicht | Bild | docs\status\P12a_Haus_tag.png |  | – |
| 5 Fensterlicht | Am Tag kein Fensterlicht | Anteil 0,00 | 0 | ok |
| 5 Fensterlicht | Bild | docs\status\P12a_Haus_abendsonne.png |  | – |
| 5 Fensterlicht | Schalter aus: nichts im Bild, kein Schatten abgelegt | leer | leer | ok |
| 7 Aufräumen | Speicherkonto nach allen Teilen | BauCheck 0,0 MB von 512,0 MB, Spitze 51,7 MB | 0 belegt | ok |
| 7 Aufräumen | Validierungsschicht | nicht eingeschaltet (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
