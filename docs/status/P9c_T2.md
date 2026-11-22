# P9c – SedimentLaufCheck (Morphologie: P9c_T2)

Attest für `com.dan.lauf`, Version 0.5. Stand 18.11.2026 08:12, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p9c\archiv_t2 |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Lauf | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-24T00:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Aufbau 3,5 s |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Sediment | sediment 0.3 auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400, Kopplung 300 s, mit Seegang, Karte (float); rechnet ab 2026-10-25T06:00:00Z; Boden aus dem Kornfeld des Gebiets; Schlick im Wasser am Anfang und am Rand 20 g/m³ |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: sediment am Bus, Hinweise beim Verdrahten (Sturm) | keine | keine | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: außerhalb der Stände gelesen (Sturm) | 0 | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: NaN oder negative Konzentration (Sturm) | 0 Zellen über 216 Intervalle | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 2: Tiefe aus der Kontinuität (water.q) gegen V(η)/Fläche am Ende der Intervalle (Sturm) | im Mittel 4,38e-06 m; 0,0000 % der Zellen über 5 mm; größte 0,000 m (53972460 Zellen über 216 Intervalle) | im Mittel ≤ 1 mm, 99 % ≤ 5 mm [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 3: Massenbilanz über den Lauf, relativ zum Umsatz (Sturm) | Sand 6,1e-06 (Umsatz 1,60e+05 t), Schlick 3,5e-05 (Umsatz 1,60e+04 t) | ≤ 1 · 10⁻³ [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Bestand am Ende (Sturm) | Sand im Wasser 7,91e+03 t, Boden +3,95e+04 t, über den Rand hinaus -4,54e+04 t (Schwebstoff) und -5,67e+03 t (Geschiebe); Schlick im Wasser 1,46e+04 t, im Boden 1,74e+05 t (Anfang 1,78e+05 t), über den Rand hinaus -4,25e+03 t; Abgleich der Tiefe: Sand -0,00242 t, Schlick -436 t |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Größen (Sturm) | Sand im Wasser bis 10,725 kg/m³, Schlick bis 0,197 kg/m³; Bodenänderung im Mittel 5,27e-03 m, größte 1,423 m; 43,1 Teilschritte je Intervall |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 10: Wasserbilanz von hydro je Stufe mit bewegtem Boden (Sturm) | 6,9e-13, 6,3e-11, 1,3e-10 | < 1 · 10⁻⁵ | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 12: Zellen am Deckel (Anteil der Teilschritte); größte Bodenänderung (Sturm) | 1,224e-04 (319188 Teilschritte von Zellen); 1,423 m | ≤ 0,1 % [G]; ≤ 3 m [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Boden (Sturm) | Faktor 1, Rückwirkung an (hydro hat 52085737 Zellstände neu gesetzt), Deckel 0,01; /Δz/: Median 1,33e-03 m, 90 % 0,011 m, 99 % 0,062 m, größte 1,423 m, 20 Zellen über 1 m; wirklich aufgenommen: Sand +4,32e+04 t, Schlick der Mischschicht -4,61e+03 t; lockere Auflage 55,8 t (bis 0,932 kg/m²) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Strom und Seegang zuletzt (Sturm) | über 279242 nasse Zellen: Strom im Mittel 0,1827 m/s, Median 0,1712 m/s, 95 % 0,3632 m/s (Rauheit aus dem Sediment an); u_bot im Mittel 0,2754 m/s, Median 0,2357 m/s, 95 % 0,5787 m/s (Seegang 20 × 18 auf 1000 m und 200 m) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit je Stunde Modellzeit, solange das Sediment rechnet (Sturm) | hydro 30,9 s, Sediment 4,5 s, Seegang 2,7 s, alles 32,9 s (P9b, Lauf B2: hydro 29 s) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 9: Rechenzeit des Sediments gegen hydro, solange es rechnet (Sturm) | Sediment 81 s, hydro 556 s (0,14) | ≤ ½ hydro | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Zeit (Sturm) | 48,0 h Modellzeit in 26,3 min; Archiv 217 Stände des Wassers, 55 des Seegangs, 217 des Schwebstoffs, 55 des Bodens, 1395 MB |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Aufnahme (Sturm) | D:\Daten\HalligHooge\_p9c\archiv_t2\pruef_sediment: 1 Tiden mit Flut- und Ebbstrom je Zelle; Eingang des Sediments für 150 × 120 Zellen ab Zelle 275, 140 über 36 Intervalle |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Teil 1 gesamt (Sturm) | 26,4 min |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit | 26,4 min |  | – |

Alles in Ordnung.
