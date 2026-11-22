# P10c – AeolianLaufCheck

Attest für `com.dan.lauf (Nachlauf atmos und aeolian)`, Version 0.2, Atmos 0.3, Aeolian 0.1. Stand 18.11.2026 14:44, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

11 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Katalog | 48 Felder | 48 | ok |
| Lauf 1 (ohne Regen, in den Prüfordner) | Lauf | archiv_m1b: 2026-10-17T00:00:00Z bis 2026-10-20T00:00:00Z, 865 Stände alle 300 s auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400; Vorlauf ab 2026-10-16T00:00:00Z; atmos ohne Regen; Hinweise der Verdrahtung: keine |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Prüfstein 12: Massenbilanz über 72 h (Auftrag − Abtrag + Ausflug, bezogen auf den Abtrag) | 1,64e-12 | ≤ 1,00e-09 | ok |
| Lauf 1 (ohne Regen, in den Prüfordner) | Prüfstein 12: dasselbe aus der Höhe (Σ Δz ρs (1 − θ) A + Ausflug) | 2,71e-15 | ≤ 1,00e-09 | ok |
| Lauf 1 (ohne Regen, in den Prüfordner) | Prüfstein 13: Abtrag auf Zellen, aus denen der Wind nie aufnehmen durfte (Wasser, fest, Schlick, über 4 % Wasser) | 0 von 263146 solchen Zellen, tiefste Änderung 0,0e+00 m | keine | ok |
| Lauf 1 (ohne Regen, in den Prüfordner) | Prüfstein 13: Fluss in Zellen, deren u* nicht über der trockenen Schwelle liegt | 0 Zellschritte | keine | ok |
| Lauf 1 (ohne Regen, in den Prüfordner) | Prüfstein 15: Größe der Bodenänderung | größter Auftrag 61,5 mm bei O 465950, N 6044030 (zuletzt Wasser, 1,01 m NHN); größter Abtrag 64,5 mm bei O 466530, N 6044050 (1,59 m NHN); Zellen mit /Δz/ ≥ 1 mm: 4952, ≥ 1 cm: 349 |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Massen | abgetragen 7915 t, aufgetragen 7286 t, davon ins Wasser 4333 t; über den Rand des Fensters 629 t; größter Fluss 26,3 g/(m s); Schritte mit Fluss 773 von 864, mit gemittelter Richtung 56; Wind in der Mitte 6,2–10,3 m/s |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Klasse 0,50–1,00 m NHN | 6995 Zellen, aufnehmen konnte der Wind aus 0; Stunden im Mittel 0,0; Aufnahme im Mittel über alle 0,0 kg/m; weitergegeben im Mittel 13,1 kg/m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Klasse 1,00–1,25 m NHN | 4938 Zellen, aufnehmen konnte der Wind aus 1; Stunden im Mittel 0,0; Aufnahme im Mittel über alle 0,0 kg/m; weitergegeben im Mittel 33,6 kg/m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Klasse 1,25–1,50 m NHN | 10777 Zellen, aufnehmen konnte der Wind aus 7004; Stunden im Mittel 7,1; Aufnahme im Mittel über alle 132,2 kg/m; weitergegeben im Mittel 271,0 kg/m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Klasse 1,50–1,75 m NHN | 4785 Zellen, aufnehmen konnte der Wind aus 4734; Stunden im Mittel 29,6; Aufnahme im Mittel über alle 779,4 kg/m; weitergegeben im Mittel 987,1 kg/m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Klasse 1,75–2,00 m NHN | 1514 Zellen, aufnehmen konnte der Wind aus 1452; Stunden im Mittel 47,0; Aufnahme im Mittel über alle 1493,2 kg/m; weitergegeben im Mittel 1557,7 kg/m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Klasse 2,00–∞ m NHN | 549 Zellen, aufnehmen konnte der Wind aus 501; Stunden im Mittel 49,0; Aufnahme im Mittel über alle 2050,8 kg/m; weitergegeben im Mittel 2083,5 kg/m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Lose Zellen über +0,5 m NHN | 29558 |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Ablage | D:\Daten\HalligHooge\_p9c\archiv_m1b_ohneRegen: 865 Stände atmos, 217 Stände Flugsand, 2559 MB; water.h am Bus gegen das Archiv: größter Unterschied 0,0e+00 m |  | – |
| Lauf 1 (ohne Regen, in den Prüfordner) | Rechenzeit | 132 s, davon atmos 52 s, aeolian 14,5 s (16,8 ms je Schritt) |  | – |
| 11 atmos im neuen Nachlauf gegen das Archiv | Prüfstein 11: atmos ohne Regen gegen archiv_m1b (Nachlauf 0.1 aus dem Archiv, Luft und Strahlung aus ERA5, Vorlauf 24.0 h, Säulen 200.0 m) | surf.wet.f16 gleich (484 MB); surf.T.f32 gleich (968 MB); zeiten.f64 gleich (0 MB); air.n.f32 gleich (620 MB);  | bitgleich | ok |
| Lauf 2 (mit Regen, ins Archiv) | Lauf | archiv_m1b: 2026-10-17T00:00:00Z bis 2026-10-20T00:00:00Z, 865 Stände alle 300 s auf G3 UTM 32, O 462000–476000, N 6042000–6050000, Zelle 20 m, 700 × 400; Vorlauf ab 2026-10-16T00:00:00Z; atmos mit Regen; Hinweise der Verdrahtung: keine |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Prüfstein 12: Massenbilanz über 72 h (Auftrag − Abtrag + Ausflug, bezogen auf den Abtrag) | 4,32e-13 | ≤ 1,00e-09 | ok |
| Lauf 2 (mit Regen, ins Archiv) | Prüfstein 12: dasselbe aus der Höhe (Σ Δz ρs (1 − θ) A + Ausflug) | 3,96e-16 | ≤ 1,00e-09 | ok |
| Lauf 2 (mit Regen, ins Archiv) | Prüfstein 13: Abtrag auf Zellen, aus denen der Wind nie aufnehmen durfte (Wasser, fest, Schlick, über 4 % Wasser) | 0 von 263220 solchen Zellen, tiefste Änderung 0,0e+00 m | keine | ok |
| Lauf 2 (mit Regen, ins Archiv) | Prüfstein 13: Fluss in Zellen, deren u* nicht über der trockenen Schwelle liegt | 0 Zellschritte | keine | ok |
| Lauf 2 (mit Regen, ins Archiv) | Prüfstein 15: Größe der Bodenänderung | größter Auftrag 14,4 mm bei O 465190, N 6045970 (zuletzt Wasser, 0,98 m NHN); größter Abtrag 28,5 mm bei O 466530, N 6044050 (1,59 m NHN); Zellen mit /Δz/ ≥ 1 mm: 1143, ≥ 1 cm: 32 |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Massen | abgetragen 1690 t, aufgetragen 1504 t, davon ins Wasser 634 t; über den Rand des Fensters 185 t; größter Fluss 21,0 g/(m s); Schritte mit Fluss 255 von 864, mit gemittelter Richtung 56; Wind in der Mitte 6,2–10,3 m/s |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Klasse 0,50–1,00 m NHN | 6995 Zellen, aufnehmen konnte der Wind aus 0; Stunden im Mittel 0,0; Aufnahme im Mittel über alle 0,0 kg/m; weitergegeben im Mittel 2,0 kg/m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Klasse 1,00–1,25 m NHN | 4938 Zellen, aufnehmen konnte der Wind aus 0; Stunden im Mittel 0,0; Aufnahme im Mittel über alle 0,0 kg/m; weitergegeben im Mittel 6,8 kg/m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Klasse 1,25–1,50 m NHN | 10777 Zellen, aufnehmen konnte der Wind aus 3118; Stunden im Mittel 1,5; Aufnahme im Mittel über alle 21,9 kg/m; weitergegeben im Mittel 50,6 kg/m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Klasse 1,50–1,75 m NHN | 4785 Zellen, aufnehmen konnte der Wind aus 4690; Stunden im Mittel 12,0; Aufnahme im Mittel über alle 254,4 kg/m; weitergegeben im Mittel 343,1 kg/m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Klasse 1,75–2,00 m NHN | 1514 Zellen, aufnehmen konnte der Wind aus 1441; Stunden im Mittel 13,8; Aufnahme im Mittel über alle 388,0 kg/m; weitergegeben im Mittel 407,6 kg/m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Klasse 2,00–∞ m NHN | 549 Zellen, aufnehmen konnte der Wind aus 459; Stunden im Mittel 14,2; Aufnahme im Mittel über alle 526,2 kg/m; weitergegeben im Mittel 535,7 kg/m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Lose Zellen über +0,5 m NHN | 29558 |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Ablage | D:\Daten\HalligHooge\_p9c\archiv_m1b: 865 Stände atmos, 217 Stände Flugsand, 2559 MB; water.h am Bus gegen das Archiv: größter Unterschied 0,0e+00 m |  | – |
| Lauf 2 (mit Regen, ins Archiv) | Rechenzeit | 101 s, davon atmos 52 s, aeolian 13,9 s (16,1 ms je Schritt) |  | – |
| Regen (F97 A): Lauf 2 gegen Lauf 1 | surf.wet mit Regen weicht erst von der Rechnung ohne Regen ab, wenn Regen gefallen ist | verschieden ab 17.10. 00:00; erster Regen 16.10. 06:55 (der Vorlauf beginnt 16.10. 00:00); Regen im Mittel der Säulen 2,32 mm im Vorlauf, 2,94 mm danach | nicht vor dem ersten Regen | ok |
| Regen (F97 A): Lauf 2 gegen Lauf 1 | Flugsand mit Regen gegen ohne | abgetragen 1690 t gegen 7915 t (-78,7 %); Zellen, aus denen der Wind aufnahm, 9708 gegen 13692; größter Auftrag 14,4 mm gegen 61,5 mm |  | – |
| Ablauf | Prüfstein 14 | der Vergleich mit der unabhängigen Rechnung (Python) steht außerhalb; dafür die Zeilen „Klasse“ von Lauf 1 |  | – |
| Ablauf | Dauer | 234 s |  | – |

Alles in Ordnung.
