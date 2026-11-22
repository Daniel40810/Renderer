# P9b – SedimentLaufCheck (Sediment im Lauf)

Attest für `com.dan.lauf`, Version 0.5. Stand 17.11.2026 23:23, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

6 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p9b\archiv; D:\Daten\HalligHooge\_p9b\archiv_ruhig |  | – |
| 2 Aus den Archiven | Aufnahme (Sturm) | 150 × 120 Zellen ab Zelle 275, 140 des Gitters, 36 Intervalle (3,0 h), mit Seegang; 1439 Teilschritte; 29 s |  | – |
| 2 Aus den Archiven | Prüfstein 4: Karte gegen Prozessor über 36 Intervalle, mittlere Abweichung relativ zum mittleren Betrag (Sturm) | sed.c Sand 1,9e-06 (im Mittel 5,49e-03 kg/m³, größte Abweichung 3,7e-07), Schlick 5,7e-06 (2,00e-02 kg/m³, 3,8e-07), Bodenänderung 2,0e-05 (6,85e-04 m, 2,8e-07 m); Bilanz der Karte Sand 8,5e-07, Schlick 1,3e-06 | ≤ 1 · 10⁻³ [G]; größte als Auskunft (S3) | ok |
| 2 Aus den Archiven | Aufnahme (ruhiger Tag) | 150 × 120 Zellen ab Zelle 275, 140 des Gitters, 36 Intervalle (3,0 h), mit Seegang; 425 Teilschritte; 3 s |  | – |
| 2 Aus den Archiven | Prüfstein 4: Karte gegen Prozessor über 36 Intervalle, mittlere Abweichung relativ zum mittleren Betrag (ruhiger Tag) | sed.c Sand 9,2e-07 (im Mittel 5,84e-03 kg/m³, größte Abweichung 8,8e-07), Schlick 7,3e-07 (2,60e-03 kg/m³, 4,1e-08), Bodenänderung 3,6e-06 (2,47e-05 m, 1,9e-08 m); Bilanz der Karte Sand 1,1e-06, Schlick 6,3e-06 | ≤ 1 · 10⁻³ [G]; größte als Auskunft (S3) | ok |
| 2 Aus den Archiven | Tiden im Feldvergleich | 2 (ab dem Beginn des Archivs, je 12,42 h); Flut bei steigendem, Ebbe bei fallendem Wasser der Zelle; je Pixel von EasyGSH (100 m) das Mittel der Zellen |  | – |
| 2 Aus den Archiven | Prüfstein 5: größter Flut- und Ebbstrom je Tide gegen EasyGSH 2015 (Median des Jahres), ruhiger Tag | Flutstrom: Rangkorrelation 0,90, Median des Verhältnisses 0,98 (Modell 0,56, EasyGSH 0,56 m/s; 9563 Pixel); Ebbstrom: Rangkorrelation 0,89, Median des Verhältnisses 1,03 (Modell 0,46, EasyGSH 0,44 m/s; 9564 Pixel);  | Rangkorrelation ≥ 0,7; Verhältnis 0,8–1,5 [G] | ok |
| 2 Aus den Archiven | Prüfstein 6: Kornschubspannung des Stroms gegen EasyGSH 2015, ruhiger Tag | Kornschubspannung bei Flut: Rangkorrelation 0,81, Median des Verhältnisses 1,36 (Modell 0,44, EasyGSH 0,32 N/m²; 9587 Pixel); Kornschubspannung bei Ebbe: Rangkorrelation 0,82, Median des Verhältnisses 1,44 (Modell 0,31, EasyGSH 0,21 N/m²; 9588 Pixel);  | Rangkorrelation ≥ 0,6; Verhältnis 0,5–2 [G] | ok |
| 2 Aus den Archiven | Sentinel-2 | S2B_32UMF_20260501_0_L2A, Aufnahme 2026-05-01T10:45:45.422Z; Schwebstoff nach Nechad u. a. (2010) aus B04 (Aufbereitung, Schritt sentinel: Reflexion = Wert · 1.0E-4 + 0.0); Wasser mit Wert in 234421 Zellen des Gitters |  | – |
| 2 Aus den Archiven | Modell zur Tidephase der Szene (Phase der M2 [G]) | 2026-10-30 02:25 UTC, 2026-10-30 14:50 UTC; C_opt = Schlick + 0,05 · Sand; verglichen, wo das Modell mindestens 0,3 m Wasser hat |  | – |
| 2 Aus den Archiven | Prüfstein 7: Trübung des Modells gegen Schwebstoff aus Sentinel-2 (anderer Tag, gleiche Tidephase) | Rangkorrelation -0,48, Median des Verhältnisses 0,71 (Modell 14,8, Sentinel-2 19,7 g/m³; 232345 Zellen) | Rangkorrelation ≥ 0,4; Verhältnis ⅓–3 [G] | **fehlt** |
| 2 Aus den Archiven | Trübung gegen Sentinel-2, nur Zellen mit mindestens 2 m Wasser (F86 A, P9c) | Rangkorrelation -0,36, Median des Verhältnisses 0,94 (Modell 19,2, Sentinel-2 18,9 g/m³; 162788 Zellen) |  | – |
| 2 Aus den Archiven | Prüfstein 8: Rippelhöhe im Archiv höchstens der Größtwert der Formel für das Korn der Zelle | 0 Werte darüber (größter Überschuss 0,0e+00 m) in 73 Ständen | 0 | ok |
| 2 Aus den Archiven | Rippel im Watt bei Niedrigwasser (2026-10-30 09:40 UTC) | 112375 Wattzellen (trocken bei Niedrigwasser, nass bei Hochwasser um 2026-10-30 02:40 UTC); davon 61,8 % mit Rippeln über 5 mm, im Mittel 15,2 mm hoch |  | – |
| 2 Aus den Archiven | Status-Bild | docs\status\P9b_Sediment.png |  | – |
| 2 Aus den Archiven | Rechenzeit | 0,6 min |  | – |

1 Verstoß.
