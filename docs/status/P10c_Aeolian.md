# P10c – AeolianCheck

Attest für `com.dan.physics.aeolian`, Version 0.1. Stand 18.11.2026 14:41, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

25 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Schwelle für trockenen Sand (Bagnold, A = 0,1) | u*t gegen die Handrechnung, größte Abweichung über 0,25, 0,175 und 0,44 mm | 0,00 % | ≤ 0,100 % | ok |
| 1 Schwelle für trockenen Sand (Bagnold, A = 0,1) | Schwellen (mit z0 = 1 mm) | 0,250 mm: 0,2280 m/s (5,2 m/s in 10 m); 0,175 mm: 0,1907 m/s (4,4 m/s in 10 m); 0,440 mm: 0,3024 m/s (7,0 m/s in 10 m);  |  | – |
| 1 Schwelle für trockenen Sand (Bagnold, A = 0,1) | u* aus dem Wind und zurück | 0,00 % | ≤ 1,00e-10 % | ok |
| 2 Schwelle für feuchten Sand (Belly 1964) | Faktor bei 1, 2, 4 Gewichtsprozent gegen die Handrechnung | 0,00 % | ≤ 0,100 % | ok |
| 2 Schwelle für feuchten Sand (Belly 1964) | Kein Einfluss, wo Bellys Formel unter 1 fiele (unter 0,046 %) | 0 %: 1,000; 0,023 %: 1,000; 0,047 %: 1,0026 | 1; 1; > 1 | ok |
| 2 Schwelle für feuchten Sand (Belly 1964) | Gewichtsprozent bei Sättigung 1 und 40 % Poren gegen 25,157 | 0,00 % | ≤ 0,100 % | ok |
| 2 Schwelle für feuchten Sand (Belly 1964) | Bei 4,5 % nimmt der Wind (12 m/s) nichts auf, bei 3,5 % schon | Art 2, dann Art 1 mit Δz -2,67e-04 m | nichts; Abtrag | ok |
| 2 Schwelle für feuchten Sand (Belly 1964) | Auskunft: Schwelle für 0,44 mm gegen Bellys zweite Form (17 log₁₀ w + 51 cm/s) | 0,5 %: 49,0 cm/s (Belly 45,9); 1,0 %: 54,4 cm/s (Belly 51,0); 2,0 %: 59,9 cm/s (Belly 56,1); 4,0 %: 65,4 cm/s (Belly 61,2);  |  | – |
| 3 Gesättigter Fluss (Kawamura; zum Vergleich Lettau und Lettau) | Kawamura bei u* = 0,3, 0,4, 0,6 m/s (0,25 mm) gegen die Handrechnung | 0,00 % | ≤ 0,100 % | ok |
| 3 Gesättigter Fluss (Kawamura; zum Vergleich Lettau und Lettau) | Lettau und Lettau ebenso | 0,00 % | ≤ 0,100 % | ok |
| 3 Gesättigter Fluss (Kawamura; zum Vergleich Lettau und Lettau) | An und unter der Schwelle | 0 | genau 0 | ok |
| 3 Gesättigter Fluss (Kawamura; zum Vergleich Lettau und Lettau) | Weit über der Schwelle (u* = 10⁴ u*t) gegen C ρ u*³ / g | 0,0100 % | ≤ 0,100 % | ok |
| 3 Gesättigter Fluss (Kawamura; zum Vergleich Lettau und Lettau) | Auskunft: Fluss im Messbereich von Sherman u. a. (2013; dort bis rund 30 g/(m s) gemessen, die Formel rund dreifach zu hoch) | u* 0,31 m/s, 0,17 mm: 10,7 g/(m s), mit C = 0,70: 2,7; u* 0,50 m/s, 0,25 mm: 51,1 g/(m s), mit C = 0,70: 12,9; u* 0,71 m/s, 0,44 mm: 148,0 g/(m s), mit C = 0,70: 37,3;  |  | – |
| 4 Gleichförmiges trockenes Feld | Änderung der Höhe im Inneren nach einer Stunde bei 9 m/s, fünf Richtungen | 0,00 m | ≤ 1,00e-12 m | ok |
| 4 Gleichförmiges trockenes Feld | Abtrag der Zelle am Rand im Luv gegen q Δt / (ρs (1 − θ) Δx) | 2,39e-14 % | ≤ 0,100 % | ok |
| 4 Gleichförmiges trockenes Feld | Rand im Luv nach einer Stunde | nach 90°: Rand -2,652 mm (Soll -2,652); nach 0°: Rand -2,652 mm (Soll -2,652); nach 270°: Rand -2,652 mm (Soll -2,652); nach 45°: Rand -1,875 mm (Soll -1,875); nach 200°: Rand -0,907 mm (Soll -0,907);  |  | – |
| 5 Kante zu Wasser und zu nassem Sand | Auftrag in der ersten Wasserzelle gegen q Δt / (ρs (1 − θ) Δx) (0,221 mm in 300 s) | 1,23e-14 % | ≤ 0,100 % | ok |
| 5 Kante zu Wasser und zu nassem Sand | Dahinter nichts; alles liegt im Wasser | größte Änderung 0,0e+00 m; 702,8 kg im Wasser | genau 0 | ok |
| 5 Kante zu Wasser und zu nassem Sand | Fluss über nassem Sand gegen q exp(−x / 100 m), 30 Zellen | 1,38e-13 % | ≤ 0,100 % | ok |
| 5 Kante zu Wasser und zu nassem Sand | Auftrag dort gegen die Rechnung | 3,80e-13 % | ≤ 0,100 % | ok |
| 5 Kante zu Wasser und zu nassem Sand | Über nassem Sand | nach 100 m noch 36,8 % des Flusses, nach 600 m 0,25 % |  | – |
| 6 Massenbilanz | Auftrag − Abtrag + Ausflug, bezogen auf den Abtrag: Nässe, Wasser, fester Boden und Korn zufällig, acht Richtungen, je 20 Schritte | 6,72e-15 | ≤ 1,00e-12 | ok |
| 6 Massenbilanz | Dasselbe aus der Höhe: Σ Δz ρs (1 − θ) A + Ausflug | 1,52e-16 | ≤ 1,00e-12 | ok |
| 6 Massenbilanz | Bewegt | 6494 t abgetragen, 293 t über den Rand; Schritte mit gemittelter Richtung 0 |  | – |
| 6 Massenbilanz | Wechselt das Vorzeichen einer Komponente im Feld, gilt die mittlere Richtung; die Bilanz schließt | 20 von 20 Schritten gemittelt, Rest 1,2e-16 | 20; ≤ 1e-12 | ok |
| 7 Richtungstreue | Bewegte Masse: größte Abweichung vom Mittel der acht Richtungen (runder Fleck von 600 m auf nasser Ebene) | 7,07 % | ≤ 1,00 % | **fehlt** |
| 7 Richtungstreue | Richtung vom Schwerpunkt des Abtrags zum Schwerpunkt des Auftrags gegen den Wind | 1,25e-12 ° | ≤ 3,00 ° | ok |
| 7 Richtungstreue | Je Richtung | 0°: 4,4 t, Auftrag nach 360,0°; 45°: 5,0 t, Auftrag nach 45,0°; 90°: 4,4 t, Auftrag nach 90,0°; 135°: 5,0 t, Auftrag nach 135,0°; 180°: 4,4 t, Auftrag nach 180,0°; 225°: 5,0 t, Auftrag nach 225,0°; 270°: 4,4 t, Auftrag nach 270,0°; 315°: 5,0 t, Auftrag nach 315,0°;  |  | – |
| 8 Fester Boden und Schlick | Abtrag auf festem Boden und auf Schlick (Wind 11 m/s, eine Stunde) | 0,0e+00 m | genau 0 | ok |
| 8 Fester Boden und Schlick | Fluss über festem Boden gegen q exp(−x / 20 m), 10 Zellen | 6,21e-14 % | ≤ 0,100 % | ok |
| 8 Fester Boden und Schlick | Fester Boden ohne Sand im Luv | 0,0e+00 | kein Fluss, keine Änderung | ok |
| 9 Rechenzeit | Ein Kopplungsintervall für 280 000 Zellen (ein Faden) | 0,0144 s | ≤ 0,100 s | ok |
| Offen | Prüfstein 10 (Schichtenvertrag, Feldnamen) | check-packages im Bau |  | – |
| Offen | Prüfsteine 11–16 (im Gebiet) | lauf.demo.AeolianLaufCheck, Attest P10c_AeolianLauf.md |  | – |
| Offen | Rechenzeit | 0,6 s |  | – |

1 Verstoß.
