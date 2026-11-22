# P9c – MorphoCheck (Rückwirkung, Faktor, Auflage, Spirale, Rauheit)

Attest für `com.dan.lauf`, Version 0.5. Stand 18.11.2026 00:55, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

12 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Boden verschieben erhält das Wasser | Prüfstein 1: Volumen der Zelle aus der verschobenen Tabelle gegen vorher; η = η₀ + Δ; trockene Zellen auf ihrem Grund | größter Fehler 5,8e-15 (relativ bzw. m), trocken 2,2e-16 m (93 von 432 Zellen trocken), drei zufällige Verschiebungen bis ±0,5 m | ≤ 1 · 10⁻¹² | ok |
| 2 Gleichmäßige Hebung | Prüfstein 2: alle Zellen und der Rand +0,5 m, Tide von ±0,5 m über 6 h: η − 0,5 m gegen den Lauf ohne Hebung | größter Unterschied 4,4e-15 m; nasse Zellen am Ende 1090 und 1090 von 1200 | ≤ 1 · 10⁻⁹ m | ok |
| 3, 4 Hügel im Gerinne, gekoppelt | Fall | Gerinne 160 × 3 Zellen zu 10 m, Durchfluss 2,40 m²/s, am Hügel h = 3,098 m, u = 0,775 m/s, Fr² = 0,020; Hügel 50 mm hoch, σ = 80 m; Sand 0,2 mm, nur Geschiebe; Geschiebe 0,0170 kg/(s m), c = 1,367e-05 m/s |  | – |
| 3, 4 Hügel im Gerinne, gekoppelt | Prüfstein 3: Wanderung des Hügels mit Rückwirkung über 200 h (Faktor 1) gegen c · t; Wasserbilanz | Schwerpunkt des Hügels (Sohle minus Sohle des Laufs ohne Hügel) +10,56 m gegen +9,84 m (+7,3 %); Wasserbilanz 1,6e-12; 260 s Rechnung | ±10 % [G]; < 1 · 10⁻⁵ | ok |
| 3, 4 Hügel im Gerinne, gekoppelt | Prüfstein 4: Sohle mit Faktor 10 über ein Zehntel der Zeit gegen Faktor 1 | im Mittel 7,26e-07 m (0,00 % der Hügelhöhe); Schwerpunkt +10,56 m gegen +10,56 m | ≤ 2 % der Hügelhöhe [G] | ok |
| 3, 4 Hügel im Gerinne, gekoppelt | Prüfstein 4: Massenbilanz des Sands (wirklich aufgenommen gegen den Fluss über den Rand), Faktor 1 und 10 | 6,1e-15 und 3,1e-15 | ≤ 1 · 10⁻⁶ | ok |
| 5 Lockere Auflage | Prüfstein 5: Absetzen in ruhendem Wasser über 1 h (2 m, 0,1 kg/m³): Konzentration gegen c₀ e^(−w t/h); Auflage gegen (1 − D) des Abgesetzten; Masse | 0,04068 gegen 0,04066 kg/m³ (+0,06 %); Auflage 0,10678 gegen 0,10678 kg/m² (+0,00 %), Mischschicht +0,01186 kg/m²; Masse 1,2e-14 | ±2 % [G]; ≤ 1 · 10⁻⁶ | ok |
| 5 Lockere Auflage | Prüfstein 5: Wiederaufnahme der Auflage über 1 h im Strom (τ über τ_a und τ_d): Auflage gegen m₀ e^(−M₁ (τ/τ_a − 1) t); Masse | τ = 0,290 N/m² (τ_a = 0,10); Auflage 0,10077 gegen 0,10080 kg/m² (-0,03 %); Schlick im Wasser 0,0496 kg/m³; Masse 0,0e+00 | ±2 % [G]; ≤ 1 · 10⁻⁶ | ok |
| 6 Spirale | Prüfstein 6: water.spiral einer Kreisströmung (links herum) gegen h /u/ / R, ab 10 Zellen vom Mittelpunkt | größte Abweichung 1,00e-06 in 5936 Zellen; Vorzeichen positiv | ±2 % [G] | ok |
| 6 Spirale | Prüfstein 6: Ablenkung des Geschiebes bei h/R = 0,008 (Strom nach Osten, Krümmung nach links): Winkel gegen atan(α_I h/R) | 5,461° gegen 5,461° nach links; α_I = 11,95; Betrag 0,0175 kg/(s m) (ohne Spirale 0,0175) | ±2 % [G] | ok |
| 7 Rauheit aus dem Sediment | Prüfstein 7: Σ Δs/n der Kanten aus n = 0,020 gegen Länge / n | größte Abweichung 0,0e+00; 1123 von 1123 Kanten geändert (Packung: n = 0,025) | ±1 % (hier Rundung) | ok |
| 7 Rauheit aus dem Sediment | Strickler | z0 = 1,7 mm (Rippel 15 mm hoch, 130 mm lang) gibt n = 0,0234; z0 = d50/12 für 0,2 mm gibt n = 0,0108 (im Lauf wenigstens 0,015) |  | – |
| 7 Rauheit aus dem Sediment | Prüfstein 7: Reibungsgefälle im ebenen Gerinne (Spiegelgefälle mal 1 − Fr²) gegen n² u² / h^(4/3) | 5,6168e-05 gegen 5,6162e-05 (+0,01 %); h = 3,047 m, u = 0,788 m/s | ±2 % [G] | ok |
| 8 Karte gegen Prozessor am Bus, mit Rückwirkung | Prozessor | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-13T00:00:00Z, Kopplung 300 s, Anlauf 1.0 h, Mittel +0.00 m, Prozessor (double, 16 Kerne); Bilanz Sand 1,5e-16, Schlick 1,7e-15; hydro hat 146224 Zellstände neu gesetzt; 82 s |  | – |
| 8 Karte gegen Prozessor am Bus, mit Rückwirkung | Karte | Lauf 0.5 über D:\Daten\HalligHooge\gebiet: Stufen G0 / G1 / G2 G3, mit Antrieb (ERA5, Windschub nach Charnock mit α aus ERA5), Start 2026-10-13T00:00:00Z, Kopplung 300 s, Anlauf 1.0 h, Mittel +0.00 m, AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); Bilanz Sand 2,2e-07, Schlick 4,6e-07; hydro hat 133777 Zellstände neu gesetzt; 23 s |  | – |
| 8 Karte gegen Prozessor am Bus, mit Rückwirkung | Prüfstein 8: Karte gegen Prozessor nach 4 h am Bus (Fenster 670,990,150,120, Faktor 50), Summe der Unterschiede durch Summe der Beträge | sed.c (kg/m³) 6,3e-04 (Mittel des Betrags 0,000140); Bodenänderung (m) 3,8e-05 (Mittel des Betrags 3,01e-05); lockere Auflage (kg/m²) 3,8e-05 (Mittel des Betrags 0,00275) | ≤ 1 · 10⁻³ [G] | ok |
| 8 Karte gegen Prozessor am Bus, mit Rückwirkung | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)); sediment 0.3 auf G3 UTM 32, O 467400–470400, N 6044800–6047200, Zelle 20 m, 150 × 120, Kopplung 300 s, ohne Seegang, Karte (float) |  | – |
| 8 Karte gegen Prozessor am Bus, mit Rückwirkung | Rechenzeit | 374 s |  | – |

Alles in Ordnung.
