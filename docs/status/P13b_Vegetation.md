# P13b – VegetationCheck

Attest für `com.dan.physics.vegetation`, Version 0.1. Stand 20.11.2026 12:18, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

14 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| Statistik der Überflutung an einer gerechneten Tide | MThw der tiefen Zelle gegen die Scheitel der Tide | 1,6246 gegen 1,6246 m, 29 Tiden | ± 0,1 mm | ok |
| Statistik der Überflutung an einer gerechneten Tide | Anteil der Tiden mit Wasser: tief, am MThw, trocken | 1,00, 0,66, 0,00 | 1; zwischen 0 und 1; 0 | ok |
| Statistik der Überflutung an einer gerechneten Tide | Zellen, die nicht in jeder Tide nass sind, bekommen das MThw der Nachbarn | gefüllt 0, 1, 1; MThw 1,6246, 1,6246, 1,6246 | 0, 1, 1; gleich | ok |
| Statistik der Überflutung an einer gerechneten Tide | Spring über Mittel über Nipp; Dauer | MSpThw 1,891, MThw 1,625, MNpThw 1,336 m; Dauer 1,00 / 0,10 / 0,00 | ja | ok |
| Zonen nach der Höhe über MThw | Grenzen −0,40 / 0 / +0,35 m über MThw | -0,50→0 -0,40→1 -0,01→1 +0,00→2 +0,34→2 +0,35→3 +1,20→3 NaN→0 | wie gesetzt | ok |
| Zonen nach der Höhe über MThw | Höhe über MThw als Byte (1 cm) hin und zurück | −1,20 … +1,20 m | ± 0,5 cm | ok |
| Pflanzen je Zone und Jahreszeit | Jahreszeit: Mitte Januar 0, Ende Juli 1 | 0,000, 1,000 | 0; > 0,99 | ok |
| Pflanzen je Zone und Jahreszeit | Manning-n nach Strickler aus z0 (zur Auskunft; Packung: Fenne 0,030, Salzwiese 0,035) | Pionierzone: n Sommer 0,0353, Winter 0,0270, beweidet 0,0303; untere Salzwiese: n Sommer 0,0337, Winter 0,0303, beweidet 0,0289; obere Salzwiese: n Sommer 0,0378, Winter 0,0337, beweidet 0,0324; |  | – |
| Pflanzen je Zone und Jahreszeit | Pionierzone im Winter lichter; Weide kürzer | Deckung Pionier 0,10 / 0,40; Höhe oben 0,120 / 0,300 m | ja | ok |
| Anteile und Rauheit je Zelle (VegetationModul) | Zelle ganz obere Salzwiese: Anteil, z0, Bedeckung | 1,000, 0,0300 m (soll 0,0300), 0,950 | 1; wie Salzwiese | ok |
| Anteile und Rauheit je Zelle (VegetationModul) | Zelle halb untere Salzwiese: z0 über den bewachsenen Teil, Bedeckung über die Zelle | 0,500, 0,0150 m, 0,450 | 0,5; z0 der Zone; halbe Bedeckung | ok |
| Anteile und Rauheit je Zelle (VegetationModul) | Fenne ohne Tidezone (F155 A): z0 wie obere Salzwiese beweidet, Bedeckung wie obere; im Bild Fenne | z0 0,0120 m (soll 0,0120), Bedeckung 0,950; Bild unverändert | ja | ok |
| Anteile und Rauheit je Zelle (VegetationModul) | Unter dem Mindestanteil keine Rauheit der Pflanzen (die Packung gilt) | 0,0000 / 0,0300 m | 0 / > 0 | ok |
| Zonen in der Gestalt und zurück (Bild, Prüfstein 7) | Art ohne Zone gleich der Art der Gestalt (Bildpunkte) | 142916 mit Zone, 1000000 gleich zurück, 0 anders | 0 anders | ok |
| Zonen in der Gestalt und zurück (Bild, Prüfstein 7) | Salzwiese unter der Pionierzone im Bild Schlick, aus wieder Salzwiese (F158 A) | Art 13, ohne Zone 2 | 13, 2 | ok |

Alles in Ordnung.
