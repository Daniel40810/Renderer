# P7b – ReferenceCheck

Attest für `com.dan.render.reference`, Version 0.2. Stand 20.11.2026 03:42, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

34 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 4 Spektrum (Prüfstein 4) | Spektrum gleicher Energie nach XYZ: Normfarbwertanteile gegen Weiß E (1/3, 1/3) | x 0,333314, y 0,333288; Abweichung 4,6e-05 | ≤ 1 · 10⁻⁴ [G] | ok |
| 4 Spektrum (Prüfstein 4) | D65 (Y = 1) nach linearem sRGB gegen (1, 1, 1) | 1,0000033; 0,9999992; 0,9999984 | ≤ 1 · 10⁻⁴ [G] | ok |
| 4 Spektrum (Prüfstein 4) | RGB → Spektrum (Mallett und Yuksel) → RGB unter D65, 2 000 Farben in [0, 1]³ | größte Abweichung 7,4e-15 | ≤ 1 · 10⁻⁶ [G] | ok |
| 4 Spektrum (Prüfstein 4) | Spektral gegen die Kanäle des Echtzeitbilds (Verhältnis) | 680 nm: Rayleigh 0,882, Ozon 0,9996, Wasser 1,0000; 550 nm: Rayleigh 0,895, Ozon 0,9999, Wasser 1,0000; 440 nm: Rayleigh 0,916, Ozon 1,0002, Wasser 1,0000;  |  | – |
| 4 Spektrum (Prüfstein 4) | Sonne außerhalb der Luft (ASTM G173) gegen das Echtzeitbild | 133100 lx gegen 127500 lx je Kanal; Farbe x 0,3234, y 0,3327; sRGB relativ 1,097 / 0,977 / 0,943 (Echtzeit weiß) |  | – |
| 4 Spektrum (Prüfstein 4) | Schwebstoff (F54 A): Fournier-Forand | n 1,10, μ_FF 3,5960; b_b/b der Tabelle 0,0190 (Ziel 0,019); b = 18,95 1/m bei 30 mg/l |  | – |
| 1 Ofentest (Prüfstein 1) | Ofentest spektral, 30° unter der Kimm (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 0,99997 ± 0,00013 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 3,8e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest spektral, 60° unter (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 1,00012 ± 0,00013 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 3,9e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest spektral, 5° über (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 1,00009 ± 0,00022 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 6,7e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest spektral, 30° über (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 0,99998 ± 0,00014 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 4,1e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest spektral, senkrecht nach unten (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 0,99989 ± 0,00013 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 3,8e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest drei Kanäle, 30° unter der Kimm (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 0,99995 ± 0,00008 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 2,3e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest drei Kanäle, 60° unter (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 1,00001 ± 0,00008 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 2,3e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest drei Kanäle, 5° über (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 0,99982 ± 0,00017 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 5,1e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest drei Kanäle, 30° über (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 0,99995 ± 0,00008 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 2,5e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest drei Kanäle, senkrecht nach unten (Luft, glattes Wasser 2 m über dem Boden mit Albedo 1) | 1,00015 ± 0,00008 (1000000 Pfade) | 1 ± 3 σ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest: Streuung des Mittels klein genug für 10⁻³ | 3 σ = 2,3e-04 | 3 σ ≤ 1 · 10⁻³ [G] | ok |
| 1 Ofentest (Prüfstein 1) | Ofentest mit rauem Wasser (Cox und Munk), 30° unter der Kimm | Wind 0 m/s (σ² 0,0030): 0,9957; Wind 5 m/s (σ² 0,0286): 0,9587; Wind 12 m/s (σ² 0,0644): 0,9061; die Schattung der Facetten nach Smith gibt Energie ab, die Mehrfachstreuung zwischen den Facetten fehlt |  | – |
| 2 Beer–Lambert und Durchlässigkeit (Prüfstein 2) | Spektrales Tracking durch 2 m reines Wasser (drei Kanäle zugleich), 32000000 Pfade | 680 nm 0,39460 gegen 0,39455; 550 nm 0,89308 gegen 0,89315; 440 nm 0,98729 gegen 0,98738; größte relative Abweichung 1,1e-04, σ relativ bis 2,2e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 2 Beer–Lambert und Durchlässigkeit (Prüfstein 2) | Spektrales Tracking durch die Luft (Anteil, der die Oberkante erreicht), 30° über der Kimm, gegen Quadratur (Rayleigh, Mie, Ozon), 12000000 Pfade | 680 nm 0,67523 gegen 0,67569; 550 nm 0,57542 gegen 0,57570; 440 nm 0,44453 gegen 0,44467; größte relative Abweichung 6,7e-04, σ relativ bis 2,9e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 2 Beer–Lambert und Durchlässigkeit (Prüfstein 2) | Ratio-Tracking durch die Luft, 30° über der Kimm bis zur Oberkante, gegen Quadratur, 16000000 Pfade | 680 nm 0,67570 gegen 0,67569; 550 nm 0,57571 gegen 0,57570; 440 nm 0,44467 gegen 0,44467; größte relative Abweichung 2,5e-05, σ relativ bis 2,2e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 3 Einfacher Rayleigh-Himmel (Prüfstein 3) | Rayleigh, Einfachstreuung (Stoßschätzer), Zenit, Sonne 30° im Süden, 4000000 Pfade | 680 nm 411,98 gegen 411,92; 550 nm 877,73 gegen 877,57; 440 nm 1700,05 gegen 1699,67; größte relative Abweichung 2,3e-04, σ relativ bis 8,0e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 3 Einfacher Rayleigh-Himmel (Prüfstein 3) | Rayleigh, Einfachstreuung (Stoßschätzer), 30° hoch nach Norden (gegen die Sonne), Sonne 30° im Süden, 4000000 Pfade | 680 nm 801,08 gegen 801,88; 550 nm 1654,00 gegen 1655,52; 440 nm 2956,24 gegen 2958,20; größte relative Abweichung 1,0e-03, σ relativ bis 5,8e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 3 Einfacher Rayleigh-Himmel (Prüfstein 3) | Rayleigh, Einfachstreuung (Stoßschätzer), 10° hoch nach Osten, Sonne 30° im Süden, 4000000 Pfade | 680 nm 1659,00 gegen 1658,49; 550 nm 3079,96 gegen 3079,21; 440 nm 4287,30 gegen 4287,12; größte relative Abweichung 3,1e-04, σ relativ bis 3,4e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 5 Gekrümmte Strahlen (Prüfstein 5) | Strahlgleichung (RK4, Schritt 10 m) über 30 km im linearen Gefälle von n (k = 0,13, 0,3° steigend) gegen die genaue Kettenlinie | Höhe am Ende 147,896906 m gegen 147,896906 m, relativ zur Länge 9,3e-14; Kreisbogen als Näherung 147,8968 m | relativ ≤ 1 · 10⁻⁶ [G] | ok |
| 5 Gekrümmte Strahlen (Prüfstein 5) | Kugel mit dn/dr = −k n / R (k = 0,13) gegen das gesenkte Gelände des Echtzeitbilds: Treffer auf NHN aus 3 m Augenhöhe in 5 km | 5000,000 m gegen 5000,000 m (Bogen auf der Erde), Abweichung 0,000 m | ≤ 0,5 m [G] | ok |
| 6 Schätzer seit P7c (zu den Prüfsteinen 1–3) | Durchlässigkeit der Luft durch Quadratur (Gauß-Legendre in Abschnitten) gegen Mittelpunktregel in 4 Mio. Schritten | aus 0 m, 30,00°: T 0,675687 / 0,575703 / 0,444675; aus 0 m, 2,00°: T 0,010670 / 0,002751 / 0,000199; aus 1000 m, -0,30°: T 0,000074 / 0,000007 / 0,000000; aus 5 m, 0,05°: T 0,000001 / 0,000000 / 0,000000; größte relative Abweichung 2,5e-10 | ≤ 1 · 10⁻⁶ [G] | ok |
| 6 Schätzer seit P7c (zu den Prüfsteinen 1–3) | Grund (Albedo 0,1) unter 5 m glattem, klarem Wasser ohne Luft, Sonne 40°, Blick 45° hinab, rot (a = 0,465/m): Pfadverfolger gegen die Formel, 24000000 Pfade | 5,18622 gegen 5,18617 cd/m², Abweichung 9,2e-06, σ relativ 9,2e-04 (Rest der Spiegelung zwischen Grund und Fläche unter 10⁻⁴) | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |
| 6 Schätzer seit P7c (zu den Prüfsteinen 1–3) | Grund (Albedo 0,1) unter 5 m rauem (σ² 0,03), klarem Wasser ohne Luft, sonst wie oben: Pfadverfolger gegen zwei Integrale mit derselben BSDF (3 000 × 3 000), 24000000 Pfade | 5,02767 gegen 5,03078 cd/m², Abweichung -6,2e-04, σ relativ 9,4e-04 | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | ok |

Alles in Ordnung.
