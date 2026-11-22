# P10b – KimmungKarteCheck

Attest für `com.dan.render.air (Kimmung auf der Karte)`, Version 0.1. Stand 18.11.2026 14:51, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

10 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Prüfstein 11: Normalatmosphäre (k = 0,13), der Durchgang auf der Karte lässt das Bild, wie es ist | bitgleich | bitgleich | ok |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Kernel gegen den Prozessor über warmem Wasser (5 K, 3 m/s): Bildpunkte, die um mehr als 1 % abweichen | 0,00 % | ≤ 0,500 % | ok |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Durchgang | ändert 2,3 % der Bildpunkte; größte Abweichung gegen den Prozessor 0,001 |  | – |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Prüfstein 12: Kimm auf der Karte gegen den Grenzwinkel (6,78′; gerade in Zeile 552, mit Kimmung in Zeile 562, Soll 561,8) | 0,204 Zeilen | ≤ 1,00 Zeilen | ok |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Prüfstein 12: Unter der Insel steht ihr Spiegelbild (Zeilen aufrecht, Zeilen gespiegelt) | 7, 4 | je ≥ 3 | ok |
| 2 Nässe im Gelände aus surf.wet (archiv_m1b, 2026-10-18 08:00 UTC) | Prüfstein 9: Gelände mit surf.wet = 1 gegen dasselbe mit water.wetAge = 0, größter Unterschied je Kanal (HDR in float16) | 1,42e-03 | ≤ 2 · 10⁻³ (gleich) | ok |
| 2 Nässe im Gelände aus surf.wet (archiv_m1b, 2026-10-18 08:00 UTC) | surf.wet im Bild | 49359 von 280000 Zellen nicht satt (im Mittel 0,58), davon 34053 unter ½; im Blick von der Westspitze ändern sich 0,1 % der Bildpunkte um mehr als 2 % gegen überall nass |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 325,0°, Neigung 0,0°, Bildwinkel 5,6°, 1920 × 1080) | Luft im Blickfeld | 5 Säulen über Wasser; Fläche -0,83 m NHN, Auge 4,83 m darüber; Sperrhöhe 0,12 m; Grenzwinkel 6,90′ gegen 3,95′ in der Normalatmosphäre des Bilds; Tafel in 0,008 s, größtes δ 18,91′ |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 325,0°, Neigung 0,0°, Bildwinkel 5,6°, 1920 × 1080) | Kernel gegen den Prozessor am gezeichneten Bild: Bildpunkte, die um mehr als 2 % abweichen | 9,65e-05 % | ≤ 1,00 % | ok |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 325,0°, Neigung 0,0°, Bildwinkel 5,6°, 1920 × 1080) | Durchgang | ändert 5,5 % der Bildpunkte um mehr als 2 % |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 325,0°, Neigung 0,0°, Bildwinkel 5,6°, 1920 × 1080) | Kimm in diesem Blick | 22 Spalten mit freiem Wasser bis zur Kimm: gerade in Zeile 552,8 (Soll 552,7), mit Kimmung in Zeile 562,0 (Soll 562,2) |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 325,0°, Neigung 0,0°, Bildwinkel 5,6°, 1920 × 1080) | Kimm über freiem Wasser | auch im Blick nach 145° reicht in keiner Spalte freies Wasser bis zur Kimm |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 325,0°, Neigung 0,0°, Bildwinkel 5,6°, 1920 × 1080) | Status-Bild (Sichtprüfung) | docs\status\P10c_KimmungKarte.png, 1920 × 1620 |  | – |
| 4 Kosten (Prüfstein 14) | Prüfstein 14: Durchgang der Kimmung je Bild (1920 × 1080, Kopie und Kimmung) | 0,459 ms | ≤ 2,00 ms | ok |
| 4 Kosten (Prüfstein 14) | Zeit je Durchgang (Stoppuhr, ms) | löschen 0,53; Gelände, Himmelslicht 0,27; Gelände, Sicht der Sonne 1,64; Gelände 2,62; Wasser, Kopie darunter 0,83; Wasser, Himmelslicht 0,10; Wasser 10,74; Himmel 2,31; Kimmung, Kopie 0,27; Kimmung 0,19; Histogramm 0,26; Ausgabe 0,49;  |  | – |
| 4 Kosten (Prüfstein 14) | Prüfstein 14: die Tafel rechnen | 0,00455 s | ≤ 0,300 s | ok |
| 4 Kosten (Prüfstein 14) | Prüfstein 14: Bilder über wechselnde Stände (120 Bilder über 1 h Szenenzeit, zweite Runde), längstes Bild gegen das Mittel | mit Kimmung Mittel 30,3 ms, Median 25,5, 95 % ≤ 54,5, längstes 80,1 (24 Tafeln neu); ohne Kimmung Mittel 29,2, Median 25,3, 95 % ≤ 49,2, längstes 75,5 | längstes ≤ 2 × Mittel, oder nicht länger als ohne Kimmung (+20 %) [G] | ok |
| 4 Kosten (Prüfstein 14) | Erste Runde (die Stände kommen zum ersten Mal von der Platte) | ohne Kimmung Mittel 29,8 ms, längstes 83,3; mit Kimmung Mittel 30,4 ms, längstes 78,8 |  | – |
| Aufräumen | Speicherkonto | KimmungKarteCheck 0,0 MB von 3,0 GB, Spitze 279,0 MB | 0 belegt | ok |
| Aufräumen | Validierung | nicht verlangt (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
