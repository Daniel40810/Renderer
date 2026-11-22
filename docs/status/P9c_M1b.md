# P9c – SedimentLaufCheck (Morphologie: P9c_M1b)

Attest für `com.dan.lauf`, Version 0.5. Stand 18.11.2026 02:48, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p9c\archiv_m1b |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Lauf | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-16T00:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Aufbau 3,9 s |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Sediment | sediment 0.3 auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400, Kopplung 300 s, mit Seegang, Karte (float); rechnet ab 2026-10-17T00:00:00Z; Boden aus dem Kornfeld des Gebiets; Schlick im Wasser am Anfang und am Rand 20 g/m³ |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: sediment am Bus, Hinweise beim Verdrahten (Sturm) | keine | keine | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: außerhalb der Stände gelesen (Sturm) | 0 | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: NaN oder negative Konzentration (Sturm) | 0 Zellen über 864 Intervalle | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 2: Tiefe aus der Kontinuität (water.q) gegen V(η)/Fläche am Ende der Intervalle (Sturm) | im Mittel 2,02e-06 m; 0,0000 % der Zellen über 5 mm; größte 0,000 m (161219536 Zellen über 864 Intervalle) | im Mittel ≤ 1 mm, 99 % ≤ 5 mm [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 3: Massenbilanz über den Lauf, relativ zum Umsatz (Sturm) | Sand 5,1e-06 (Umsatz 2,59e+04 t), Schlick 1,4e-05 (Umsatz 5,54e+04 t) | ≤ 1 · 10⁻³ [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Bestand am Ende (Sturm) | Sand im Wasser 5,04 t, Boden -4,60e+05 t, über den Rand hinaus +2,91e+03 t (Schwebstoff) und +1,20e+03 t (Geschiebe); Schlick im Wasser 2,97e+03 t, im Boden 2,38e+05 t (Anfang 1,72e+05 t), über den Rand hinaus +302 t; Abgleich der Tiefe: Sand +7,56e-05 t, Schlick +102 t |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Größen (Sturm) | Sand im Wasser bis 41,887 kg/m³, Schlick bis 0,208 kg/m³; Bodenänderung im Mittel 1,32e-01 m, größte 6,788 m; 23,2 Teilschritte je Intervall |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 10: Wasserbilanz von hydro je Stufe mit bewegtem Boden (Sturm) | 2,6e-12, 6,2e-11, 1,8e-10 | < 1 · 10⁻⁵ | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 12: Zellen am Deckel (Anteil der Teilschritte); größte Bodenänderung (Sturm) | 2,165e-03 (12149467 Teilschritte von Zellen); 6,788 m | ≤ 0,1 % [G]; ≤ 3 m [G] | **fehlt** |
| 1 Lauf mit Sediment und Archiv, Sturm | Boden (Sturm) | Faktor 60, Rückwirkung an (hydro hat 132249580 Zellstände neu gesetzt), Deckel 0,01; /Δz/: Median 3,30e-02 m, 90 % 0,366 m, 99 % 1,299 m, größte 6,788 m, 5029 Zellen über 1 m; wirklich aufgenommen: Sand -4,12e+03 t, Schlick der Mischschicht +1,09e+03 t; lockere Auflage 3,80e+03 t (bis 3,496 kg/m²) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Strom und Seegang zuletzt (Sturm) | über 129062 nasse Zellen: Strom im Mittel 0,0608 m/s, Median 0,0497 m/s, 95 % 0,1406 m/s (Rauheit aus dem Sediment an); u_bot im Mittel 0,0159 m/s, Median 0,0102 m/s, 95 % 0,0532 m/s (Seegang 20 × 18 auf 1000 m und 200 m) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit je Stunde Modellzeit, solange das Sediment rechnet (Sturm) | hydro 31,4 s, Sediment 2,9 s, Seegang 2,2 s, alles 35,1 s (P9b, Lauf B2: hydro 29 s) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 9: Rechenzeit des Sediments gegen hydro, solange es rechnet (Sturm) | Sediment 212 s, hydro 2262 s (0,09) | ≤ ½ hydro | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Zeit (Sturm) | 96,0 h Modellzeit in 56,2 min; Archiv 865 Stände des Wassers, 217 des Seegangs, 865 des Schwebstoffs, 217 des Bodens, 5550 MB |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Aufnahme (Sturm) | D:\Daten\HalligHooge\_p9c\archiv_m1b\pruef_sediment: 5 Tiden mit Flut- und Ebbstrom je Zelle; Eingang des Sediments für 150 × 120 Zellen ab Zelle 275, 140 über 36 Intervalle |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Teil 1 gesamt (Sturm) | 56,3 min |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit | 56,3 min |  | – |

1 Verstoß.
