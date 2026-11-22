# P8c – SeegangCheck (Seegang im Lauf und im Bild, mit Rückwirkung auf hydro)

Attest für `com.dan.lauf`, Version 0.3. Stand 16.11.2026 23:38, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

18 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p8c\archiv; D:\Daten\HalligHooge\_p8c\archiv_ruhig |  | – |
| 1 Lauf mit Seegang und Archiv, Sturm | Lauf | Lauf 0.3 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-23T00:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Aufbau 23,2 s |  | – |
| 1 Lauf mit Seegang und Archiv, Sturm | Prüfstein 1: waves am Bus (Sturm) | waves 0.1 (Waves 0.2, Karte (float)) | angemeldet und verdrahtet | ok |
| 1 Lauf mit Seegang und Archiv, Sturm | Seegang | Gitter G2w 148 × 172, G3w 100 × 80; 28 × 36 Stützstellen; rechnet ab 2026-10-24T18:00:00Z (12 h vor dem Ablegen); Kopplung 1200 s |  | – |
| 1 Lauf mit Seegang und Archiv, Sturm | Hinweise beim Verdrahten | keine |  | – |
| 1 Lauf mit Seegang und Archiv, Sturm | Prüfstein 6: Spektrum am Ende endlich und nicht negativ (Sturm) | 0 Werte | 0 | ok |
| 1 Lauf mit Seegang und Archiv, Sturm | Lauf (Sturm): außerhalb der Stände gelesen | 0 | 0 | ok |
| 1 Lauf mit Seegang und Archiv, Sturm | Zeit | 72,0 h Modellzeit in 70,8 min (ohne Bild); Archiv 217 Stände des Wassers, 55 des Seegangs, 780 MB |  | – |
| 1 Lauf mit Seegang und Archiv, Sturm | Prüfstein 7: Rechenzeit des Seegangs je 20 min Szenenzeit, solange er rechnet (28 × 36) | 30,2 s (Seegang 2717 s, hydro 651 s, 4,18) | ≤ 48 s (Zeitbudget, Zeitraffer 25) | ok |
| 1 Lauf mit Seegang und Archiv, Sturm | Teil 1 gesamt (Sturm) | 71,2 min |  | – |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Lauf | Lauf 0.3 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-28T00:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Aufbau 4,1 s |  | – |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Prüfstein 1: waves am Bus (ruhiger Tag) | waves 0.1 (Waves 0.2, Karte (float)) | angemeldet und verdrahtet | ok |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Seegang | Gitter G2w 148 × 172, G3w 100 × 80; 28 × 36 Stützstellen; rechnet ab 2026-10-29T12:00:00Z (12 h vor dem Ablegen); Kopplung 1200 s |  | – |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Hinweise beim Verdrahten | keine |  | – |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Prüfstein 6: Spektrum am Ende endlich und nicht negativ (ruhiger Tag) | 0 Werte | 0 | ok |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Lauf (ruhiger Tag): außerhalb der Stände gelesen | 0 | 0 | ok |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Zeit | 72,0 h Modellzeit in 61,6 min (ohne Bild); Archiv 289 Stände des Wassers, 73 des Seegangs, 1039 MB |  | – |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Rechenzeit (ruhiger Tag) | Seegang 2165 s, hydro 757 s (2,86) |  | – |
| 1 Lauf mit Seegang und Archiv, ruhiger Tag | Teil 1 gesamt (ruhiger Tag) | 61,6 min |  | – |
| 2 Aus dem Archiv | Archiv | Archiv archiv: Wasser G2 370 × 430, G3 500 × 400, 217 Stände; Seegang G2w 148 × 172, G3w 100 × 80, 55 Stände (2026-10-25 06:00 UTC bis 2026-10-26 00:00 UTC) |  | – |
| 2 Aus dem Archiv | Prüfstein 2: Hs am Westrand von G2w gegen ERA5, Mittel des Betrags | 1,54 % | ≤ 5,00 % | ok |
| 2 Aus dem Archiv | Prüfstein 2: größte Abweichung; Werte | 4,8 %; 9460 Zellen × Stände (Tiefe > 5 m, Hs > 0,05 m, 2026-10-25T06:00:00Z bis 2026-10-26T00:00:00Z) |  | – |
| 2 Aus dem Archiv | Prüfstein 3: Hs in den Randzellen von G3w gegen G2w, Mittel des Betrags | 4,50 % | ≤ 5,00 % | ok |
| 2 Aus dem Archiv | Prüfstein 3: größte Abweichung; Werte | 44,4 %; 6331 Zellen × Stände (Tiefe > 5 m wie Prüfstein 2, Hs > 0,1 m) |  | – |
| 2 Aus dem Archiv | Prüfstein 4: Hs gegen ERA5 im Inneren von G2w, Mittel des Betrags | 5,82 % | ≤ 25,0 % | ok |
| 2 Aus dem Archiv | Prüfstein 4: Tp gegen ERA5 (pp1d), Mittel des Betrags | 6,61 % | ≤ 25,0 % | ok |
| 2 Aus dem Archiv | Prüfstein 4: Werte (Modell/ERA5, alle 3 h) | 38 Punkte × Stunden; 06:00 8,00° O 54,50° N (16 m): Hs 3,34/3,08 m, Tp 7,6/8,1 s; 06:00 8,00° O 55,00° N (16 m): Hs 3,35/3,18 m, Tp 8,2/9,3 s; 09:00 8,00° O 54,50° N (18 m): Hs 4,16/3,83 m, Tp 8,3/8,9 s; 09:00 8,00° O 55,00° N (17 m): Hs 4,22/3,84 m, Tp 8,5/9,2 s; 12:00 8,00° O 54,50° N (18 m): Hs 5,07/4,67 m, Tp 9,2/10,0 s; 12:00 8,00° O 55,00° N (18 m): Hs 5,08/4,63 m, Tp 9,5/10,2 s; 15:00 8,00° O 54,50° N (18 m): Hs 5,61/5,30 m, Tp 10,2/11,2 s; 15:00 8,00° O 55,00° N (18 m): Hs 5,34/5,28 m, Tp 10,3/11,1 s; 18:00 8,00° O 54,50° N (18 m): Hs 5,78/5,50 m, Tp 11,0/11,5 s; 18:00 8,00° O 55,00° N (18 m): Hs 5,50/5,53 m, Tp 11,1/11,6 s; 21:00 8,00° O 54,50° N (19 m): Hs 5,75/5,42 m, Tp 11,0/11,6 s; 21:00 8,00° O 55,00° N (18 m): Hs 5,77/5,63 m, Tp 11,1/12,0 s; 00:00 8,00° O 54,50° N (19 m): Hs 5,39/5,10 m, Tp 10,7/11,6 s; 00:00 8,00° O 55,00° N (19 m): Hs 5,49/5,36 m, Tp 11,1/12,0 s;  |  | – |
| 2 Aus dem Archiv | Prüfstein 5: Hs/h ≤ 0,8 in den nassen Zellen von G3w über alle Stände | 99,921 % von 375982 (größtes Hs/h 3,41) | ≥ 99,9 % | ok |
| 2 Aus dem Archiv | Prüfstein 5: über 0,8; davon bei fallendem Wasser; davon unter 0,3 m Tiefe | 296; 208; 246 |  | – |
| 2 Aus dem Archiv | Prüfstein 5 mit der Tiefe, die der Seegang sah (water.h eine Kopplung vor dem Stand, 1200 s) | 99,781 % von 372587 ≤ 0,8 (größtes Hs/h 1,03) |  | – |
| 2 Aus dem Archiv | Prüfstein 5 über beide Archive (P8c, W7) | 99,958 % von 740173 (ruhiger Tag allein 99,997 % von 364191, größtes Hs/h 1,07) | ≥ 99,9 % | ok |
| 2 Aus dem Archiv | EasyGSH 2015 | D:\Daten\HalligHooge\gezeiten\easygsh_kennwerte\2015: 720 × 850 Pixel zu 100 m ab O 428000, N 6096000 (Ecke Nordwest) |  | – |
| 2 Aus dem Archiv | Feldvergleich G2w (Auskunft): größte Hs des Sturms zwischen 95-%-Wert und Maximum 2015 | 14,6 % von 12527 Zellen (darunter 0,5 %, darüber 84,9 %; 6165 nasse Zellen ohne Wert in EasyGSH) |  | – |
| 2 Aus dem Archiv | Prüfstein 7 (P8c): größte Hs des Sturms in G3w zwischen 95-%-Wert und Maximum von EasyGSH 2015 | 93,6 % von 1128 Zellen (darunter 0,4 %, darüber 6,0 %; 6850 nasse Zellen ohne Wert in EasyGSH) | ≥ 90 % [G] | ok |
| 2 Aus dem Archiv | Status-Bild Feldvergleich | docs\status\P8c_Feld.png |  | – |
| 2 Aus dem Archiv | Prüfstein 6: kein NaN im Seegang der Archive | 0 (Sturm), 0 (ruhiger Tag) | 0 | ok |
| 2 Aus dem Archiv | Höchster mittlerer Seegang auf G3w | 2026-10-26 00:00 UTC: 0,86 m |  | – |
| 2 Aus dem Archiv | Status-Bild | docs\status\P8c_Seegang.png |  | – |
| 2 Aus dem Archiv | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| 3 Bild | Zeit der Ansichten | 2026-10-25 12:50 UTC (höchster Seegang auf G3w bei Tag, 09–15 UTC, und 10 min danach) |  | – |
| 3 Bild | Prüfstein 8: Stufe 2 ohne Seegang gegen Stufe 1 (Westspitze) | 1 von 255 | ≤ 1 | ok |
| 3 Bild | Prüfstein 9: Neigungsvarianz der langen Wellen, Karte gegen Prozessor, relativ zu max(aufgelöst, mss) (1000 Orte) | 2,19e-06 | ≤ 0,000100 | ok |
| 3 Bild | Prüfstein 9: dieselbe relativ zum aufgelösten Teil allein; Werte am Ort (Hs, Tp, Richtung, mss, diss) | 2,7e-05; größte relative Abweichung 7,9e-04 |  | – |
| 3 Bild | Prüfstein 10: Bilder/s aus dem Archiv mit Seegang, 1920 × 1080 (je Bild gewartet) | 38,2 | ≥ 30 | ok |
| 3 Bild | Prüfstein 12: Status-Bild (Sichtprüfung) | docs\status\P8c_Bild.png |  | – |
| 3 Bild | Rechenzeit | 133,0 min |  | – |

Alles in Ordnung.
