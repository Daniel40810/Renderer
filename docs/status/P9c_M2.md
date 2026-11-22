# P9c – SedimentLaufCheck (Morphologie: P9c_M2)

Attest für `com.dan.lauf`, Version 0.5. Stand 18.11.2026 03:24, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

8 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet, Archive | D:\Daten\HalligHooge\gebiet; D:\Daten\HalligHooge\_p9c\archiv_m2 |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Lauf | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-24T00:00:00Z, Kopplung 300 s, Anlauf 3.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Aufbau 4,4 s |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Sediment | sediment 0.3 auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400, Kopplung 300 s, mit Seegang, Karte (float); rechnet ab 2026-10-25T06:00:00Z; Boden aus dem Kornfeld des Gebiets; Schlick im Wasser am Anfang und am Rand 20 g/m³ |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: sediment am Bus, Hinweise beim Verdrahten (Sturm) | keine | keine | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: außerhalb der Stände gelesen (Sturm) | 0 | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 1: NaN oder negative Konzentration (Sturm) | 0 Zellen über 360 Intervalle | 0 | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 2: Tiefe aus der Kontinuität (water.q) gegen V(η)/Fläche am Ende der Intervalle (Sturm) | im Mittel 3,95e-06 m; 0,0000 % der Zellen über 5 mm; größte 0,000 m (92723077 Zellen über 360 Intervalle) | im Mittel ≤ 1 mm, 99 % ≤ 5 mm [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 3: Massenbilanz über den Lauf, relativ zum Umsatz (Sturm) | Sand 5,9e-06 (Umsatz 2,12e+05 t), Schlick 4,2e-05 (Umsatz 2,85e+04 t) | ≤ 1 · 10⁻³ [G] | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Bestand am Ende (Sturm) | Sand im Wasser 1,88e+03 t, Boden -5,19e+04 t, über den Rand hinaus -4,43e+04 t (Schwebstoff) und -5,97e+03 t (Geschiebe); Schlick im Wasser 1,28e+04 t, im Boden 2,18e+05 t (Anfang 2,38e+05 t), über den Rand hinaus +1,57e+03 t; Abgleich der Tiefe: Sand -0,00180 t, Schlick -429 t |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Größen (Sturm) | Sand im Wasser bis 12,200 kg/m³, Schlick bis 0,527 kg/m³; Bodenänderung im Mittel 1,38e-01 m, größte 6,857 m; 39,6 Teilschritte je Intervall |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 10: Wasserbilanz von hydro je Stufe mit bewegtem Boden (Sturm) | 7,7e-13, 1,1e-10, 9,1e-12 | < 1 · 10⁻⁵ | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | P9c Prüfstein 12: Zellen am Deckel (Anteil der Teilschritte); größte Bodenänderung (Sturm) | 5,978e-04 (2383369 Teilschritte von Zellen); 6,857 m | ≤ 0,1 % [G]; ≤ 3 m [G] | **fehlt** |
| 1 Lauf mit Sediment und Archiv, Sturm | Boden (Sturm) | Faktor 4, Rückwirkung an (hydro hat 88021456 Zellstände neu gesetzt), Deckel 0,01; /Δz/: Median 3,63e-02 m, 90 % 0,381 m, 99 % 1,332 m, größte 6,857 m, 5458 Zellen über 1 m; wirklich aufgenommen: Sand +4,84e+04 t, Schlick der Mischschicht -4,86e+03 t; lockere Auflage 150 t (bis 3,786 kg/m²) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Strom und Seegang zuletzt (Sturm) | über 278951 nasse Zellen: Strom im Mittel 0,1524 m/s, Median 0,1443 m/s, 95 % 0,2961 m/s (Rauheit aus dem Sediment an); u_bot im Mittel 0,2037 m/s, Median 0,1648 m/s, 95 % 0,4806 m/s (Seegang 20 × 18 auf 1000 m und 200 m) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit je Stunde Modellzeit, solange das Sediment rechnet (Sturm) | hydro 32,6 s, Sediment 4,4 s, Seegang 2,6 s, alles 35,8 s (P9b, Lauf B2: hydro 29 s) |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Prüfstein 9: Rechenzeit des Sediments gegen hydro, solange es rechnet (Sturm) | Sediment 133 s, hydro 979 s (0,14) | ≤ ½ hydro | ok |
| 1 Lauf mit Sediment und Archiv, Sturm | Zeit (Sturm) | 60,0 h Modellzeit in 35,8 min; Archiv 361 Stände des Wassers, 91 des Seegangs, 361 des Schwebstoffs, 91 des Bodens, 2318 MB |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Aufnahme (Sturm) | D:\Daten\HalligHooge\_p9c\archiv_m2\pruef_sediment: 2 Tiden mit Flut- und Ebbstrom je Zelle; Eingang des Sediments für 150 × 120 Zellen ab Zelle 275, 140 über 36 Intervalle |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Teil 1 gesamt (Sturm) | 35,9 min |  | – |
| 1 Lauf mit Sediment und Archiv, Sturm | Rechenzeit | 35,9 min |  | – |

1 Verstoß.
