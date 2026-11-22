# P9c – SedimentLaufCheck (Morphologie: P9c_Feld_M1a)

Attest für `com.dan.lauf`, Version 0.5. Stand 18.11.2026 03:24, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

7 Prüfsteine, 2 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p9c\archiv_m2; D:\Daten\HalligHooge\_p9c\archiv_m1a |  | – |
| 2 Aus den Archiven | Aufnahme (Sturm) | 150 × 120 Zellen ab Zelle 275, 140 des Gitters, 36 Intervalle (3,0 h), mit Seegang; 477 Teilschritte; 8 s |  | – |
| 2 Aus den Archiven | Prüfstein 4: Karte gegen Prozessor über 36 Intervalle, mittlere Abweichung relativ zum mittleren Betrag (Sturm) | sed.c Sand 2,0e-06 (im Mittel 8,50e-04 kg/m³, größte Abweichung 1,6e-07), Schlick 1,9e-06 (1,96e-02 kg/m³, 1,8e-07), Bodenänderung 1,7e-05 (7,81e-05 m, 9,6e-08 m); Bilanz der Karte Sand 5,1e-07, Schlick 5,5e-06 | ≤ 1 · 10⁻³ [G]; größte als Auskunft (S3) | ok |
| 2 Aus den Archiven | Aufnahme (ruhiger Tag) | 150 × 120 Zellen ab Zelle 275, 140 des Gitters, 36 Intervalle (3,0 h), mit Seegang; 446 Teilschritte; 6 s |  | – |
| 2 Aus den Archiven | Prüfstein 4: Karte gegen Prozessor über 36 Intervalle, mittlere Abweichung relativ zum mittleren Betrag (ruhiger Tag) | sed.c Sand 1,2e-06 (im Mittel 1,07e-03 kg/m³, größte Abweichung 9,1e-07), Schlick 9,3e-07 (1,88e-03 kg/m³, 4,4e-08), Bodenänderung 1,7e-05 (7,66e-06 m, 3,1e-08 m); Bilanz der Karte Sand 2,4e-06, Schlick 1,6e-05 | ≤ 1 · 10⁻³ [G]; größte als Auskunft (S3) | ok |
| 2 Aus den Archiven | Tiden im Feldvergleich | 5 (ab dem Beginn des Archivs, je 12,42 h); Flut bei steigendem, Ebbe bei fallendem Wasser der Zelle; je Pixel von EasyGSH (100 m) das Mittel der Zellen |  | – |
| 2 Aus den Archiven | Prüfstein 5: größter Flut- und Ebbstrom je Tide gegen EasyGSH 2015 (Median des Jahres), ruhiger Tag | Flutstrom: Rangkorrelation 0,92, Median des Verhältnisses 0,93 (Modell 0,50, EasyGSH 0,55 m/s; 9801 Pixel); Ebbstrom: Rangkorrelation 0,89, Median des Verhältnisses 0,92 (Modell 0,39, EasyGSH 0,43 m/s; 9801 Pixel);  | Rangkorrelation ≥ 0,7; Verhältnis 0,8–1,5 [G] | ok |
| 2 Aus den Archiven | Prüfstein 6: Kornschubspannung des Stroms gegen EasyGSH 2015, ruhiger Tag | Kornschubspannung bei Flut: Rangkorrelation 0,81, Median des Verhältnisses 1,19 (Modell 0,35, EasyGSH 0,31 N/m²; 9826 Pixel); Kornschubspannung bei Ebbe: Rangkorrelation 0,82, Median des Verhältnisses 1,15 (Modell 0,22, EasyGSH 0,21 N/m²; 9826 Pixel);  | Rangkorrelation ≥ 0,6; Verhältnis 0,5–2 [G] | ok |
| 2 Aus den Archiven | Sentinel-2 | S2B_32UMF_20260501_0_L2A, Aufnahme 2026-05-01T10:45:45.422Z; Schwebstoff nach Nechad u. a. (2010) aus B04 (Aufbereitung, Schritt sentinel: Reflexion = Wert · 1.0E-4 + 0.0); Wasser mit Wert in 234421 Zellen des Gitters |  | – |
| 2 Aus den Archiven | Modell zur Tidephase der Szene (Phase der M2 [G]) | 2026-10-14 01:20 UTC, 2026-10-14 13:45 UTC, 2026-10-15 02:10 UTC, 2026-10-15 14:35 UTC, 2026-10-16 03:00 UTC, 2026-10-16 15:25 UTC; C_opt = Schlick + 0,05 · Sand; verglichen, wo das Modell mindestens 0,3 m Wasser hat |  | – |
| 2 Aus den Archiven | Prüfstein 7: Trübung des Modells gegen Schwebstoff aus Sentinel-2 (anderer Tag, gleiche Tidephase) | Rangkorrelation -0,21, Median des Verhältnisses 0,99 (Modell 20,0, Sentinel-2 19,7 g/m³; 232290 Zellen) | Rangkorrelation ≥ 0,4; Verhältnis ⅓–3 [G] | **fehlt** |
| 2 Aus den Archiven | P9c Prüfstein 16: Trübung gegen Sentinel-2, nur Zellen mit mindestens 2 m Wasser (F86 A) | Rangkorrelation -0,22, Median des Verhältnisses 1,05 (Modell 20,0, Sentinel-2 18,7 g/m³; 156807 Zellen) | Rangkorrelation ≥ 0,4; Verhältnis ⅓–3 [G] | **fehlt** |
| 2 Aus den Archiven | Prüfstein 8: Rippelhöhe im Archiv höchstens der Größtwert der Formel für das Korn der Zelle | 0 Werte darüber (größter Überschuss 0,0e+00 m) in 217 Ständen | 0 | ok |
| 2 Aus den Archiven | Rippel im Watt bei Niedrigwasser (2026-10-15 09:40 UTC) | 118416 Wattzellen (trocken bei Niedrigwasser, nass bei Hochwasser um 2026-10-14 02:20 UTC); davon 91,0 % mit Rippeln über 5 mm, im Mittel 16,2 mm hoch |  | – |
| 2 Aus den Archiven | Status-Bild | docs\status\P9c_Feld_M1a_Sediment.png |  | – |
| 2 Aus den Archiven | Rechenzeit | 0,3 min |  | – |

2 Verstöße.
