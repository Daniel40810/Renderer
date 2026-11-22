# P1 – CoreCheck

Attest für `com.dan.core`, Version 0.1. Stand 20.11.2026 22:24, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

119 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Raum | UTM 32 gegen PROJ, 5 Orte | 7,09e-05 m | ≤ 0,00100 m | ok |
| 1 Raum | UTM hin und zurück | 2,65e-10 ° | ≤ 1,00e-09 ° | ok |
| 1 Raum | Meridiankonvergenz gegen PROJ | 9,84e-10 ° | ≤ 1,00e-06 ° | ok |
| 1 Raum | Maßstab gegen PROJ | 6,18e-11 | ≤ 1,00e-08 | ok |
| 1 Raum | Hanswarft O 470 779, N 6 047 148 → 8,548005° O, 54,570997° N | 3,56e-10 ° | ≤ 1,00e-07 ° | ok |
| 1 Raum | Coriolis-Parameter bei Hooge | 1,1884·10⁻⁴ 1/s | 1,19·10⁻⁴ ± 0,005·10⁻⁴ | ok |
| 1 Raum | Normalschwere bei 45° gegen GRS80 (9,806199 m/s²) | -5,13e-10 m/s² | ≤ 1,00e-06 m/s² | ok |
| 1 Raum | Oberfläche des Ellipsoids gegen GRS80 (510 065 621,7 km²), relativ | -1,08e-11 | ≤ 1,00e-09 | ok |
| 1 Raum | Erdfest hin und zurück | 7,89e-10 m | ≤ 1,00e-06 m | ok |
| 1 Raum | Absenkung in 10 km gegen clipmap (r²/2R′ = 6,8278 m) | 0,00 m | ≤ 1,00e-09 m | ok |
| 1 Raum | Kimm für ein Auge 2 m über dem Wasser | 5412 m |  | – |
| 1 Raum | Maßstab und Konvergenz an der Hanswarft | 0,999610, -0,3683° (24 km im Gitter sind 9,4 m kürzer als in der Natur) |  | – |
| 1 Raum | Lage relativ zum Auge als float | Fehler 0,0e+00 m (Nord als float allein: 0,25 m) | < 1·10⁻⁴ m | ok |
| 2 Zeit und Himmelsmechanik | TT − UTC | 2026: 69,184 s, 1999: 64,184 s | 69,184 s und 64,184 s | ok |
| 2 Zeit und Himmelsmechanik | Julianisches Datum 2000-01-01 12:00 UTC = 2 451 545,0 | 0,00 d | ≤ 1,00e-09 d | ok |
| 2 Zeit und Himmelsmechanik | Sonne, Meeus 25.b (α 198,378121°, δ −7,783817°) | 5,72e-05 ° | ≤ 0,00100 ° | ok |
| 2 Zeit und Himmelsmechanik | Mond, Meeus 47.a (α 134,688470°, δ 13,768368°) | 1,61e-06 ° | ≤ 0,000500 ° | ok |
| 2 Zeit und Himmelsmechanik | Mond, Meeus 47.a, Abstand 368 409,7 km | -0,0152 km | ≤ 1,00 km | ok |
| 2 Zeit und Himmelsmechanik | Sonnenstand über Hooge gegen astropy, 3 Zeiten | 0,000459 ° | ≤ 0,0200 ° | ok |
| 2 Zeit und Himmelsmechanik | Abstand der Sonne vom Ort gegen astropy | 7,16e-07 AE | ≤ 1,00e-05 AE | ok |
| 2 Zeit und Himmelsmechanik | Mondstand über Hooge gegen astropy, mit Parallaxe | 0,000368 ° | ≤ 0,0500 ° | ok |
| 2 Zeit und Himmelsmechanik | Abstand des Monds vom Ort gegen astropy | 37,5 km | ≤ 100 km | ok |
| 2 Zeit und Himmelsmechanik | sun.E im Perihel gegen Aphel | 1407,6 und 1316,8 W/m², Verhältnis 1,0690 | 1,069 ± 0,002 | ok |
| 2 Zeit und Himmelsmechanik | Szenenzeit = Start + Zeitraffer · Laufzeit, auch rückwärts | 2026-03-20T06:42:00Z | 2026-03-20T06:42:00Z | ok |
| 3 Gezeitenpotential und Partialtiden | Winkelgeschwindigkeit von 10 Partialtiden gegen das Package tide | 4,00e-08 °/h | ≤ 2,00e-07 °/h | ok |
| 3 Gezeitenpotential und Partialtiden | Partialtiden von EOT20 | [2N2, J1, K1, K2, M2, M4, MF, MM, N2, O1, P1, Q1, S1, S2, SA, SSA, T2] | 17 | ok |
| 3 Gezeitenpotential und Partialtiden | Knotenfaktor f von M2 und O1 gegen Schureman, relativ | 0,00163 | ≤ 0,00500 | ok |
| 3 Gezeitenpotential und Partialtiden | Mondknoten Mitte 2026 | N = 332,6°, f(M2) = 0,967, u(M2) = 0,97°, f(K1) = 1,103, f(O1) = 1,166 |  | – |
| 3 Gezeitenpotential und Partialtiden | Rayleigh über 369 Tage | 15 Tiden getrennt, ausgelassen [] | alle 15 trennbar | ok |
| 3 Gezeitenpotential und Partialtiden | Gleichgewichtstide aus Sonne und Mond | M2 0,2432 m (-0,4 %), +0,06°; S2 0,1133 m (-0,2 %), -0,02°; N2 0,0467 m (-0,0 %), +0,23°; K1 0,1421 m (+0,1 %), -0,02°; O1 0,1015 m (+0,6 %), -0,02°; |  | – |
| 3 Gezeitenpotential und Partialtiden | Amplitude von M2, S2, N2, K1, O1 gegen die Gleichgewichtstide (Cartwright), relativ [G] | 0,00624 | ≤ 0,0200 | ok |
| 3 Gezeitenpotential und Partialtiden | Phase von M2, S2, N2, K1, O1 gegen das astronomische Argument [G] | 0,228 ° | ≤ 1,00 ° | ok |
| 3 Gezeitenpotential und Partialtiden | Rest der Analyse (Äquator, 45° N) | 9,8 mm, 9,3 mm bei Hub 0,87 m |  | – |
| 3 Gezeitenpotential und Partialtiden | Faktor der festen Erde 1 + k₂ − h₂ | 0,693 | 0,69 ± 0,005 | ok |
| 4 Felder und Katalog | Katalog der Grundstufe (Werkbuch 5.3, mit den vier Beiträgen aus F18, tide.eq aus P5c, air.taux, air.tauy aus P5e, bed.fluff aus P9c, air.sw, air.lw, water.sst aus P10b) | 48 Felder in 11 Gruppen [aeol, air, bed, ground, sed, sun, surf, tide, veg, water, waves] | 48 Felder, 11 Gruppen | ok |
| 4 Felder und Katalog | Länge nach Ort, Schichten und Komponenten | Zelle 35, Kante 82, Rand 48, Säule 2240, 3 Komponenten 105, global 3, Spektrum 35280 | 35, 82, 48, 2240, 105, 3, 35 280 | ok |
| 4 Felder und Katalog | Referenz in double, Betrieb in float; Kanten erst quer zu x, dann quer zu y | ja |  | ok |
| 4 Felder und Katalog | ground.hyps ist kein Feld; air.n hat 64 Schichten und keine andere Zahl | beides abgelehnt |  | ok |
| 4 Felder und Katalog | Abgleich mit der Karte ist ausdrücklich (hoch, herunter); ohne Karte geschieht nichts | je ein Ruf |  | ok |
| 4 Felder und Katalog | Ein Schreiber je Feld (F18 A): bed.dz und surf.z0 bildet der Kern aus Beiträgen | bed.dz.sediment, bed.dz.aeolian, surf.z0.sediment, surf.z0.vegetation | kein Feld mit zwei Schreibern | ok |
| 5 Gitter | G0 Schelf, 15° W–13° O, 43–64° N | 336 × 378 | 336 × 378 | ok |
| 5 Gitter | G1 Deutsche Bucht, Hülle in UTM 32 auf volle km | G1 UTM 32, O 299000–507000, N 5894000–6188000, Zelle 1000 m, 208 × 294 | rund 200 × 290 | ok |
| 5 Gitter | G2 Nordfriesland, 200 m (seit P5b nach F30 A bis O 502 km, N 6 097 km) | 370 × 430 | 370 × 430 | ok |
| 5 Gitter | G3 Szene Hooge, 20 m | 1200 × 1600 | 1200 × 1600 | ok |
| 5 Gitter | Schachteln: G3 in G2 im Verhältnis 10, G2 in G1 im Verhältnis 5, G4 (1000² Zellen zu 2 m, F19 A) in G3 im Verhältnis 10 | G3/G2 10, G4/G3 10, G2/G1 5 | 10, 10, 5 | ok |
| 5 Gitter | Fläche von G0 gegen den Streifen des Ellipsoids, relativ | 2,22e-16 | ≤ 1,00e-12 | ok |
| 5 Gitter | Zellen | G0 127.008 · G1 61.152 · G2 159.100 · G3 1.920.000 · G4 1.000.000 |  | – |
| 5 Gitter | Zelle von G0 im Süden und im Norden | 6,8 × 6,2 km und 4,1 × 6,2 km |  | – |
| 5 Gitter | Punkt von G0 nach G2 (über Länge und Breite) | 2,66e-05 m | ≤ 0,00100 m | ok |
| 6 Subgrid | Volumen an den Stufen gegen die Summe über die Pixel, relativ | 7,79e-15 | ≤ 1,00e-12 | ok |
| 6 Subgrid | Volumen unter der tiefsten und über der höchsten Stufe, relativ | 8,44e-15 | ≤ 1,00e-12 | ok |
| 6 Subgrid | Volumen zwischen den Stufen an jeder Pixelhöhe, Anteil der Schranke A·Spanne/(4·11²) | 0,958 | ≤ 1,00 | ok |
| 6 Subgrid | Umkehrung η(V(η)) | 4,44e-16 m | ≤ 1,00e-09 m | ok |
| 6 Subgrid | Volumen des Gebiets bei ebenem Spiegel, 41 Stände: Fehler als mittlere Tiefe [G] | 6,43e-05 m | ≤ 0,00100 m | ok |
| 6 Subgrid | Volumen des Gebiets (Abweichung der Tabelle) | η -2,50 m: 0 m³ (+0,000 %); η -1,25 m: 1.320 m³ (+0,401 %); η +0,00 m: 5.052 m³ (+0,196 %); η +0,25 m: 7.093 m³ (+0,069 %); η +1,25 m: 139.323 m³ (+0,000 %); η +2,50 m: 325.965 m³ (+0,000 %); |  | – |
| 6 Subgrid | Prüfstück: der Priel (7 m breit, Sohle −2,0 m) ist schmaler als eine Zelle – bei η = −1,0 m liegt keine Zelle im Mittel unter Wasser | 0 Zellen mit mittlerer Höhe unter η | 0 | ok |
| 6 Subgrid | Der Priel führt bei η = −1,0 m Wasser von West nach Ost | durchgehend, 38 Kanten mit Leitwert | durchgehend | ok |
| 6 Subgrid | Leitwert der Kanten gegen die Summe über die Pixel ab 1 cm wirksamer Tiefe, relativ [G] | 0,0334 | ≤ 0,0500 | ok |
| 6 Subgrid | Wirksame Tiefe der Kanten im Film unter 1 cm [G] | 0,000151 m | ≤ 0,000500 m | ok |
| 6 Subgrid | Leitwert der Kanten | im Mittel 0,23 % daneben über 158.174 Proben; im Film unter 1 cm bis 16 % (16 von 1.826 Proben über 5 %) |  | – |
| 6 Subgrid | Querschnitt der Kanten (mittlere benetzte Tiefe h_A) an den Stufen gegen die Summe über die Pixel (P4, F25) | 0,00 m | ≤ 1,00e-12 m | ok |
| 6 Subgrid | Querschnitt der Kanten zwischen den Stufen ab 1 cm mittlerer Tiefe, relativ [G] (157.790 Proben) | 0,0240 | ≤ 0,0500 | ok |
| 6 Subgrid | Querschnitt der Kanten im Film unter 1 cm [G] | 0,000155 m | ≤ 0,00500 m | ok |
| 6 Subgrid | Neu gebacken, wo sich der Boden geändert hat (6 × 4 Zellen): gleich dem Neubau | bitgleich | bitgleich | ok |
| 6 Subgrid | Volumen aus der Packung (float16) gegen die Tabelle in double, relativ zu Zellfläche · Spanne [G] | 0,000483 | ≤ 0,00200 | ok |
| 6 Subgrid | Querschnitt und wirksame Tiefe der Kanten aus der Packung (float16) gegen double, relativ zur Spanne [G] | 0,000471 | ≤ 0,00200 | ok |
| 6 Subgrid | Größe der Packung | 244 Byte je Zelle (mit Randkanten); G3 mit 1,92 Mio. Zellen: 432 MB |  | – |
| 6 Subgrid | Backen | 384 Zellen mit 400 Pixeln und 808 Kanten in 0,03 s; größte Spanne in einer Zelle 3,03 m |  | – |
| 7 Umrechnung zwischen Gittern | Menge bleibt erhalten: Schachtel fein → grob und grob → fein, relativ | 1,11e-16 | ≤ 1,00e-12 | ok |
| 7 Umrechnung zwischen Gittern | Anteil außerhalb des Ziels | fein → grob 0,000, grob → fein 0,968 | 0 und 0,968 | ok |
| 7 Umrechnung zwischen Gittern | Menge und Flächenintegral bleiben erhalten: geographisch → UTM, relativ | 9,77e-15 | ≤ 1,00e-12 | ok |
| 7 Umrechnung zwischen Gittern | Zustand bilinear: eine Ebene im groben Gitter bleibt im feinen dieselbe | 6,82e-13 m | ≤ 1,00e-09 m | ok |
| 7 Umrechnung zwischen Gittern | Zustand geographisch → UTM: linear in Länge und Breite; außerhalb bleibt das Ziel unberührt | 3,55e-15 | ≤ 1,00e-09 | ok |
| 7 Umrechnung zwischen Gittern | Zellen des UTM-Gitters in der geographischen Quelle | 3028 von 20800 |  | – |
| 8 Bus | Mängel der Verdrahtung, jeder für sich: es startet nichts | 7 von 7 erkannt | alle | ok |
| 8 Bus | Alle Mängel in einer Meldung | 7 Mängel | ≥ 6 | ok |
| 8 Bus | Der Bus hält drei Stände; gelesen wird dazwischen | Stände bei 1200, 2400, 3600 s | 3 Stände, kein Lesen außerhalb | ok |
| 8 Bus | Zustand zur Zeit t zwischen zwei Ständen (linear in der Zeit) | 1,82e-12 m | ≤ 1,00e-09 m | ok |
| 8 Bus | Derselbe Zustand auf einem gröberen Gitter (bilinear) | 3,64e-12 m | ≤ 1,00e-09 m | ok |
| 8 Bus | Lesen vor dem ältesten Stand: gilt der älteste, und es wird gezählt | Zeit 1200.0 s, gezählt 1 |  | ok |
| 8 Bus | Leser, die das Werkbuch nennt, geben keinen Hinweis | [] | kein Hinweis | ok |
| 8 Bus | Ein Modul liest nur, was es angemeldet hat | abgelehnt | abgelehnt | ok |
| 8 Bus | Verbund am Bus: bed.dz als Summe, surf.z0 als Maximum der Beiträge | bed.dz 0,4320 m (Beiträge zur Zeit 540 s), surf.z0 0,030 m | 0,4320 m und 0,030 m | ok |
| 8 Bus | bed.dz schreibt nur der Kern; ein Feld ohne Verbund lässt sich nicht zusammensetzen | beides abgelehnt | abgelehnt | ok |
| 9 Takt | Reihenfolge und Zahl der Schritte in 30 min (Tide 60 s, Hydro 20 s, Atmos 300 s) | 126 Schritte, Beginn: [tide 0.0 60.0, hydro 0.0 20.0, atmos 0.0 300.0, hydro 20.0 40.0] | 126 Schritte; tide, hydro, atmos, hydro | ok |
| 9 Takt | Jedes Modul findet die Felder der anderen zu seiner Startzeit zwischen zwei Ständen | 0 Lesungen außerhalb | 0 | ok |
| 9 Takt | Hinweis auf einen Leser, den das Werkbuch nicht nennt | [render.water liest surf.wet: im Werkbuch nicht als Leser genannt (aeolian, render.terrain, archiv)] | 1 Hinweis | ok |
| 9 Takt | Fluss als Zeitmittel über das Kopplungsintervall: Σ q̄·Δt gegen ∫ q dt über 10 min, relativ | 0,00 | ≤ 1,00e-12 | ok |
| 9 Takt | Reproduzierbar: gleicher seed, gleiche Reihenfolge, gleiches Ergebnis – nacheinander und im eigenen Faden | Protokoll und Prüfsumme gleich; anderer seed, andere Prüfsumme | bitgleich | ok |
| 9 Takt | Bild liest im eigenen Faden, während die Physik rechnet | 68 Bilder bis 1500 s: zerrissen 0, Uhr vor der Physik 0, außerhalb gelesen 0 | nichts davon | ok |
| 9 Takt | Zeitraffer im Faden | Ziel 20 000, gehalten 20000; die Physik könnte 3,7e+05 |  | – |
| 9 Takt | Physik zu langsam (2 ms je 20 s): der Zeitraffer sinkt auf das Erreichbare, die Uhr bleibt hinter der Physik | Ziel 50 000, gehalten 8933 (erreichbar 9926), 536-mal angehalten, außerhalb gelesen 0 | gesenkt, ≤ erreichbar, nie voraus | ok |
| 9 Takt | Die Physik holt auf: der Zeitraffer steigt wieder auf das Ziel | 50000 nach 0,9 s | 50 000 innerhalb von 4,8 s | ok |
| 10 Archiv | Kopf, Stände und Größe eines Laufs nach dem Wiederöffnen | 13 Stände zu 2304 Byte, Kopf gleich | 13 Stände zu 2304 Byte | ok |
| 10 Archiv | Stände gelesen wie geschrieben (float32 genau, float16 auf den nächsten Wert) | 0,00 | ≤ 0,00 | ok |
| 10 Archiv | Abspielen vorwärts, rückwärts und gesprungen: linear zwischen zwei Ständen | 0,00 | ≤ 0,00 | ok |
| 10 Archiv | Vor dem ersten und nach dem letzten Stand gilt dieser | 0 s und 3600 s |  | ok |
| 10 Archiv | Einzelne Werte (Archiv.wert) gleich den Sätzen; die gehaltenen Sätze bleiben | 0,00 | ≤ 0,00 | ok |
| 10 Archiv | Vorlesen eines Stands (W5c): alle Spuren, nur einmal, die gehaltenen Sätze bleiben | 2304 Byte für Stand 7 | 2304 Byte | ok |
| 10 Archiv | Messung des Lesens (dieser Faden) | Lesen 0,00 s (115 Sätze, 0,1 MB, langsamster water.eta Stand 0 0,00 s), Warten auf die Sperre 0,00 s |  | – |
| 10 Archiv | Größe eines Stands von G3 (h, u, v, c als float16) | 15,4 MB; eine Tide in 5-min-Schritten (149 Stände): 2,29 GB |  | – |
| 10 Archiv | Auflösung von float16 | bei 1 m 0,98 mm, bei 3 m 2,0 mm, bei 30 m 16 mm |  | – |
| 11 Bilanzen | Geschlossene Bilanz über 2000 Schritte: Rest relativ zum Bezug | 7,77e-16 | ≤ 1,00e-12 | ok |
| 11 Bilanzen | Zeile für bilanzen.csv | Volumen G3;40000.0;4.24058723862696E8;4.119295546356512E8;-3.8807044536434853E8;49614.59139414055;150000.0;2.2646065644948976E-7;2.830052056012635E-16 | 9 Spalten | ok |
| 11 Bilanzen | Ein Leck von 30 m³ je Schritt bei 4·10⁸ m³ und Grenze 1·10⁻⁵: Fehler, keine Warnung | Fehler im Schritt 134: Bilanz Volumen G3 zur Zeit 2680.0 s: Rest -4020.00 m³, erlaubt 4000.23 (1.0e-05 des Bezugs 4.00023e+08) | Fehler im Schritt 134 (Rest über 4000 m³) | ok |
| 12 Prüfrahmen | Harmonische Analyse eines Pegels aus 10 Partialtiden, 369 Tage: Amplitude | 2,00e-15 m | ≤ 1,00e-09 m | ok |
| 12 Prüfrahmen | Harmonische Analyse: Phase | 1,14e-13 ° | ≤ 1,00e-06 ° | ok |
| 12 Prüfrahmen | Harmonische Analyse: Mittel und Rest | 1,82e-15 m | ≤ 1,00e-09 m | ok |
| 12 Prüfrahmen | Vorhersage aus dem Ergebnis für 2031 | 3,11e-15 m | ≤ 1,00e-08 m | ok |
| 12 Prüfrahmen | Rayleigh-Kriterium bei 15 Tagen | getrennt [M2, K1, S2, O1, M4, MS4], ausgelassen [P1, N2, K2] | M2, S2, K1, O1, M4, MS4; ohne N2, K2, P1 | ok |
| 12 Prüfrahmen | 30 Tage mit 5 cm Rauschen: Amplitude von M2 [G] | -0,00785 m | ≤ 0,0100 m | ok |
| 12 Prüfrahmen | 30 Tage mit Rauschen | M2 1,4921 m, 200,12°; Rest 5,0 cm |  | – |
| 12 Prüfrahmen | Sammler (P5d): drei Orte, 30 Tage, gegen je eine Analyse: Mittel und Amplitude (m), Phase (° · 10⁻³) | 4,44e-16 | ≤ 1,00e-10 | ok |
| 12 Prüfrahmen | Sammler aus parallelen Aufrufen: 4000 Orte, acht Runden, jede Tide | 0 Ausnahmen, Abweichung bis 0,0e+00 | keine Ausnahme, ≤ 1e-12 | ok |
| 12 Prüfrahmen | RMSE, Bias, Skill nach Willmott, Korrelation an einem Handbeispiel | RMSE 0,5, Bias 0,5, Skill 0,952381 | 0,5; 0,5; 0,952381 | ok |
| 12 Prüfrahmen | Vergleich zweier Felder: größte Abweichung in float-Schritten, NaN zählt | 1,9e-06 m = 2 Schritte in Zelle 100, 1 NaN | 2 Schritte, 1 NaN | ok |
| 12 Prüfrahmen | ΔE₀₀ gegen drei Prüfpaare von Sharma u. a. | 4,03e-05 | ≤ 0,000100 | ok |
| 12 Prüfrahmen | Bildvergleich: ein Viertel der Bildpunkte verfärbt (Rot und Grün +12 von 255) | ΔE₀₀ im Mittel 1,13, größter 4,50, 25 % über 1 | 25 % über 1 | ok |
| 13 Feingelände (Kachelstapel, P3) | Ablage: STUFE.txt und Kacheln float32 Little Endian, Zeilen von Nord; fehlende Kachel ohne Werte | gleich | gleich | ok |
| 13 Feingelände (Kachelstapel, P3) | Regel: Abtastung 1 m an einer Pixelmitte genau die Kachel; leeres Pixel und Kachel ohne Datei aus der 10-m-Stufe (bilinear, Ebene); Abtastung 10 m aus der 10-m-Stufe | 5,0300, 54,4950, 55,5150, 54,7050 | 5,0300, 54,4950, 55,5150, 54,7050 | ok |
| 13 Feingelände (Kachelstapel, P3) | Feingelände unter einem Gitter 20 m mit 20 Pixeln je Zelle: Pixel gleich der Kachel (Süd nach Nord), Rand aus der 10-m-Stufe, Subgrid ohne Lücke | 9999 von 9999 Pixeln gleich, Volumen bei η = 6 m 9519,30 von 9519,30 m³ | gleich | ok |
| 13 Feingelände (Kachelstapel, P3) | Mindesthöhe im Stapel (P6e): Abtastung 1 m gegen Gelaende.mindestens beim Backen; Abtastung 10 m unverändert | 10000 von 10000 Pixeln gleich, 50 gehoben; 10 m gleich | alle gleich, gehoben > 0 | ok |
| 14 Gebiet (Gitterkette, Packung, Bauwerke, P3d) | gebiet.txt: Gitterkette (geographisch und UTM) hin und zurück | 2 Gitter, gleich | gleich | ok |
| 14 Gebiet (Gitterkette, Packung, Bauwerke, P3d) | subgrid.bin: Packung von 600 Zellen und 1250 Kanten (145600 Byte) hin und zurück, Volumen an 21 Wasserständen | bitgleich, Volumen gleich (0,0e+00) | bitgleich | ok |
| 14 Gebiet (Gitterkette, Packung, Bauwerke, P3d) | ground.z.f32 und nass.f32 hin und zurück | gleich | bitgleich | ok |
| 14 Gebiet (Gitterkette, Packung, Bauwerke, P3d) | bauwerke.txt: Ringe (mit Namen, ohne Krone), Siel, Kanten mit Profil und nan | 2 Ringe, 1 Siele, 2 Kanten | gelesen wie geschrieben | ok |
| 14 Gebiet (Gitterkette, Packung, Bauwerke, P3d) | Stufe.Leser: Pixel und Pixel am Ort gegen Stufe.wert, 2000 Stichproben über vier Kacheln (eine fehlt) | 0 falsch | 0 | ok |
| 15 Felder auf Gradrastern und Pegel (P5b) | Gradraster: bilinear an 1 000 Orten gegen eine lineare Funktion | 3,55e-15 | ≤ 1,00e-05 | ok |
| 15 Felder auf Gradrastern und Pegel (P5b) | Gradraster: ein Punkt NaN – drei Gewichte mit Summe 1; alle vier NaN – der nächste gültige im Umkreis von 2 Punkten, weiter weg keiner; außerhalb keiner | 3 Gewichte (Summe 1,000000000000), nächster 69, weit null |  | ok |
| 15 Felder auf Gradrastern und Pegel (P5b) | Tidenfeld: geschrieben und gelesen bitgleich (NaN an Land eingeschlossen); Wasserstand aus den Faktoren gegen Σ Partialtide.wert, 200 Zeiten | bitgleich, /Δη/ bis 1,2e-15 m | bitgleich, < 1e-9 m | ok |
| 15 Felder auf Gradrastern und Pegel (P5b) | Wetterfeld: geschrieben, abgebildet gelesen; nach 1,5 h zwischen den Punkten gegen die lineare Funktion; nach dem Ende NaN | /Δ/ bis 4,9e-12 | < 2e-3 (float) | ok |
| 15 Felder auf Gradrastern und Pegel (P5b) | Pegel: Reihe, Kennwerte und Analyse geschrieben und gelesen; Vorhersage aus den abgelegten Tiden (auf 0,1 mm abgelegt), Wert zwischen zwei Schritten, NaN bleibt NaN | M2 1,3000 m 350,00°, /Δ Vorhersage/ 8,2e-10 m, /Δ Wert/ 3,0e-08 m | < 1e-4 m | ok |
| 16 Bus: ein Feld auf mehreren Gittern, Antrieb im Intervall (P5c) | Rand nach Ort.RAND (seit P5c in Gitter): Süd, Ost, Nord, West gegen den Uhrzeigersinn, je Randkante eine Zelle, Geisterzelle eine Zelle weiter außen | 0 von 16 falsch; Folge 0,0 1,0 2,0 3,0 4,0 4,0 4,1 4,2 4,2 … | 0 | ok |
| 16 Bus: ein Feld auf mehreren Gittern, Antrieb im Intervall (P5c) | Ein Feld auf zwei Gittern eines Schreibers (seit P5c): Leser auf jedem und auf einem dritten (bilinear aus dem feinsten, das es deckt); ohne Gitter kein Schreibfeld | /Δ/ bis 2,7e-15 m, 2 Leitungen, Hinweise [], Schreibfeld ohne Gitter abgelehnt | < 1e-9 m, 2, keine, abgelehnt | ok |
| 16 Bus: ein Feld auf mehreren Gittern, Antrieb im Intervall (P5c) | Mängel mehrerer Gitter und der Antriebe: zweimal auf demselben Gitter, ein Leser außerhalb aller Gitter des Schreibers, Antrieb im Kreis | 3 von 3 erkannt | alle | ok |
| 16 Bus: ein Feld auf mehreren Gittern, Antrieb im Intervall (P5c) | Antrieb im Intervall (seit P5c): hydro (300 s) liest tide.bc (60 s) zu neun Zeiten in jedem Intervall; tide, später angemeldet, rechnet vorher bis zum Ende des Intervalls | Beginn [tide 0.0 60.0, tide 60.0 120.0, tide 120.0 180.0, tide 180.0 240.0, tide 240.0 300.0, hydro 0.0 300.0]; 54 Lesungen, /Δ/ bis 4,4e-16, außerhalb 0, Leitung mit 7 Ständen | fünfmal tide, dann hydro; genau; 0; 7 Stände | ok |

## Befund

- 6 Subgrid: Die Subgrid-Tabellen von G3 brauchen auf der Karte 432 MB (Zelle: Stufen, V, A; zwei Kanten: Stufen, wirksame Tiefe, Querschnitt – Version 2 seit P4), nicht 160 MB wie in Abschnitt 11 geschätzt. Das Budget von 1,5 GB hält.

Alles in Ordnung.
