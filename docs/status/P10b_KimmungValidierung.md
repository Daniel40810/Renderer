# P10b – KimmungKarteCheck

Attest für `com.dan.render.air (Kimmung auf der Karte)`, Version 0.1. Stand 18.11.2026 12:36, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

12 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Prüfstein 11: Normalatmosphäre (k = 0,13), der Durchgang auf der Karte lässt das Bild, wie es ist | bitgleich | bitgleich | ok |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Kernel gegen den Prozessor über warmem Wasser (5 K, 3 m/s): Bildpunkte, die um mehr als 1 % abweichen | 0,00 % | ≤ 0,500 % | ok |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Durchgang | ändert 2,3 % der Bildpunkte; größte Abweichung gegen den Prozessor 0,001 |  | – |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Prüfstein 12: Kimm auf der Karte gegen den Grenzwinkel (6,78′; gerade in Zeile 552, mit Kimmung in Zeile 562, Soll 561,8) | 0,204 Zeilen | ≤ 1,00 Zeilen | ok |
| 1 Kernel gegen den Prozessor (ebene See mit Insel in 8 km, Auge 4 m, Bildwinkel 5,6°) | Prüfstein 12: Unter der Insel steht ihr Spiegelbild (Zeilen aufrecht, Zeilen gespiegelt) | 7, 4 | je ≥ 3 | ok |
| 2 Nässe im Gelände aus surf.wet (archiv_m1b, 2026-10-18 08:00 UTC) | Prüfstein 9: Gelände mit surf.wet = 1 gegen dasselbe mit water.wetAge = 0, größter Unterschied je Kanal (HDR in float16) | 9,76e-04 | ≤ 2 · 10⁻³ (gleich) | ok |
| 2 Nässe im Gelände aus surf.wet (archiv_m1b, 2026-10-18 08:00 UTC) | surf.wet im Bild | 50370 von 280000 Zellen nicht satt (im Mittel 0,39), davon 35658 unter ½; im Blick von der Westspitze ändern sich 0,1 % der Bildpunkte um mehr als 2 % gegen überall nass |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Luft im Blickfeld | 281 Säulen über Wasser; Fläche -0,60 m NHN, Auge 4,60 m darüber; Sperrhöhe 0,12 m; Grenzwinkel 6,69′ gegen 3,85′ in der Normalatmosphäre des Bilds; Tafel in 0,004 s, größtes δ 18,72′ |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Kernel gegen den Prozessor am gezeichneten Bild: Bildpunkte, die um mehr als 2 % abweichen | 0,00 % | ≤ 1,00 % | ok |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Durchgang | ändert 1,5 % der Bildpunkte um mehr als 2 % |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Kimm in diesem Blick | in keiner Spalte reicht freies Wasser bis zur Kimm |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Prüfstein 12 im Gebiet: Kimm über freiem Wasser (Blick nach 270°) gegen den Grenzwinkel 6,91′, Mittel über 60 Spalten (1,6 Zeilen je ′) | 0,141 Zeilen | ≤ 1,00 Zeilen | ok |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Kimm über freiem Wasser | gerade in Zeile 275,0 (Soll 275,4), mit Kimmung in Zeile 280,0 (Soll 280,1): 5,0 Zeilen = 3,11′ tiefer; in 60 von 60 Spalten auf 1,5 Zeilen am Soll |  | – |
| 3 Kimmung im Gebiet (Auge O 462600,0, N 6046900,0, 4,0 m NHN, Richtung 90,0°, Neigung -0,0°, Bildwinkel 5,6°, 960 × 540) | Status-Bild (Sichtprüfung) | docs\status\P10b_KimmungValidierung.png, 960 × 810 |  | – |
| 4 Kosten (Prüfstein 14) | Prüfstein 14: Durchgang der Kimmung je Bild (960 × 540, Kopie und Kimmung) | 0,155 ms | ≤ 2,00 ms | ok |
| 4 Kosten (Prüfstein 14) | Zeit je Durchgang (Stoppuhr, ms) | löschen 0,17; Gelände, Himmelslicht 0,36; Gelände, Sicht der Sonne 2,26; Gelände 2,39; Wasser, Kopie darunter 0,21; Wasser, Himmelslicht 0,11; Wasser 3,39; Himmel 0,75; Kimmung, Kopie 0,12; Histogramm 0,07; Ausgabe 0,17;  |  | – |
| 4 Kosten (Prüfstein 14) | Prüfstein 14: die Tafel rechnen | 0,00488 s | ≤ 0,300 s | ok |
| 4 Kosten (Prüfstein 14) | Prüfstein 14: Bilder über wechselnde Stände (120 Bilder über 1 h Szenenzeit), längstes Bild gegen das Mittel | mit Kimmung Mittel 26,2 ms, Median 23,0, längstes 82,9 (23 Tafeln neu); ohne Kimmung Mittel 20,7, Median 17,0, längstes 64,1 | längstes ≤ 2 × Mittel, oder nicht länger als ohne Kimmung (+20 %) [G] | **fehlt** |
| Aufräumen | Speicherkonto | KimmungKarteCheck 0,0 MB von 3,0 GB, Spitze 225,6 MB | 0 belegt | ok |
| Aufräumen | Prüfstein 15: Validierungsschicht 1.4.363 über Kernel und Bild der Kimmung | 0 Fehler | 0 Fehler | ok |

1 Verstoß.
