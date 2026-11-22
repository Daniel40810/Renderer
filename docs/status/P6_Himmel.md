# P6c – AirCheck

Attest für `com.dan.render.air`, Version 0.2. Stand 18.11.2026 23:16, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

23 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Sonne und Mond | Ort | Hanswarft, UTM 32 O 470900, N 6046615 = 8,54993° O, 54,56621° N |  | – |
| 1 Sonne und Mond | Sonne aus core.astro gegen sky.astro (8 Zeitpunkte 2026/27, wahre Höhe und Azimut) | größter Winkel 0,00246° | ≤ 0,01° (Prüfstein 4) | ok |
| 1 Sonne und Mond | Mond aus core.astro gegen sky.astro (mit Parallaxe) | größter Winkel 0,00035° | ≤ 0,01° [G] | ok |
| 1 Sonne und Mond | Phasenwinkel und Helligkeit des Monds gegen sky | Phasenwinkel ± 0,0003°, Helligkeit ± 0,0005 mag | ≤ 0,05° [G], ≤ 0,005 mag [G] | ok |
| 1 Sonne und Mond | Wahres Nord und Ost in der Szene (Gitter UTM 32) gegen einen Schritt in Länge und Breite | Konvergenz -0,3667°, größter Winkel 4,07e-05° | ≤ 1e-4° [G] | ok |
| 1 Sonne und Mond | Strahlenbrechung (Sæmundsson) an der Kimm und im Zenit | 0,4830° bei 0°, -0,000032° bei 90° | 0,48° ± 0,002°, 0 | ok |
| 1 Sonne und Mond | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| 2 Tabellen auf der Karte | Luft | Sicht 30 km [G], Bodenalbedo 0,23, Mie-Auslöschung 1,168e-04 /m |  | – |
| 2 Tabellen auf der Karte | Durchlässigkeit als optische Tiefe (256 × 64) gegen Scattering, relativ (unter 10⁻³ absolut) | ± 3,58e-05 | ≤ 1e-3 [G] (Prüfstein 4) | ok |
| 2 Tabellen auf der Karte | J der drei Kanäle (2560 × 768, float16) gegen Streuordnungen, bezogen auf den größten Wert je Kanal | ± 0,00e+00 | ≤ 1e-3 [G] (Prüfstein 4) | ok |
| 2 Tabellen auf der Karte | Himmelsansicht (256 × 128) gegen Scattering.radianceSpektral (Sonne), radiance (Mond) und Nachthimmel, Leuchtdichte (256 Texel, 125 über der Kimm) | ± 2,19e-04, hellster 108755 cd/m² | ≤ 1e-3 [G] (Prüfstein 4) | ok |
| 2 Tabellen auf der Karte | Himmelsansicht, größte Abweichung | Texel 226/64 (Blick -0,001 hoch): Karte 1210,9, Prozessor 1211,1 |  | – |
| 3 Himmel und Luftperspektive im Bild | Himmel im Bild (RGBA16F) gegen die Himmelsansicht auf dem Prozessor (1152 Bildpunkte) | relativ ± 9,46e-04 | ≤ 2e-3 [G] (float16: 4,9e-4) | ok |
| 3 Himmel und Luftperspektive im Bild | Luftperspektive (8 Schritte) gegen Scattering.strecke, bis 40 km über dem Boden (1152 Bildpunkte) | relativ ± 9,36e-04 | ≤ 2e-3 [G] | ok |
| 3 Himmel und Luftperspektive im Bild | Luftperspektive auf 30 km | Durchlässigkeit 0,168 / 0,137 / 0,082, Streulicht 135931 cd/m² |  | – |
| 3 Himmel und Luftperspektive im Bild | Luftperspektive mit 8 Schritten gegen 256 Schritte (Blickachse, 0,5 / 5 / 30 km) | Streulicht ± 4,61e-03 | ≤ 1 % [G] | ok |
| 3 Himmel und Luftperspektive im Bild | Tabellen der Luft nur einmal gerechnet | 1-mal | 1 | ok |
| 3 Himmel und Luftperspektive im Bild | Zweites Bild mit derselben Luft und Zeit: Tabellen und Ansicht bleiben (seit P7e) | 1-mal, Ansicht 1-mal | 1, 1 | ok |
| 3 Himmel und Luftperspektive im Bild | Ansicht neu erst, wenn die Sonne um mehr als 0,01° wandert [G], dann über fünf Bilder verteilt (F62 A): 1 s später nicht, 60 s später nach einem Bild noch nicht, nach fünf ja | 1 / 1 / 2 | 1 / 1 / 2 | ok |
| 3 Himmel und Luftperspektive im Bild | Himmelsansicht über fünf Bilder verteilt gegen in einem Zug (F62 A), bezogen auf den hellsten Wert | ± 0,00e+00 | ≤ 1e-6 [G] | ok |
| 3 Himmel und Luftperspektive im Bild | Abgelegt im Bildziel | [Himmel/Durchlaessigkeit, Himmel/Mehrfachstreuung, Himmel/Ansicht], [Himmel/Werte], [Himmel/Sonne, Himmel/Mond] | alle sechs | ok |
| 4 Sonnenscheibe | Sonne | wahre Höhe 7,671°, scheinbar 7,785° (Brechung 0,114°), Azimut 123,72° (im Gitter 124,09°), Radius 0,2679° |  | – |
| 4 Sonnenscheibe | Scheibe im HDR-Bild unter der Grenze (Vorbelichtung 10⁻⁵) | 0 Bildpunkte an der Grenze | 0 | ok |
| 4 Sonnenscheibe | Licht der Scheibe über ihre Bildpunkte = Beleuchtungsstärke der Sonne mal Durchlässigkeit | 20487 lx gegen 20521 lx (-0,16 %) | ± 1 % [G] | ok |
| 4 Sonnenscheibe | Scheibe in der Bildmitte, wenn der Blick auf die scheinbare Sonne zielt | 0,069 Bildpunkte (0,00071°) daneben, 2086 Bildpunkte in der Scheibe | ≤ 0,25 Bildpunkte [G] | ok |
| 5 Tiefe Sonne (P10d, Prüfsteine 2 und 3) | Sonne scheinbar 3,20° (wahr 2,97°, 2026-10-25T15:36:22Z), Auge 4 m; Licht aus der Richtung 3,20° (aus den Werten des Bilds) | unter der Kimm (640 Punkte): Karte / Java 1,018 (R 1,018 G 1,018 B 1,016), Ansicht bilinear in Java / Java 1,018 (R 1,018 G 1,018 B 1,017); 0° bis 2° (74 Punkte): Karte / Java 1,001 (R 1,001 G 1,001 B 1,001), Ansicht bilinear in Java / Java 1,001 (R 1,001 G 1,001 B 1,001); 2° bis 6° (200 Punkte): Karte / Java 0,999 (R 0,999 G 0,999 B 0,999), Ansicht bilinear in Java / Java 0,999 (R 0,999 G 0,999 B 1,000); über 6° (1386 Punkte): Karte / Java 1,000 (R 1,000 G 1,000 B 1,000), Ansicht bilinear in Java / Java 1,001 (R 1,001 G 1,001 B 1,000);  |  | – |
| 5 Tiefe Sonne (P10d, Prüfsteine 2 und 3) | Prüfstein 2: Himmel im Bild der Karte gegen Java mit derselben Richtung, Sonne 3,2°, Höhe über 2°, je Band und Kanal | größte Abweichung 0,1 % (über 2° im Ganzen R 1,000 G 1,000 B 1,000) | 1 ± 0,02 [G] | ok |
| 5 Tiefe Sonne (P10d, Prüfsteine 2 und 3) | Sonne scheinbar 1,00° (wahr 0,60°, 2026-10-25T15:54:19Z), Auge 4 m; Licht aus der Richtung 1,00° (aus den Werten des Bilds) | unter der Kimm (638 Punkte): Karte / Java 1,017 (R 1,018 G 1,017 B 1,015), Ansicht bilinear in Java / Java 1,018 (R 1,019 G 1,017 B 1,015); 0° bis 2° (72 Punkte): Karte / Java 1,003 (R 1,003 G 1,003 B 1,000), Ansicht bilinear in Java / Java 1,003 (R 1,004 G 1,003 B 1,000); 2° bis 6° (202 Punkte): Karte / Java 0,999 (R 0,998 G 0,999 B 1,000), Ansicht bilinear in Java / Java 0,999 (R 0,999 G 0,999 B 1,000); über 6° (1386 Punkte): Karte / Java 1,000 (R 1,000 G 1,000 B 0,999), Ansicht bilinear in Java / Java 1,000 (R 1,000 G 1,000 B 1,000);  |  | – |
| 6 Albedo aus dem Anteil des Wassers (P10d, Messung d; seit F105 A je Kanal) | Mischung | Wasser (F105 A, Schwebstoff 30 g/m³, Wind 8 m/s, Sonne wie im Bild) R 0,442 G 0,421 B 0,269; Anteil Wasser 0,60 → Albedo R 0,357 G 0,345 B 0,254 (nach Y 0,341), Anteil des Lands je Kanal 1,75 / 1,67 / 1,14; erstes Bild 1,3 s (mit den Tabellen für Wasser und Land, beim ersten Mal gerechnet und abgelegt) |  | – |
| 6 Albedo aus dem Anteil des Wassers (P10d, Messung d; seit F105 A je Kanal) | J der drei Kanäle auf der Karte gegen die Mischung in Java je Kanal (float16), bezogen auf den größten Wert je Kanal; die Luft der Werte mit der Albedo nach Y | ± 6,97e-04; Luft 0,341 | ≤ 2e-3 [G] | ok |
| 6 Albedo aus dem Anteil des Wassers (P10d, Messung d; seit F105 A je Kanal) | Neu gemischt erst ab 0,003 Änderung der Albedo [G]; danach die Ansicht über fünf Bilder verteilt, nicht in einem Zug | Mischungen 1 / 1 / 2, Ansichten 1 / 1 / 2 | gleich / +1; gleich / gleich / +1 | ok |
| 7 Aufräumen | Speicherkonto nach allen Teilen | AirCheck 0,0 MB von 512,0 MB, Spitze 274,5 MB | 0 belegt | ok |
| 7 Aufräumen | Validierungsschicht | nicht eingeschaltet (-Dcore.gpu.validierung=true, braucht das Vulkan SDK) |  | – |

Alles in Ordnung.
