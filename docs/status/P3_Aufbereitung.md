# P3 – AufbereitungCheck

Attest für `com.dan.aufbereitung`, Version 0.6. Stand 13.11.2026 08:02, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

32 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 GeoTIFF, erzeugt | 64 Arten: II/MM × Streifen/Kacheln × ohne/LZW/Deflate/PackBits × float32, float32 mit Prädiktor 3, int16 und 3 × uint8 mit Prädiktor 2; Bild ganz und Fenster mit Rand außerhalb | alle Werte gleich | gleich | ok |
| 1 GeoTIFF, erzeugt | BigTIFF, Deflate mit Prädiktor 3, Leerwert −9999 | Werte gleich, Leerwert NaN | gleich | ok |
| 1 GeoTIFF, erzeugt | Lage aus ModelTransformation mit PixelIsPoint (Anknüpfpunkt auf der Pixelmitte) | Ecke 312995,0 / 6113005,0, Schritt 10 / -10, EPSG 25832 | Ecke 312995 / 6113005, Schritt 10 / −10, EPSG 25832 | ok |
| 1 GeoTIFF, erzeugt | Lage aus PixelScale und Tiepoint, geographisch, Leerwert als Text mit Komma-Stelle | Ecke -15 / 64, EPSG 4326, leer -32767 | Ecke −15 / 64, EPSG 4326, leer −32767 | ok |
| 2 Ein Bit je Pixel (CCITT Gruppe 4) | Ein Bit je Pixel, Gruppe 4 (CCITT T.6), 2601 × 24 (libtiff), MinIsBlack, Läufe bis 2601 | 0 Pixel falsch | 0 | ok |
| 2 Ein Bit je Pixel (CCITT Gruppe 4) | Ein Bit je Pixel, PackBits, 97 × 24 (libtiff), MinIsBlack, Läufe bis 2601 | 0 Pixel falsch | 0 | ok |
| 2 Ein Bit je Pixel (CCITT Gruppe 4) | Ein Bit je Pixel, ohne Kompression, 97 × 24 (libtiff), MinIsBlack, Läufe bis 2601 | 0 Pixel falsch | 0 | ok |
| 3 XYZ | Werte an ihren Pixeln (Zeilen von Nord), Trenner Leerzeichen, Komma, Tabulator, CRLF, Kopfzeile, Exponent | gleich | gleich | ok |
| 3 XYZ | Bericht: Punkte, außerhalb, doppelt, leer | 7, 1, 1, 11 | 7, 1, 1, 11 | ok |
| 4 JSON und OSM | JSON: Zahlen, Wahrheitswerte, null, Escapes, leere Objekte; Fehler mit Ort | gleich | gleich | ok |
| 4 JSON und OSM | Overpass (out body geom): Punkt, Weg, geschlossener Weg, Multipolygon aus drei Wegen (einer umgekehrt) und einem Loch; nach UTM und zurück | 1 Punkte, 2 Wege, 2 Flächen, Länge 149,9999 m, Ringe 2 mit 50000,0 m² | 1, 2, 2, 150 m, 2 Ringe 50 000 m² | ok |
| 4 JSON und OSM | Auswahl nach Tags: Werte mit /, vorhanden (*), Muster (~, ohne Groß/klein), und (&), oder (;), leer | richtig | richtig | ok |
| 5 Füllwert | Flächen über Ost- und Nordgrenze von Kacheln (Teile mit 30 bzw. 50 Pixeln) entfernt; 30 Pixel im Inneren und 1,305 m bleiben | 340 Pixel in 2 Flächen entfernt, 1 kleine Flächen behalten | 340 Pixel in 2 Flächen | ok |
| 6 Stapel aus erzeugten Daten | Aufbau | 9 Kacheln, 1541889 Pixel Land, 48689 Saum, 56422 Hof; 16179 Pixel des Grunds mit Mittel; größte Verschiebung im Saum 3,941 m; Füllwert: 7458111 Pixel; 1,0 s |  | – |
| 6 Stapel aus erzeugten Daten | Prüfstein 2 (erzeugt): gemessene Pixel des Lands, Stichproben | 3508 von 3508 gleich | bitgleich | ok |
| 6 Stapel aus erzeugten Daten | Prüfstein 6 (erzeugt): Stufe zwischen Nachbarpixeln im Saum ≤ 0,25 m [G] plus Stufe des Grunds, wo die Randpixel, die das Paar erreichen, auf 0,5 m [G] gleich hoch sind und gleich weit über dem Grund liegen; sonst höchstens der größere der beiden Unterschiede | 97106 Paare, 0 über der Grenze; 2293 Paare an Rändern verschiedener Höhe oder verschiedenen Abstands zum Grund (Stufe dort bis 0,50 m); 395 Paare über 0,25 m | 0 über der Grenze | ok |
| 6 Stapel aus erzeugten Daten | Prüfstein 5 (erzeugt): zusammenhängende Flächen mit 1,30 ± 0,002 m ab 100 m² unter den gemessenen Pixeln der Landstufe (gleich dem DGM1 vor dem Füllwert) | 0 Flächen, 0 Pixel (von 1541889 gemessenen); in der ganzen Stufe mit Saum und Hof 0 Flächen, 0 Pixel (Höhenlinie 1,30 m im Saum) | keine | ok |
| 6 Stapel aus erzeugten Daten | Prüfstein 3 (erzeugt): Grund ohne Land und Saum gleich der Quelle; Abtastung in 1 m außerhalb der Landstufe gleich dem Grund der Quelle | 13103 von 13103 Pixeln des Grunds, 18708 von 18708 Punkten gleich | bitgleich | ok |
| 6 Stapel aus erzeugten Daten | Prüfstein 7 (erzeugt): Pixel des Grunds mit Land oder Saum tragen das Mittel des zusammengesetzten Felds, auf float gerundet | 16179 Pixel, 0 ungleich; Abstand zum Mittel in double bis 2,1e-07 m (Rundung auf float bei Höhen bis 4,4 m) | gleich | ok |
| 6 Stapel aus erzeugten Daten | Subgrid 20 m aus dem 1-m-Stapel (0,8 s): Volumen bei η = −2 … +5 m gegen die Summe über die Pixel, als mittlere Tiefe | 2,04e-05 m | ≤ 0,00100 m | ok |
| 6 Stapel aus erzeugten Daten | Schachtel 200 m (aus der 10-m-Stufe) gegen 20 m (aus 1 m), Volumen über das Gebiet bei η = −1 … +2 m | größte Abweichung 0,29 % |  | – |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Erzeugte Hallig | Ringdeich r = 520 m, Krone 3,0 m, zwei Linien mit einer Lücke im Damm und einer am Siel (Rinne 6 m, Sohle 0 m), Durchlass; 6 Linien, 208 Kanten, 2 Siele, 1 Ringe, 0,2 s; Lücken: mit Siel geschlossen (node/12): way/10–way/11: 45.4 m bei O 471482 N 6047020, Krone 0.00 m (Enden 2.96 / 2.94 m) · geschlossen: way/10–way/11: 27.2 m bei O 471760 N 6046050, Krone 2.87 m (Enden 2.97 / 2.88 m) · geschlossen: way/11–way/10: 27.2 m bei O 471783 N 6046064, Krone 2.84 m (Enden 2.88 / 2.97 m) · mit Siel geschlossen (node/12): way/11–way/10: 45.4 m bei O 471527 N 6047019, Krone 0.00 m (Enden 2.94 / 2.96 m) |  | – |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Lücke im Damm geschlossen, Lücke am Schleusentor als Siel, Durchlass als Siel, ausgeschlossene Linie fehlt | 2 Siele: [Siel1 Rinne_0.00_m+node/12, Siel2 way/13] | 2 Siele | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Prüfstein 11 (erzeugt): jeder Punkt einer Deichlinie (je 1 m) liegt höchstens 20 m [G] von einer Deichkante | 3344 Punkte, größter Abstand 9,0 m bei O 471791 N 6046931 (way/11) | ≤ 20 m | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Prüfstein 11 (erzeugt): Krone je Stelle gleich dem höchsten Pixel im Querschnitt ±5 m quer zur Linie (eigene Rechnung) | 4080 Stellen an Kanten aus OSM gleich, 0 ungleich; 2 Kanten mit Lücke oder Siel nicht nachgerechnet | gleich (auf 1 mm) | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Prüfstein 13 (erzeugt): jedes Siel auf einer Deichkante; das Gelände zeigt eine Rinne (≥ 0,5 m [G] unter der Krone, ≤ 20 m entfernt) oder OSM nennt einen Durchlass | 2 von 2: Siel1: auf Sielkante, Rinne 2,94 m unter der Krone, OSM; Siel2: auf Sielkante, Rinne 2,44 m unter der Krone, OSM; | alle | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Prüfstein 10 (erzeugt): Ring um Mitte geschlossen; bei der niedrigsten Krone − 5 cm keine Zelle im Ring nass (Flutfüllung über die Kanten, Siele zu) | 2128 Zellen (0,85 km²); erste Zelle nass bei 2,83 m, niedrigste Krone 2,83 m bei O 471090 N 6046820 | ≥ niedrigste Krone − 0,05 m | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Prüfstein 10 (erzeugt): bei der höchsten Krone des Rings + 5 cm (2,98 m) sind alle Zellen nass, die tiefer liegen | 2124 von 2124 Zellen nass | alle | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Prüfstein 12 (erzeugt): Fläche, die durch das Einrasten die Seite wechselt, im Ring Mitte und an seinem Rand | 14072 m² = 1,65 % von 0,85 km²; laut bauwerke.txt 14072 m² | ≤ 3 % [G] | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Rauheit 10 m über der Stufe des Grunds, auch wenn der Stapel eine gröbere Stufe hat (Stand P5b: bis dahin nahm sie die gröbste) | Stapel 3 Stufen; Rauheit 10 m: Zelle 10 m, Südwest O 469000 N 6044000, 500 Zellen je Kachel | wie die Stufe 10 m | ok |
| 7 Bauwerke und Rauheit aus erzeugten Daten | Rauheit der erzeugten Hallig: Fenne im Ring, Deich auf der Linie, Watt davor, Wasser in der Tiefe (10 m) | Fenne, Deich, Watt, Fenne, Wasser | Fenne, Deich, Watt, Fenne, Wasser | ok |
| 12 Leser für P5b: HDF5, GRIB, PEGELONLINE, ERA5 (erzeugt) | HDF5 (erzeugt): alte und neue Art (Superblock 0 und 3, Objektköpfe 1 und 2), float64 Big Endian zusammenhängend, float32, int16 kompakt, Ausschnitt | 2 von 2 Dateien mit allen Namen, /Δ/ 0,0e+00 | genau | ok |
| 12 Leser für P5b: HDF5, GRIB, PEGELONLINE, ERA5 (erzeugt) | GRIB (erzeugt): Edition 1 Nord → Süd und Süd → Nord mit Bitmap, Edition 2 über den Nullmeridian mit Vorhersageschritt und Bitmap; Größe, gültige Zeit, Raster, Werte | 3 Felder, Kopf richtig, 2 fehlende als NaN, /Δ/ bis 0,0000 | ≤ 0,01 (16 Bit) | ok |
| 12 Leser für P5b: HDF5, GRIB, PEGELONLINE, ERA5 (erzeugt) | PEGELONLINE (erzeugt): zwei Dateien mit 60 Minuten Überlapp, 100 Minuten Lücke, eine Spitze (+0,8 m), ein Wert außerhalb; Pegelnull, Kennwerte, M2 der Analyse | 2 Dateien, 60 doppelt, 100 Lücken, 1 Spitzen, 1 außerhalb; MThw 1,716 m NHN; M2 1,2000 m 330,00° | 1, 1; M2 1,2 m 330° (±1 mm, ±0,05°) | ok |
| 12 Leser für P5b: HDF5, GRIB, PEGELONLINE, ERA5 (erzeugt) | ERA5 (erzeugt): verschiedene Raster, fehlende Größen werden gemeldet | Raster gemeldet; u10 fehlt zu 2026-10-25T18:00:00Z; v10 fehlt zu 2026-10-25T18:00:00Z; v10 fehlt zu 2026-10-25T19:00:00Z; msl fehlt zu 2026-10-25T19:00:00Z; | gemeldet | ok |
| 8 Quellen, Stapel, Bauwerke und Rauheit des Gebiets | ohne Rezept | nicht geprüft (--rezept) |  | – |

Alles in Ordnung.
