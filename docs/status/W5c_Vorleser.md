# W5c – Vorleser

Attest für `lauf.Archivquelle (Vorleser)`, Version W5c. Stand 20.11.2026 22:25, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

5 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Szenario | Sturmflut, 25.10.2026, D:\Daten\HalligHooge\_p6\archiv |  | – |
|  | Nachgestellte Platte | schläft nach 3 s ohne Zugriff ein, wacht in 6 s auf; nur Stände außerhalb des Dateipuffers |  | – |
| 1 Ohne Vorleser: A47 nachgestellt | Prüfstein V1: ein Stand nach Ruhe ohne Vorleser (wie die Bildschleife bis W5b) | 6,04 s | ≥ 5,5 s (die Platte schläft) | ok |
| 2 Mit Vorleser: Abspielen mit Pause | Ablauf | 2351 Bilder in 40,0 s, Szenenzeit 9945 s weit, 282 Stände vorgelesen (315 MB), langsamster 6,01 s |  | – |
| 2 Mit Vorleser: Abspielen mit Pause | Prüfstein V2: bereit() je Bild, auch während die Platte aufwacht | 99 % der Bilder ≤ 0,0147 ms, größte 0,07 ms | ≤ 0,1 ms; größte ≤ 50 ms (wartet nie auf die Platte) [G] | ok |
| 2 Mit Vorleser: Abspielen mit Pause | Prüfstein V3: Lesen der Stände im Bild, wenn bereit | größte 118,9 ms in 34 Bildern mit neuem Stand | ≤ 50 ms [G] | **fehlt** |
| 2 Mit Vorleser: Abspielen mit Pause | Prüfstein V4: die Platte schlief nach der Pause ein, das Bild musste nicht halten | Vorleser wartete 6,01 s; die Uhr hielt 0,02 s (1 Mal) | Vorleser ≥ 5,5 s; Halten ≤ 0,5 s | ok |
| 3 Mit Vorleser: Sprung in kalte Stände | Prüfstein V5: Sprung in kalte Stände – das Bild läuft weiter, die Uhr hält, bis sie da sind | bereit nach 6,01 s; bereit() größte 0,15 ms; Bild danach bei 259200 s (Ziel 258600 s) | ≥ 5,5 s und ≤ 8 s; bereit() ≤ 50 ms | ok |

1 Verstoß.
