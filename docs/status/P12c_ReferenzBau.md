# P12c – ReferenzBauCheck

Attest für `com.dan.render.reference (Bauten)`, Version 0.2. Stand 19.11.2026 23:45, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

10 Prüfsteine, 1 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Häuser und Gehölze im Gebiet | true, true | true, true | ok |
| 1 Dreiecksbaum | Baum | 202433 Dreiecke (Häuser und Körper), gebaut in 0,13 s |  | – |
| 1 Dreiecksbaum | Dreiecksbaum gegen alle Dreiecke (zufällige Strahlen) | 0 von 4000 anders, 88 mit Treffer; 2,0 µs je Schnitt im Baum, 1360 µs gegen alle | 0 anders | **fehlt** |
| 2 Ofen | Ofen: Laub (Blatt) im weißen Ofen, Strahlen von unten in die Krone | Mittel 1,000000000000, größte Abweichung 0,0e+00; 19106 von 20000 treffen zuerst Laub | 1, Abweichung < 10⁻⁹ | ok |
| Prüfstein 8: hanswarft_haeuser | hanswarft_haeuser: Arten im Echtzeitbild | Himmel 44,3 %, Bauten 4,5 %, Gelände 50,9 %, Gestaltwasser 0,3 %, Bauten in der Referenz: 59353 Dreiecke |  | – |
| Prüfstein 8: hanswarft_haeuser | hanswarft_haeuser: abweichend (Echtzeit → Referenz, ohne Rand) | Gelände → Himmel 27; Gelände → Bauten 182; Gelände → Gestaltwasser 2;  |  | – |
| Prüfstein 8: hanswarft_haeuser | Prüfstein 8: hanswarft_haeuser – Art des ersten Treffers, Echtzeit gegen Referenz (ohne Rand zweier Arten) | 99,959 % von 513549 Bildpunkten gleich; am Rand 4851 (0,9 %) ausgenommen, mit ihnen 99,842 % | ≥ 99,9 % | ok |
| Prüfstein 8: lahnungen | lahnungen: Arten im Echtzeitbild | Himmel 42,6 %, Bauten 0,7 %, Gelände 56,0 %, Wasser 0,7 %, Bauten in der Referenz: 131893 Dreiecke |  | – |
| Prüfstein 8: lahnungen | lahnungen: abweichend (Echtzeit → Referenz, ohne Rand) | Bauten → Gelände 1; Gelände → Wasser 11;  |  | – |
| Prüfstein 8: lahnungen | Prüfstein 8: lahnungen – Art des ersten Treffers, Echtzeit gegen Referenz (ohne Rand zweier Arten) | 99,998 % von 506565 Bildpunkten gleich; am Rand 11835 (2,3 %) ausgenommen, mit ihnen 99,983 % | ≥ 99,9 % | ok |
| Prüfstein 8: fething | fething: Arten im Echtzeitbild | Himmel 44,3 %, Gelände 51,3 %, Gestaltwasser 4,4 %, Bauten in der Referenz: 70673 Dreiecke |  | – |
| Prüfstein 8: fething | fething: abweichend (Echtzeit → Referenz, ohne Rand) | Gelände → Gestaltwasser 144; Gestaltwasser → Gelände 5;  |  | – |
| Prüfstein 8: fething | Prüfstein 8: fething – Art des ersten Treffers, Echtzeit gegen Referenz (ohne Rand zweier Arten) | 99,971 % von 512505 Bildpunkten gleich; am Rand 5895 (1,1 %) ausgenommen, mit ihnen 99,847 % | ≥ 99,9 % | ok |
| Prüfstein 19: Gehölze | Gehölze | Tag 200 (Sommer); 554 Kronen in der Referenz; Bildpunkte mit Gehölz: Echtzeit 61307, Referenz 54190, beide 49143 |  | – |
| Prüfstein 19: Gehölze | Prüfstein 19: Gehölz im Echtzeitbild, das auch in der Referenz Gehölz ist | 80,2 % | ≥ 70 % [G] | ok |
| Prüfstein 19: Gehölze | Prüfstein 19: Gehölz in der Referenz, das auch im Echtzeitbild Gehölz ist | 90,7 % | ≥ 70 % [G] | ok |
| Prüfstein 9: alles aus | Prüfstein 9: Häuser, Gestalt und Bewuchs aus – die Szene der Referenz ist die vor P12c3 (Gelände und Wasser, dieselbe Referenz) | Gelaendeszene ohne Gestalt, ohne Bauten und Kronen | ja | ok |
| Prüfstein 9: alles aus | Referenz mit allem an: zwei Bilder mit derselben Saat (64 × 36, 2 Pfade) | 0 Werte anders, 0 nicht endlich | 0, 0 | ok |
| Prüfstein 10: Fotos | Hanswarft am Tag | Foto 480 × 270: ZEITGRENZE; 14 Runden, 462 s, Rauschen in Blöcken 2,48 |  | – |
| Prüfstein 10: Fotos | Hanswarft am Tag: Häuser im Bild (Auskunft) | mittlere Helligkeit (sRGB, 0 … 1) Foto 0,302, Echtzeit 0,327, Verhältnis 0,923 über 5358 Bildpunkte |  | – |
| Prüfstein 10: Fotos | Hanswarft am Tag: Bild | docs\status\P12c_Foto_hanswarft_haeuser.png |  | – |
| Prüfstein 10: Fotos | Hanswarft nachts (Fenster) | Foto 480 × 270: ZEITGRENZE; 6 Runden, 428 s, Rauschen in Blöcken 4,35 |  | – |
| Prüfstein 10: Fotos | Hanswarft nachts (Fenster): Häuser im Bild (Auskunft) | mittlere Helligkeit (sRGB, 0 … 1) Foto 0,087, Echtzeit 0,057, Verhältnis 1,520 über 5354 Bildpunkte |  | – |
| Prüfstein 10: Fotos | Hanswarft nachts (Fenster): Bild | docs\status\P12c_Foto_hanswarft_haeuser_nacht.png |  | – |
| Prüfstein 10: Fotos | Gehölze an der Hanswarft | Foto 480 × 270: ZEITGRENZE; 35 Runden, 467 s, Rauschen in Blöcken 2,04 |  | – |
| Prüfstein 10: Fotos | Gehölze an der Hanswarft: Häuser im Bild (Auskunft) | mittlere Helligkeit (sRGB, 0 … 1) Foto 0,255, Echtzeit 0,309, Verhältnis 0,823 über 3990 Bildpunkte |  | – |
| Prüfstein 10: Fotos | Gehölze an der Hanswarft: Bild | docs\status\P12c_Foto_gehoelz.png |  | – |
| Prüfstein 10: Fotos | Bilder zur Sichtprüfung (Prüfstein 10) | docs\status\P12c_Foto.png |  | – |

1 Verstoß.
