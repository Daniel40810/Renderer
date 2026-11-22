# P9c – SedimentLaufCheck (Morphologie: P9c_M1a)

Attest für `com.dan.lauf`, Version 0.5. Stand 18.11.2026 01:52, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p9c\archiv_m1a |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Lauf | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-13T00:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Aufbau 23,3 s |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Sediment | sediment 0.3 auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400, Kopplung 300 s, mit Seegang, Karte (float); rechnet ab 2026-10-14T00:00:00Z; Boden aus dem Kornfeld des Gebiets; Schlick im Wasser am Anfang und am Rand 20 g/m³ |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: sediment am Bus, Hinweise beim Verdrahten (Sturm) | keine | keine | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: außerhalb der Stände gelesen (Sturm) | 0 | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: NaN oder negative Konzentration (Sturm) | 0 Zellen über 864 Intervalle | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 2: Tiefe aus der Kontinuität (water.q) gegen V(η)/Fläche am Ende der Intervalle (Sturm) | im Mittel 3,00e-06 m; 0,0000 % der Zellen über 5 mm; größte 0,000 m (183351978 Zellen über 864 Intervalle) | im Mittel ≤ 1 mm, 99 % ≤ 5 mm [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 3: Massenbilanz über den Lauf, relativ zum Umsatz (Sturm) | Sand 4,1e-06 (Umsatz 1,56e+05 t), Schlick 2,4e-05 (Umsatz 5,80e+04 t) | ≤ 1 · 10⁻³ [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Bestand am Ende (Sturm) | Sand im Wasser 3,55e+03 t, Boden -2,28e+05 t, über den Rand hinaus -1,30e+04 t (Schwebstoff) und -3,40e+03 t (Geschiebe); Schlick im Wasser 6,03e+03 t, im Boden 1,72e+05 t (Anfang 1,78e+05 t), über den Rand hinaus +381 t; Abgleich der Tiefe: Sand -0,000674 t, Schlick +1,83e+03 t |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Größen (Sturm) | Sand im Wasser bis 19,058 kg/m³, Schlick bis 0,315 kg/m³; Bodenänderung im Mittel 1,34e-01 m, größte 4,317 m; 33,1 Teilschritte je Intervall |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 10: Wasserbilanz von hydro je Stufe mit bewegtem Boden (Sturm) | 4,7e-13, 7,8e-11, 1,2e-10 | < 1 · 10⁻⁵ | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 12: Zellen am Deckel (Anteil der Teilschritte); größte Bodenänderung (Sturm) | 3,729e-03 (29826109 Teilschritte von Zellen); 4,317 m | ≤ 0,1 % [G]; ≤ 3 m [G] | **fehlt** |
| 1 Lauf mit Sediment und Archiv, Sturm | Boden (Sturm) | Faktor 60, Rückwirkung an (hydro hat 158488525 Zellstände neu gesetzt), Deckel 0,01; /Δz/: Median 2,93e-02 m, 90 % 0,381 m, 99 % 1,362 m, größte 4,317 m, 5509 Zellen über 1 m; wirklich aufgenommen: Sand +1,28e+04 t, Schlick der Mischschicht -111 t; lockere Auflage 1,80e+03 t (bis 3,019 kg/m²) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Strom und Seegang zuletzt (Sturm) | über 192395 nasse Zellen: Strom im Mittel 0,5070 m/s, Median 0,4838 m/s, 95 % 0,9335 m/s (Rauheit aus dem Sediment an); u_bot im Mittel 0,0559 m/s, Median 0,0367 m/s, 95 % 0,1712 m/s (Seegang 20 × 18 auf 1000 m und 200 m) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit je Stunde Modellzeit, solange das Sediment rechnet (Sturm) | hydro 31,3 s, Sediment 3,6 s, Seegang 2,3 s, alles 35,3 s (P9b, Lauf B2: hydro 29 s) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 9: Rechenzeit des Sediments gegen hydro, solange es rechnet (Sturm) | Sediment 259 s, hydro 2254 s (0,11) | ≤ ½ hydro | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Zeit (Sturm) | 96,0 h Modellzeit in 56,5 min; Archiv 865 Stände des Wassers, 217 des Seegangs, 865 des Schwebstoffs, 217 des Bodens, 5550 MB |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Aufnahme (Sturm) | D:\Daten\HalligHooge\_p9c\archiv_m1a\pruef_sediment: 5 Tiden mit Flut- und Ebbstrom je Zelle; Eingang des Sediments für 150 × 120 Zellen ab Zelle 275, 140 über 36 Intervalle |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Teil 1 gesamt (Sturm) | 56,9 min |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit | 56,9 min |  | – |

1 Verstoß.
