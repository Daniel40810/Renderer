# P10c – SandfegenKarteCheck

Attest für `com.dan.render.terrain (Sandfegen auf der Karte)`, Version 0.2. Stand 18.11.2026 15:00, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

6 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| 1 Deckung des Schleiers (Prüfstein 17) | Schleier | Korn 0,17 mm, Dichte 2650 kg/m³, Korngeschwindigkeit 2,0 m/s [G]: senkrechte optische Dicke 1,665 je kg/(m s) Fluss; Boden am Ort 1,50 m NHN |  | – |
| 1 Deckung des Schleiers (Prüfstein 17) | Prüfstein 17: Deckung im schrägen Blick (20 m über dem Boden, −30°, q = 100 g/(m s) ohne Streifen) gegen 1 − exp(−τ / sin β), Median der Abweichung über 518400 Bildpunkte | 0,332 % | ≤ 10,0 % | ok |
| 1 Deckung des Schleiers (Prüfstein 17) | Deckung | im Mittel 0,335, Soll 0,336; 90 % der Bildpunkte auf 0,9 % (der Boden ist nicht eben: β gilt zur Fläche am Ort) |  | – |
| 1 Deckung des Schleiers (Prüfstein 17) | Mit Streifen | Deckung im Mittel 0,307 gegen 0,335 ohne Streifen (Verhältnis 0,92) |  | – |
| 1 Deckung des Schleiers (Prüfstein 17) | Prüfstein 17: Deckung von oben (300 m über dem Boden, q = 10 g/(m s)), Mittel über 442886 Bildpunkte (Soll 1,65 %) | 1,78 % | ≤ 2,00 % | ok |
| 2 Kosten (Prüfstein 17) | Prüfstein 17: Gelände je Bild mit Sandfegen überall gegen ohne (960 × 540, Ansicht in Augenhöhe: 1,55 gegen 1,73 ms) | -0,175 ms | ≤ 0,500 ms | ok |
| 3 Im Gebiet (archiv_m1b, 2026-10-17 16:00 UTC, Auge O 465850,0, N 6042890,0, 3,2 m NHN, Richtung 20,0°, Neigung -3,0°, Bildwinkel 55,0°, 960 × 540) | Flugsand im Archiv zu dieser Zeit | Fluss in 8671 Zellen, größter 10,0 g/(m s), am Ort der Ansicht 9,4 g/(m s) |  | – |
| 3 Im Gebiet (archiv_m1b, 2026-10-17 16:00 UTC, Auge O 465850,0, N 6042890,0, 3,2 m NHN, Richtung 20,0°, Neigung -3,0°, Bildwinkel 55,0°, 960 × 540) | Das Sandfegen ändert das Bild der Ansicht | 39,2 % der Bildpunkte um mehr als 2 %, 6,3 % um mehr als 10 %; größte Änderung 36 % | mehr als 0 Bildpunkte | ok |
| 3 Im Gebiet (archiv_m1b, 2026-10-17 16:00 UTC, Auge O 465850,0, N 6042890,0, 3,2 m NHN, Richtung 20,0°, Neigung -3,0°, Bildwinkel 55,0°, 960 × 540) | Status-Bild (Sichtprüfung) | docs\status\P10c_SandfegenValidierung.png, 960 × 540 |  | – |
| Aufräumen | Speicherkonto | SandfegenKarteCheck 0,0 MB von 3,0 GB, Spitze 229,9 MB | 0 belegt | ok |
| Aufräumen | Validierungsschicht 1.4.363 über das Gelände mit Flugsand | 0 Fehler | 0 Fehler | ok |

Alles in Ordnung.
