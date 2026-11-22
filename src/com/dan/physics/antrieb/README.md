# com.dan.physics.antrieb – Wind, Luftdruck und Windschub aus ERA5

**Stand: P10b (18.11.2026), Version 0.3.** Seit P5e schreibt der Antrieb wahlweise auch den Windschub (F34, siehe unten), seit P10b mit `mitLuft` auch Luft, Strahlung und die Temperatur des Meers (F90 A, siehe unten). Grundlage ist das Werkbuch HalligHooge, Teil A, Abschnitte 7.2 und 12.6 (F28 A: ERA5 aus dem Climate Data Store). Später kommt die Wetterregie für erfundene Stürme dazu, als zweiter Schreiber derselben Felder in einem anderen Lauf.

## Schicht

```
com.dan.physics.antrieb    core + java.*    Modul am Bus
```

Die Daten kommen aus dem Gebiet: `antrieb/era5.txt` (`core.gitter.Wetterfeld`, Aufbereitung Schritt `antrieb`).

## Felder

| Feld | Einheit | Inhalt |
|---|---|---|
| `air.u10`, `air.v10` | m/s | Wind in 10 m nach Osten und Norden |
| `air.p` | Pa | Luftdruck auf Meereshöhe |
| `air.taux`, `air.tauy` | N/m² | Windschub auf das Wasser nach Osten und Norden (seit P5e, nur mit `schub` außer `WU`) |

Geschrieben auf dem Raster von ERA5 als geographischem Gitter (`Antrieb.gitter`: je Punkt eine Zelle, der Punkt in der Mitte; 117 × 89 Zellen zu 0,25°). Wer auf einem anderen Gitter liest, bekommt die Werte vom Bus bilinear umgerechnet – zwischen den Punkten dasselbe wie `Gradraster.gewichte`. In der Zeit linear zwischen den Stunden von ERA5. Außerhalb der Datei ist es ein Fehler.

**Anlauf.** Wind und Abweichung des Luftdrucks von 101 325 Pa fahren über den Anlauf (Vorgabe 3 h [G]) mit r(t) = ½ (1 − cos(π t / Anlauf)) an, wie die Tide.

**Windschub (`Antrieb.schub`, P5e, F34).**

| `Schub` | Woher | Was hydro tut |
|---|---|---|
| `WU` (Vorgabe des Moduls, bis P5d) | – | rechnet τ = ρ_L C_d \|U\| U mit C_d nach Wu (1982) selbst aus dem Wind |
| `ERA5` | `iews`, `inss` aus ERA5, mal r(t) | nimmt `air.taux`, `air.tauy` |
| `CHARNOCK` (Vorgabe im Lauf seit F34 B) | τ = ρ_L C_d \|U\| U auf r(t) · U, C_d = (κ / ln(10 m / z₀))², z₀ = α u*²/g + 0,11 ν/u*, α je Punkt und Stunde aus ERA5 (`charnock`) | nimmt `air.taux`, `air.tauy` |

κ = 0,40, ν = 1,5 · 10⁻⁵ m²/s, ρ_L = 1,25 kg/m³ wie in hydro. Gerechnet wird an den Punkten von ERA5; der Bus rechnet bilinear auf die Gitter um, hydro dreht in UTM. `cdCharnock` löst die Gleichung für u* durch Einsetzen (höchstens 50 Runden, 10⁻¹² relativ). Über Land gibt ERA5 α = 0,018 (der feste Wert des IFS ohne Seegang). Braucht ein Wetterfeld mit `iews`, `inss`, `charnock` (Aufbereitung mit `antrieb.schub` im Rezept, Quelle `era5_cds_schub` im Kartenwerkzeug); sonst lehnt `anmelden` ab.

## Prüfung

In `com.dan.lauf.demo.LaufCheck` (Teil 3): Luftdruck und Wind in hydro auf G0 (geographisch) und G2 (UTM, gedreht) gegen ERA5 an den Zellmitten (≤ 0,01 Pa, ≤ 10⁻⁵ m/s [G]). Seit P5e Teil 9: der Windschub in hydro auf G0 und G2 gegen eine eigene Rechnung (Charnock dort mit Bisektion), ≤ 10⁻⁸ N/m² [G], je Art; Gegenprobe mit einem Wetterfeld ohne die Größen.

## Quellen

- Charnock, H. (1955): Wind stress on a water surface. Q. J. R. Meteorol. Soc. 81.
- Özkan, F. N., Verlaan, M., Muis, S., Zijl, F. (2025): Sensitivity of global storm surge modelling to sea surface drag. Ocean Dynamics.
- Hersbach, H. u. a. (2020): The ERA5 global reanalysis. Q. J. R. Meteorol. Soc.


## Seit P10b: Luft, Strahlung, Temperatur des Meers (Version 0.3, F90 A)

`new Antrieb(wetter, start).mitLuft(Wetterfeld.lies(gebiet, Wetterfeld.LUFT))` schreibt dazu auf dem Raster der Datei
`antrieb/luft` (Gitter `ERA5luft`; Aufbereitung Schritt `luft`):

| Feld | Einheit | Inhalt |
|---|---|---|
| `air.t2` | K | Temperatur in 2 m (`t2`) |
| `air.q2` | kg/kg | Feuchte in 2 m aus dem Taupunkt (`d2`) und dem Luftdruck: q = ε e / (p − (1 − ε) e), e der Sättigungsdampfdruck beim Taupunkt nach IAPWS-IF97 (dieselbe Gleichung wie in `physics.atmos.Luft`) |
| `air.sw`, `air.lw` | W/m² | Sonnenstrahlung und Gegenstrahlung auf die Fläche (`ssrd`, `strd`): Mittel der Stunde, hier zur Mitte der Stunde genommen und dazwischen linear |
| `water.sst` | K | Temperatur des Meers (`sst`), über Land aus den Nachbarn über See |

Ohne Anlauf. Geprüft in `com.dan.lauf.demo.AtmosLaufCheck` (Prüfstein 2 von P10b).

## Seit P10c (Antrieb 0.4)

Mit `mitLuft` schreibt der Antrieb dazu `air.rain` (kg/(m² s)), wenn die Datei der Luft `tp` hält: `tp` ist die Summe der
Stunde vor der Zeit (m); die Rate gilt in der Mitte dieser Stunde, dazwischen linear.

## Seit 0.5 (Steuerhaus Y2, Werkbuch HalligHooge 153): Regler

`windFaktorZiel`, `windDrehungZiel` (aus jedem Faden setzbar): Der Wind aus ERA5 wird mit dem Faktor gestreckt und um den Winkel im Uhrzeigersinn gedreht (`Antrieb.drehe`; der Wind „dreht rechts“), an den Punkten von ERA5, vor dem Ablegen – der Bus rechnet bilinear um, das ist linear und vertauscht mit der Drehung. Der Windschub folgt: Charnock rechnet auf den geänderten Wind, ERA5 (`iews`, `inss`) wird mit dem Quadrat des Faktors gedreht. Luftdruck, Luft und Strahlung bleiben. Die Ziele fährt der Antrieb in der Modellzeit an (`rampeFaktor` 2 je Stunde, `rampeDrehung` 180° je Stunde [G]); `windFaktor()`, `windDrehung()` geben die wirksamen Werte. Ohne Ziel bitgleich.
