# P9c – SedimentLaufCheck (Morphologie: P13a_Xaver_Lauf)

Attest für `com.dan.lauf`, Version 0.5. Stand 20.11.2026 03:39, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p13\archiv_xaver |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Lauf | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2013-12-03T06:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)); Aufbau 23,9 s |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Sediment | sediment 0.3 auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400, Kopplung 300 s, mit Seegang, Karte (float); rechnet ab 2013-12-04T18:00:00Z; Boden aus dem Kornfeld des Gebiets; Schlick im Wasser am Anfang und am Rand 20 g/m³ |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: sediment am Bus, Hinweise beim Verdrahten (Sturm) | keine | keine | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: außerhalb der Stände gelesen (Sturm) | 0 | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: NaN oder negative Konzentration (Sturm) | 0 Zellen über 576 Intervalle | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 2: Tiefe aus der Kontinuität (water.q) gegen V(η)/Fläche am Ende der Intervalle (Sturm) | im Mittel 4,39e-06 m; 0,0000 % der Zellen über 5 mm; größte 0,000 m (147865508 Zellen über 576 Intervalle) | im Mittel ≤ 1 mm, 99 % ≤ 5 mm [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 3: Massenbilanz über den Lauf, relativ zum Umsatz (Sturm) | Sand 9,0e-06 (Umsatz 5,99e+05 t), Schlick 2,1e-05 (Umsatz 6,06e+04 t) | ≤ 1 · 10⁻³ [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Bestand am Ende (Sturm) | Sand im Wasser 1,19e+04 t, Boden -3,16e+04 t, über den Rand hinaus +2,05e+04 t (Schwebstoff) und +18,0 t (Geschiebe); Schlick im Wasser 9,73e+03 t, im Boden 1,62e+05 t (Anfang 1,78e+05 t), über den Rand hinaus +1,23e+04 t; Abgleich der Tiefe: Sand -0,00120 t, Schlick -485 t |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Größen (Sturm) | Sand im Wasser bis 17,374 kg/m³, Schlick bis 0,474 kg/m³; Bodenänderung im Mittel 2,44e-02 m, größte 3,207 m; 41,8 Teilschritte je Intervall |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 10: Wasserbilanz von hydro je Stufe mit bewegtem Boden (Sturm) | 1,7e-12, 3,4e-11, 7,3e-11 | < 1 · 10⁻⁵ | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 12: Zellen am Deckel (Anteil der Teilschritte); größte Bodenänderung (Sturm) | 1,134e-05 (76443 Teilschritte von Zellen); 3,207 m | ≤ 0,1 % [G]; ≤ 3 m [G] | **fehlt** |
| 1 Lauf mit Sediment und Archiv, Sturm | Boden (Sturm) | Faktor 1, Rückwirkung an (hydro hat 142498453 Zellstände neu gesetzt), Deckel 0,05; /Δz/: Median 1,00e-02 m, 90 % 0,057 m, 99 % 0,208 m, größte 3,207 m, 131 Zellen über 1 m; wirklich aufgenommen: Sand -3,25e+04 t, Schlick der Mischschicht -1,64e+04 t; lockere Auflage 94,8 t (bis 2,236 kg/m²) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Strom und Seegang zuletzt (Sturm) | über 267504 nasse Zellen: Strom im Mittel 0,4938 m/s, Median 0,4448 m/s, 95 % 1,0504 m/s (Rauheit aus dem Sediment aus); u_bot im Mittel 0,2035 m/s, Median 0,1907 m/s, 95 % 0,4117 m/s (Seegang 28 × 36 auf 500 m und 100 m) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit je Stunde Modellzeit, solange das Sediment rechnet (Sturm) | hydro 29,3 s, Sediment 4,3 s, Seegang 99,6 s, alles 89,8 s (P9b, Lauf B2: hydro 29 s) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 9: Rechenzeit des Sediments gegen hydro, solange es rechnet (Sturm) | Sediment 207 s, hydro 1405 s (0,15) | ≤ ½ hydro | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Zeit (Sturm) | 84,0 h Modellzeit in 125,8 min; Archiv 433 Stände des Wassers, 109 des Seegangs, 433 des Schwebstoffs, 109 des Bodens, 2810 MB |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Aufnahme (Sturm) | D:\Daten\HalligHooge\_p13\archiv_xaver\pruef_sediment: 2 Tiden mit Flut- und Ebbstrom je Zelle; Eingang des Sediments für 150 × 120 Zellen ab Zelle 275, 140 über 36 Intervalle |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Teil 1 gesamt (Sturm) | 126,2 min |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit | 126,2 min |  | – |

1 Verstoß.
