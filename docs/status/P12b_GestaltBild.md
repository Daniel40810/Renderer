# P12b – GestaltBildCheck

Attest für `com.dan.render.terrain (Gestalt)`, Version 0.3. Stand 19.11.2026 21:11, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

14 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gebiet | D:\Daten\HalligHooge\gebiet |  | – |
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.11.2 (AMD proprietary shader compiler)) |  | – |
|  | Gestaltkarte im Gebiet geladen | 46 Kacheln | vorhanden | ok |
| 1 Spiegelung auf einem Fething (Prüfstein 12) | Fething | dichteste Zelle zu 40 m bei O 469980 N 6047540 (1600 m² Wasser) |  | – |
| 1 Spiegelung auf einem Fething (Prüfstein 12) | Himmelslicht (licht.comp) | E = 12566 / 12566 / 12566 lx (π · L = 12566) |  | – |
| 1 Spiegelung auf einem Fething (Prüfstein 12) | Prüfstein 12: Leuchtdichte auf dem Fething gegen F · L_Himmel + (1 − F) · ρ/π · E_Himmel (Java), je Kanal | 26940 Bildpunkte; Verhältnis im Mittel 0,9996, 0,9990 … 1,0000 | ≥ 500 Bildpunkte, 1 ± 0,03 [G] | ok |
| 1 Spiegelung auf einem Fething (Prüfstein 12) | Bild | docs\status\P12b_Fething.png |  | – |
| 1 Spiegelung auf einem Fething (Prüfstein 12) | Grund für das Wasserbild (F134 A): Alpha · L mit Gestalt gegen L ohne Gestalt auf dem Fething; ohne Wasser Alpha 1 | 0,9985 … 1,0006; Alpha 1 an 40105 Bildpunkten ohne Wasser, anders an 0 | 1 ± 0,01 (Halbfloat); 0 anders | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Asphalt – Albedo mit Muster gegen ohne, im Mittel | 0,9989 über 34528 Bildpunkte (O 471300 N 6047884, 12 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Klinker – Albedo mit Muster gegen ohne, im Mittel | 0,9990 über 26463 Bildpunkte (O 470940 N 6046652, 12 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Bild | docs\status\P12b_Muster_Klinker.png |  | – |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Schotter – Albedo mit Muster gegen ohne, im Mittel | 0,9937 über 12167 Bildpunkte (O 471764 N 6045884, 12 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Beton – Albedo mit Muster gegen ohne, im Mittel | 0,9992 über 16444 Bildpunkte (O 470372 N 6047572, 12 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Grasweg – Albedo mit Muster gegen ohne, im Mittel | 1,0003 über 12236 Bildpunkte (O 469348 N 6047036, 12 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Holz – Albedo mit Muster gegen ohne, im Mittel | 1,0000 über 3726 Bildpunkte (O 470276 N 6047628, 12 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Deckwerk – Albedo mit Muster gegen ohne, im Mittel | 0,9889 über 10220 Bildpunkte (O 468285 N 6047505, 40 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Bild | docs\status\P12b_Muster_Deckwerk.png |  | – |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Fenne – Albedo mit Muster gegen ohne, im Mittel | 0,9989 über 129600 Bildpunkte (O 470505 N 6045975, 40 m von oben) | 1 ± 0,02 | ok |
| 2 Muster im Mittel (Prüfstein 13) | Prüfstein 13: Salzwiese – Albedo mit Muster gegen ohne, im Mittel | 0,9923 über 124425 Bildpunkte (O 472395 N 6046455, 40 m von oben) | 1 ± 0,02 | ok |
| 3 Außerhalb der Gestalt unverändert (Prüfstein 9) | Prüfstein 9: Gestalt an gegen aus – wo keine Gestalt getroffen wird, bitgleich | 547 Bildpunkte anders, davon 0 ohne Gestalt (ohne Gestalt 95 % des Bilds) | 0, und die Gestalt ändert etwas | ok |
| 3 Außerhalb der Gestalt unverändert (Prüfstein 9) | Hülle der Gestalt | O 468000 … 475000, N 6043000 … 6048500 |  | – |
| 3 Außerhalb der Gestalt unverändert (Prüfstein 9) | Bild | docs\status\P12b_Rand.png |  | – |
| 4 Aufräumen | Speicherkonto nach allen Teilen | GestaltBildCheck 0,0 MB von 2,0 GB, Spitze 155,2 MB | 0 belegt | ok |
| 4 Aufräumen | Validierungsschicht | nicht eingeschaltet (-Dcore.gpu.validierung=true) |  | – |

Alles in Ordnung.
