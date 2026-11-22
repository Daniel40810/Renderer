# com.dan.aufbereitung – Gebiete aus ihren Quellen backen

**Stand: P4d (12.11.2026).** Die Aufbereitung macht aus dem geladenen Kartenmaterial ein Gebiet für Physik und Bild. Grundlage ist das Werkbuch HalligHooge, Teil A, Abschnitte 10.4 und 12.4; entschieden mit F20–F24 A (13.2). Hooge ist das erste Gebiet; nichts im Code ist für Hooge gebaut, was ein Gebiet braucht, steht in seinem Rezept.

## Schicht

```
com.dan.aufbereitung   core (ohne gpu) + java.*   Werkzeug: Quellen lesen, Stapel schreiben, Gitter backen; läuft nicht im Lauf
```

`check-packages` prüft die Schicht wie die anderen: Sie wird gegen die übersetzten Klassen des Kerns übersetzt und darf keine Element-Packages kennen; physics und render dürfen sie nicht kennen. Gelesen wird das Ergebnis im Lauf über den Kern (`core.gitter.Gelaende` für den Stapel, `core.gitter.Gebiet` für Gitterkette, Packungen und Bauwerke).

## Was sie tut (Stand P5b)

1. **Lesen.** `GeoTiff`: klassisch und BigTIFF, II und MM, Streifen und Kacheln, ohne Kompression, LZW, Deflate, PackBits, Prädiktor 1, 2 und 3, 8 bis 64 Bit, dazu ein Bit je Pixel auch mit CCITT Gruppe 4 (`Fax`, für die Folien der DTK5); Lage aus ModelTransformation oder PixelScale und Tiepoint, PixelIsPoint auf die Ecke geschoben; Leerwert aus `GDAL_NODATA`. `Dgm1`: Kacheln des DGM1 Schleswig-Holstein als XYZ oder als GeoTIFF aus `kartenmaterial.py dgm1-tif`, mit Bericht (außerhalb, doppelt, leer).
2. **Grund** (`Stapel.grund`): ein Fenster aus einem GeoTIFF (TrilaWatt 2021, 10 m) als Stufe des Kachelstapels, im Raster der Quelle.
3. **Land** (`Fuellwert`): Der Füllwert (DGM1 2024: genau 1,30 m über Wasser und Watt) wird entfernt: zusammenhängende Flächen mit dem Wert ± Toleranz ab einer Mindestfläche, auch über Kachelgrenzen.
4. **Naht** (`Stapel.land`): Saum zum Grund, Hof und Mittel.
   - **Saum.** Jedes Randpixel e des Lands hat den Unterschied Δ_e zum Grund und eine Breite s_e = |Δ_e| / Steigung, zwischen `naht.saum` und `naht.saum.max`. Im Abstand d_e < s_e wirkt es mit dem Rest t_e = 1 − d_e/s_e und dem Gewicht t_e/d_e; z = z_Grund + max t_e · Σ w_e Δ_e / Σ w_e. Die Rampe ist damit nie steiler als die Steigung, zwischen zwei Landstücken verschiedener Höhe linear, und an jedem Rand stetig.
   - **Hof.** Ein Pixel des Grunds, in dem Land oder Saum liegt, trägt danach das Mittel des zusammengesetzten Felds. Die Landstufe trägt jedes Pixel, das bei bilinearer Abtastung einen solchen Pixel des Grunds berühren würde, mit dem Grund wie vorher. So liest eine Abtastung in 1 m außerhalb der Landstufe genau den Grund der Quelle.

5. **Bauwerke** (`Bauwerke`, P3c): Deiche und Siele als Kanten von G3; ein Damm im Inneren einer Zelle wird vom Subgrid nicht gesehen.
   - **Linien** aus OSM (`bauwerke.deiche`, ohne `bauwerke.ohne`), am Gitter geschnitten. **Lücken** bis `bauwerke.luecke` zwischen dem Ende einer Linie und einer anderen werden geschlossen, wenn das Gelände dort einen Damm zeigt oder OSM einen Durchlass nennt (dann mit Siel).
   - **Kantenzug:** vierfach verbunden, je Schritt die Ecke, die der Linie näher liegt.
   - **Krone je Kante** als Profil mit einer Stelle je Pixel (20 bei G3): das höchste Pixel auf dem Querschnitt ±`bauwerke.korridor` quer zur Linie durch ihren nächsten Punkt. Die Krone der Kante ist die niedrigste Stelle; dort läuft das Wasser zuerst über.
   - **Siele:** Schleusentor nah an der Linie, Durchlass oder Graben mit dem Namen eines Siels, der die Linie kreuzt oder an ihr endet, Brücke in der Linie, oder eine Rinne in der Krone, die bis zum Fuß reicht. Die Kanten am Siel bekommen die Krone der Umgebung (Tor zu); das Siel trägt Sohle, A und μ [G].
   - **Geschnittene Zellen:** Pixel zwischen Linie und Kantenzug bekommen die Krone als Mindesthöhe (`G3/mindesthoehe/`); gebacken wird mit max(Gelände, Mindesthöhe).
   - **Ringe:** Zellen, die vom Rand nur über Deichkanten zu erreichen sind; Sonden aus dem Rezept (`ring.<Name> = Ost Nord`) benennen sie.
   - Ablage `G3/bauwerke.txt`: Gitter, Ringe, Siele, Kanten mit Profil.
6. **Rauheit** (`Rauheit`, P3c): Klassen je Pixel (Watt, Sand, Fenne, Salzwiese, Deich, Warft und Bebauung, Wasser, Deckwerk und Lahnung) als Stufen 1 m (über der Landstufe) und 10 m (über dem Grund, der Stufe `grund.stufe`; bis P5b nahm sie die gröbste Stufe des Stapels, nach einem früheren `grob` also die Stufe 100 m), Manning-n in `rauheit/klassen.txt` [G]. Quellen der Reihe nach: Folien der DTK5 (CCITT Gruppe 4, eigener Leser `Fax`), Flächen und Linien aus OSM, gemessenes Land und Höhe; Grünland im Ring wird Fenne, sonst Salzwiese; Streifen um die Deiche und Lahnungen zuletzt.
7. **Leser:** `Json` (RFC 8259) und `Osm` (Overpass mit Geometrie, nach UTM; Multipolygone aus ihren Wegen, Auswahl nach Tags mit `=`, `|`, `*`, `~`, `&`, `;`).

8. **Stufe 100 m** (`Grob`, P3d, F22) über der UTM-Hülle von G1, auf den Feinpixeln von G1: das Mittel an 10 × 10 Punkten aus der 10-m-Stufe, sonst TrilaWatt „ger“ (bilinear, zusammen das Flächenmittel der 10-m-Pixel). Wo TrilaWatt fehlt, an Land GEBCO, im Meer EMODnet bzw. GEBCO, um eine Korrektur k(E, N) auf NHN gebracht: EMODnet und GEBCO liegen in der Deutschen Bucht auf LAT (0,6–2,7 m über TrilaWatt). k ist ein Polynom zweiten Grades durch die Mediane von Quelle − TrilaWatt je Feld von 10 km (Meer, TrilaWatt unter −0,5 m); die Glieder stehen in `gelaende/100m/korrektur.txt` und `gebiet.txt`. Natural Earth (`NaturalEarth`, Shapefile in der ZIP-Datei) sagt, was Land ist.
9. **Schließen** (`Schliessen`, P3d, F23, F24): Land hinter Deichen sehen G0–G2 nicht. Eine Maske im Raster von TrilaWatt: Wand ab `schliessen.hoehe` und Land ohne TrilaWatt, Lücken bis `schliessen.luecke` geschlossen (Quadrat 7 × 7), Flutfüllung von den offenen Rändern; geschlossen ist, was sie nicht erreicht (außer Gelände ab der Wandhöhe), dazu Land ohne TrilaWatt. Ablage `geschlossen/10m` (1 = zu) und `geschlossen/100m` (Anteil je Pixel von G1). Sonden `zu.<Name>` und `offen.<Name>` im Rezept.
10. **Backen** (`Backen`, P3d): G0 aus GEBCO in 3 × 3 Teilen je Pixel (in G1 aus der Stufe 100 m), `ground.z` als Mittel über den Meeranteil, `nass` als Meeranteil nach Natural Earth; G1–G3 als Subgrid aus dem Stapel (`core.gitter.Subgrid`), dazu beim Backen: G1 geschlossen nach dem Anteil, G2 nach der Maske (geschlossen heißt +`schliessen.auf` m), G3 mit der Mindesthöhe; Rauheit aus den Klassen (G2 10 m, G3 1 m und 10 m); G0 und G1 haben keine Klassen und bekommen ein Manning-n für alle Pixel aus `rauheit.grob` (seit P5d; ohne den Schlüssel 0,025 wie bisher, `Subgrid.MANNING`), seit P5e wahlweise mit `rauheit.grob.cd` ein n je Pixel aus der Tiefe für einen festen Beiwert C_d: n = max(−z, d_min)^(1/6) √(C_d/g), d_min aus `rauheit.grob.tiefe` (Vorgabe 1 m) (`Backen.rauheitAusTiefe`). G4 wird zur Probe gebacken und nicht abgelegt; im Lauf backt es `core.gitter.Gebiet.auge` nach denselben Regeln (Mindesthöhe und Rauheit liegen seit P4d als `Gelaende.mindestens` und `Gelaende.rauheit` im Kern, `Backen` ruft sie auf). `ground.z` ist das Zellmittel des gebackenen Feingeländes. Ablage je Gitter `ground.z.f32` und `subgrid.bin` (Format in `core.gitter.Gebiet`; seit P4 Packung Version 2 mit dem Querschnitt je Kante, F25 – ältere Gebiete einmal mit `--schritte backen` neu backen).
12. **Vorgabe der Tide** (`Vorgabe`, Schritt `vorgabe`, P4d): bis P5 den Rand als `tide.bc` rechnet, gibt sie den Rand von G2 (`vorgabe.gitter`) vor. Je Randkante (Folge West, Ost, Süd, Nord; Ort an der Mitte der Kante) Mittelwasser, Amplitude und Phase einer Partialtide (`vorgabe.tide`, für Hooge M2) aus drei Rastern (`vorgabe.amplitude`, `vorgabe.phase`, `vorgabe.mittel`; EasyGSH 2015): das nächste Pixel mit allen drei Werten, dessen Gelände im Stapel unter dem Niedrigwasser der Tide liegt (Mittel − Amplitude), im Umkreis `vorgabe.umkreis` [G]; sonst übernimmt die Kante die Werte der nächsten Kante derselben Seite. Auf Watt, das trockenfällt, rechnet EasyGSH Mittelwasser und M2 nur über die nasse Zeit (am Hafen Husum 0,62 statt 0,11 m); darum die Bedingung. Dazu die Pegel aus dem Rezept (`pegel.<Name> = Ost Nord`) nach derselben Regel. Ablage `vorgabe.txt` mit den Quellen (SHA-256); gelesen im Lauf mit `core.gitter.Gebiet.vorgabe()`.
11. **Inventar** (`Inventar`): `gebiet.txt` mit Rezept, Gitterkette, jeder Quelle (Gruppe, Pfad, Byte, SHA-256, Stand, Lizenz aus `lizenz.<gruppe>` und `stand.<gruppe>`), jeder Ablage mit SHA-256, den Korrekturen und den Zeiten.

13. **EOT20** (`Eot20`, Schritt `eot20`, P5b): die 17 Partialtiden der Meeresgezeiten aus `eot20.datei` (`EOT20.zip` mit `ocean_tides.zip` darin, `ocean_tides.zip` selbst oder ein Ordner mit `<Tide>_ocean_eot20.nc`), ohne Entpacken auf die Platte. `Hdf5` liest NetCDF-4 so weit nötig: Superblock 0–3, Objektköpfe 1 und 2 mit Fortsetzungen, Gruppen alter Art (B-Baum, lokaler Heap) und neuer Art (Verweise im Objektkopf), Datensätze zusammenhängend oder kompakt, float und ganze Zahlen in beiden Byte-Folgen; gestückelt, gepackt und dichte Gruppen nicht. Ausgeschnitten wird G0 und `eot20.rand` Grad [G] darüber hinaus auf den Punkten von EOT20 (1/8°, über 0° hinweg), Real- und Imaginärteil in m, NaN an Land (`core.gitter.Tidenfeld`). Ablage `tide/eot20.txt` (Quelle mit SHA-256, Raster, Tiden, Ablage) und `tide/eot20.f32`. Das Protokoll meldet die Minima von M2 in der Nordsee (wie im Befund) und den Rand von G0: je Randkante an einer Zelle mit Wasser bilinear über nasse Punkte an der Mitte der Geisterzelle, sonst der nächste nasse Punkt im Umkreis von `eot20.umkreis` Punkten [G].
14. **Pegel** (`Pegelonline`, Schritt `pegel`, P5b): je Ordner unter `pegelonline.ordner` die Stammdaten (`station.json`) und alle Dateien `W_*.json` zusammen auf 60 s; in m NHN mit dem Pegelnull; Ausreißer verworfen (außerhalb `pegelonline.grenzen` [G], oder mehr als `pegelonline.spitze` [G] neben dem Median der gültigen Werte über ±`pegelonline.fenster` Minuten [G]); die harmonische Analyse über die ganze Reihe (`core.pruef.Harmonik`, Rayleigh 1, halb- und eintägige und Flachwassertiden; langperiodische nicht, in 30 Tagen wären sie Windstau); die Kennwerte (MThw, MTnw, HThw, NTnw) in m NHN. Ablage `pegel/pegel.txt` und je Pegel die Reihe (Format in `core.pruef.Pegel`). Das Protokoll nennt je Pegel M2 aus EOT20 dagegen und das feinste Gitter mit seiner Zelle.
15. **Antrieb** (`Grib`, `Era5`, Schritt `antrieb`, P5b, F28): ERA5 als GRIB aus dem Climate Data Store (`antrieb.ordner`, alle `*.grib` nach Namen; eine spätere Datei ersetzt Felder einer früheren). `Grib` liest Edition 1 und 2, reguläre Gitter in Länge und Breite, einfach gepackt, mit Bitmap, in jeder Richtung abgetastet (nicht abwechselnd je Zeile); keine Gaußschen Gitter, keine komplexe oder JPEG-Packung. Verlangt werden lückenlose Stunden mit `u10`, `v10`, `msl` auf einem Raster, das die Geisterzellen von G0 deckt. Seit P5e (F34) mit `antrieb.schub` im Rezept dazu die GRIB-Dateien aus diesem Ordner mit `iews`, `inss` und `charnock` (Quelle `era5_cds_schub`); `charnock` NaN füllt `Era5.charnockFuellen` aus den Nachbarn (ERA5 gibt über Land 0,018, dort bleibt es dabei).

**Schritt `wind` (P5e, F36 A).** Wind an Stationen des DWD (`Dwd`, Quelle `dwd_wind` des Kartenwerkzeugs): je `wind.station.<Name> = <Kennung>` im Rezept die ZIP `10minutenwerte_wind_<Kennung>_akt.zip` aus `wind.ordner`; gelesen werden `produkt_zehn_min_ff_*` (Spalten über ihre Namen: `MESS_DATUM`, `FF_10`, `DD_10`, −999 fehlt), `Metadaten_Geographie_*` (Lage) und, wenn vorhanden, `Metadaten_Geraete_Windgeschwindigkeit_*` (Höhe des Gebers; sonst `wind.geber.<Name>` im Rezept). Abgelegt als `wind/wind.txt` und `wind/<Name>.csv` (`core.pruef.Windstation`). Ohne `wind.ordner` im Rezept tut der Schritt nichts. Ablage `antrieb/era5.txt` und `antrieb/era5.f32` (Format in `core.gitter.Wetterfeld`). Die Dateien lädt das Kartenwerkzeug: `python tools\kartenmaterial\kartenmaterial.py laden --quellen era5_cds --monate 202610 --ziel <daten>` (Konto, Schlüssel in `%USERPROFILE%\.cdsapirc`).

**Schritt `sediment` (P9b).** Der Boden am Anfang für `physics.sediment` (`Korn`, Quelle `easygsh_sediment` des Kartenwerkzeugs: `sediment\easygsh_sediment\EasyGSH_DB_Sedimentologie_2016.zip`, EasyGSH-DB der BAW, Stand 2016, Raster 100 m): d50 und Porosität aus den GeoTIFF, der Schlickanteil (Anteil feiner als 63 µm, φ = 4) aus der Summenkurve der CSV, die im Strom durch die geschachtelten ZIP gelesen wird (4,8 GB, nichts wird entpackt außer den beiden GeoTIFF nach `ziel\quellen\sediment`); dazu der Anteil festen Bodens je Pixel aus den Rauheitsklassen (Fenne, Salzwiese, Deich, Warft, Deckwerk; Schritt `rauheit` muss gelaufen sein, sonst gilt aller Boden als lose). Pixel ohne Wert füllt der Schritt aus den Nachbarn (`sediment.reichweite`, Vorgabe 10 Pixel), sonst mit `sediment.vorgabe` (d50 in m, Porosität, Schlickanteil; Vorgabe `2e-4 0.40 0`) [G]. Über die Hülle von G3 (`g3.gebiet`), abgelegt als `ziel\sediment\sediment.txt` und je Feld eine `.f32` (`core.gitter.Kornfeld`). Der Schritt prüft die Lage: d50 der CSV gegen das GeoTIFF am selben Pixel, Median der Abweichung höchstens 5 %. Er läuft nur auf Wunsch: `--schritte sediment`.

**Schritt `sentinel` (P9b).** Schwebstoff an der Oberfläche aus einer Szene von Sentinel-2 L2A für die Prüfung des Sediments (`sentinel.szene`, Vorgabe `luftbild\sentinel2\S2B_32UMF_20260501_0_L2A`): Band B04 (665 nm) über die Hülle von G3 auf 20 m (Mittel der vier Pixel zu 10 m), nur wo die Szene Wasser sieht (SCL 6); Reflexion = Wert · Maßstab + Versatz aus dem JSON der Szene (steht dort `earthsearch:boa_offset_applied: true`, ist der Versatz in den Werten schon abgezogen); Schwebstoff nach Nechad u. a. (2010): 355,85 ρ / (1 − ρ / 0,1728) g/m³. Legt `ziel\pruef\sentinel_spm.txt` und `.f32` ab (`core.gitter.Rasterfeld`). Nur auf Wunsch: `--schritte sentinel`. Gelesen wird mit `GeoTiff`; der TIFF-Leser von Java kann 16 Bit mit Prädiktor nicht.

**Schritt `seegang` (P8b, F74 A).** Seegang aus ERA5 für den Rand von `physics.waves` (`Seegang`, Quelle `era5_cds_seegang` des Kartenwerkzeugs, `seegang.ordner`, Vorgabe `wetter\era5_cds_seegang`); läuft nur, wenn er genannt ist (`--schritte seegang`). `Grib` kennt dafür die Parametertafel 140 von ECMWF: `swh`, `mwd`, `pp1d`, `mwp`, Windsee `shww`, `mdww`, `mpww`, Dünung `shts`, `mdts`, `mpts`, Richtungsbreiten `dwww` (225), `dwps` (228). Raster des Seegangsmodells (0,5°); über Land Ring um Ring aus den Nachbarn über See, Richtungen als Einheitsvektoren. Ablage `antrieb/seegang.txt` und `.f32` (`Wetterfeld.lies(gebiet, Wetterfeld.SEEGANG)`).

**Schritt `luft` (P10b, F90 A).** Luft, Strahlung und Temperatur des Meers aus ERA5 für `physics.atmos` (`Era5Luft`, Quelle `era5_cds_luft` des Kartenwerkzeugs, `luft.ordner`, Vorgabe `wetter\era5_cds_luft`); läuft nur, wenn er genannt ist (`--schritte luft`). `Grib` kennt dafür aus der Tafel 128 `t2` (167), `d2` (168), `sst` (34), `ssrd` (169), `strd` (175), `sshf` (146), `slhf` (147), `tcc` (164), `tp` (228) und die Zeit von Summen (Zeitart 2–5: gültig am Ende, P2). Die Summen der Stunde (J/m²) werden Mittel (W/m²) mit der Zeit am Ende der Stunde; `sst` über Land Ring um Ring aus den Nachbarn über See. Ablage `antrieb/luft.txt` und `.f32` (`Wetterfeld.lies(gebiet, Wetterfeld.LUFT)`).

**Zeiträume (P13a).** Ein weiteres Ereignis liegt neben dem Monat in `antrieb/` in einem eigenen Ordner `antrieb/<Zeitraum>/`. Das Rezept nennt mit `antrieb.monat` (HalligHooge: `202610`) den Monat, der in `antrieb/` selbst liegt; ein anderer Zeitraum kommt über die Befehlszeile: `--schritte antrieb,seegang --antrieb.zeitraum 201312` nimmt in den Ordnern von `antrieb`, `antrieb.schub`, `seegang` und `luft` nur die GRIB-Dateien, deren Name mit `201312` beginnt, und legt nach `antrieb/201312/` ab. Ohne beide Schlüssel nimmt jeder Schritt alle Dateien (wie bisher). Der Lauf wählt den Ordner nach seinem Start (`Wetterfeld.lies(gebiet, name, zeit)`).

**Schritt `luftbild` (P6b, F41 A).** Albedo je Rauheitsklasse und Struktur aus dem Luftbild (`Luftbild`); läuft nur, wenn er genannt ist (`--schritte luftbild`). Je DOP20-Kachel (`luftbild.dop`, Vorgabe `luftbild\dop20`) das Mittel aus 5 × 5 Pixeln je 1-m-Pixel; geeicht gegen Sentinel-2 L2A (`luftbild.s2`, Ordner mit `TCI.tif` und `SCL.tif`, Vorgabe die Szene vom 01.05.2026): je Kanal ρ = g · d über die 10-m-Pixel, die ganz im Luftbild liegen, zu 90 % [G] Fenne, Salzwiese oder Deich sind und in SCL Vegetation oder Boden; TCI / 255 · 0,25 [S, Sättigung von Sen2Cor bei 25 %]. Gerechnet mit d linear und mit d in sRGB entschlüsselt; es gilt das mit dem kleineren Rest. Watt und Sand trocken: A / (1 − 0,45 · 0,5) [G]. Ablage `luftbild/albedo.txt` (Albedo trocken und im Zustand des Flugs, Pixel, Eichung) und `luftbild/1m` (Stufe wie `rauheit/1m`: Leuchtdichte relativ zur Klasse, 0,2 … 5, NaN ohne Luftbild). Auf dem eigenen Rechner (22 Kacheln) rund 70 s.

**Seit P5b (F30 A)** reicht G2 bis O 502 km und N 6 097 km (370 × 430 Zellen), dazu `grund.gebiet`: Husum und List liegen in G2. Danach einmal alle Schritte.

**Seit P5:** G0 bekommt beim Backen ein Subgrid (`G0/subgrid.bin`, fein 10: Pixel zu 30″ × 20″, je das Mittel seiner GEBCO-Teile, in G1 aus der Stufe 100 m; Land nach Natural Earth und in G1 nach dem Anteil geschlossen; `Backen.g0Fein`). `ground.z` und `nass` von G0 bleiben, wie sie waren. Die Packung eines geographischen Gitters ist relativ zur Fläche der Zeile (`zellflaeche` NaN).

**Schritt `gebaeude` (P12a, F125 A, F126 A).** Die Häuser aus dem 3D-Gebäudemodell LoD2 (`CityGml`, `Gebaeude`;
Quelle `lod2` des Kartenwerkzeugs, `gebaeude.ordner`, Vorgabe `gebaeude\lod2`), läuft nur, wenn er genannt ist
(`--schritte gebaeude`). CityGML 1.0 und 2.0 im Strom (auch in ZIP-Dateien), Häuser mit ihren BuildingParts, Flächen
nach Thema (sonst nach der Neigung), äußere Ringe. Im Gebiet `gebaeude.gebiet` (Vorgabe `land.gebiet`): Boden aus den
Grundflächen, Traufe und First aus den Dächern; Wände bis 0,15 m [G] unter das tiefste Gelände an den Ecken. Dach Reet
als Regel (Daniel, 19.11.2026), flach bei Dachform 1000 oder Neigung unter 10° [G]; die Farbe des Dachs aus dem DOP20
(Mittel über den Dachflächen, 0,4 m vom Rand, mit der Eichung aus `luftbild/albedo.txt`), außerhalb des Bereichs von
Reet die Vorgabe; rote Dächer zählt der Bericht nur (`gebaeude.ausnahmen = farbe` führte sie als Ziegel). Wände
Backstein [G]. Namen und Prüfstein 1 aus den Gebäuden der OSM (`gebaeude.osm`, Vorgabe `bauwerke.osm`). Ablage
`bau/gebaeude.bin` (`core.szene.Bauten`) und `bau/gebaeude.txt` (Bericht, je Haus eine Zeile). Prüfung:
`demo/GebaeudeCheck [--gebiet <ziel>]` (Leser an erzeugtem CityGML; Prüfsteine 1–3).

## Benutzung

Das Rezept eines Gebiets ist eine Textdatei (`schlüssel = wert`, `#` für Kommentare). Für Hooge: `HalligHooge/gebiet/Hooge.rezept`.

```bash
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.Aufbereitung ../HalligHooge/gebiet/Hooge.rezept
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.Aufbereitung ../HalligHooge/gebiet/Hooge.rezept --schritte bauwerke,rauheit
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.Aufbereitung ../HalligHooge/gebiet/Hooge.rezept --schritte grob,schliessen,backen
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.Aufbereitung ../HalligHooge/gebiet/Hooge.rezept --schritte vorgabe
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.Aufbereitung ../HalligHooge/gebiet/Hooge.rezept --schritte eot20,pegel,antrieb
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.demo.AufbereitungCheck --rezept ../HalligHooge/gebiet/Hooge.rezept
java -Xmx8g -cp dist/Renderer.jar com.dan.aufbereitung.demo.GebietCheck --rezept ../HalligHooge/gebiet/Hooge.rezept
```

Ohne `--schritte` laufen `gelaende`, `bauwerke`, `rauheit`, `grob`, `schliessen`, `backen`, `vorgabe`, `eot20`, `pegel` und `antrieb`; ein späterer Schritt liest, was ein früherer abgelegt hat. Werte des Rezepts lassen sich auf der Befehlszeile überschreiben (`--ziel <ordner>`, `--daten <ordner>`). Ergebnis unter `ziel`: `gelaende/1m`, `10m`, `100m` (Format in `core.gitter.Gelaende`), `geschlossen/`, `G0/` bis `G3/`, `G3/bauwerke.txt` und `G3/mindesthoehe/`, `gebiet.txt`, `vorgabe.txt`, `rauheit/1m`, `rauheit/10m` und `rauheit/klassen.txt`, seit P5b `tide/`, `pegel/` und `antrieb/`, Quellen aus ZIP-Dateien in `quellen/`, das Protokoll in `protokoll.txt`.

## Prüfung

`demo/AufbereitungCheck` läuft ohne Fenster und endet mit „Alles in Ordnung.“; das Attest steht danach in `docs/status/P3_Aufbereitung.md`.

- **Ohne Rezept** an erzeugten Daten: GeoTIFF in 64 Arten, dazu BigTIFF und beide Arten der Lage; ein Bit je Pixel mit Gruppe 4, PackBits und ohne Kompression (Bilder aus libtiff); XYZ; JSON und OSM; Füllwert über Kachelgrenzen; der Stapel mit Saum, Hof und Mitteln (Prüfsteine 2, 3, 5, 6, 7 aus 12.4); Subgrid aus dem Stapel; eine erzeugte Hallig mit Ringdeich, zwei Lücken in den Linien, Siel und Durchlass (Prüfsteine 10–13) und ihre Rauheit.
- **Leser für P5b** (Teil 12) an erzeugten Proben, mit ecCodes und h5py gemacht und im Quelltext abgelegt: HDF5 alter und neuer Art, GRIB Edition 1 und 2 (Abtastfolge, Bitmap, über den Nullmeridian, Vorhersageschritt), PEGELONLINE mit Überlapp, Lücke, Spitze und Wert außerhalb, ERA5 mit verschiedenen Rastern und fehlenden Größen.
- **Mit Rezept** dazu die Quellen auf dem eigenen Rechner gegen Vergleichswerte aus tifffile und PIL (GEBCO, EMODnet, EasyGSH, Copernicus mit Deflate und Prädiktor 3, Sentinel-2 mit Prädiktor 2, TrilaWatt, Folien der DTK5 mit Gruppe 4), der gebaute Stapel gegen seine Quellen (`--ohne-stapel` lässt ihn aus), die Bauwerke von G3 (Prüfsteine 10–13 für jeden Ring mit Sonde) und die Rauheit (Prüfstein 17).

Prüfstein 10 (Deichring) rechnet eine eigene Flutfüllung über die Kanten von G3: Deichkanten mit ihrer Krone, Siele zu, alle anderen Kanten mit der Schwelle aus dem Stapel und der Mindesthöhe (das niedrigste Paar der höheren Pixel). Prüfstein 11 rechnet die Krone je Stelle nach, Prüfstein 12 zählt die Pixel mit Mindesthöhe im Ring und an seinem Rand, Prüfstein 13 sucht die Rinne am Siel im Stapel.

`demo/GebietCheck` ist die Abnahme von P3 (Prüfsteine 1–18 aus 12.4, Attest `docs/status/P3_Gebiet.md`, Status-Bild `docs/status/P3_Gebiet.png`): Inventar und zweimal gebacken (G0–G2 ganz gegen die SHA-256 der Ablage, G3 an einem Fenster bitgleich), die Quellen, der Stapel, der Höhenbezug (Stufe 100 m und G0 gegen eine eigene Rechnung), Subgrid gegen das Feingelände (Prüfstein 8), die Schachteln (Prüfstein 9), Bauwerke, Rauheit, G0 gegen Natural Earth (Prüfstein 14), das Schließen an den Sonden (Prüfstein 18), Zeiten und Bild. Prüfstein 9 vergleicht je Zelle nur, wo die feinen Zellen im Mittel mindestens 1 m tief sind; fast trockene Zellen unterscheiden sich durch die Auflösung um bis zu 100 % (die Fassung mit 1 cm steht daneben im Attest).

Seit P5b hat `GebietCheck` Teil 12 mit den Prüfsteinen 19–26 (`PruefungP5b`, `--nur 12` für ihn allein): EOT20 gegen den Befund (numpy aus denselben Dateien, M2 an acht Pegeln, S2, N2, K1, O1 an zwei), die M2-Amphidromien der Nordsee, der Rand von G0, M2 der Pegel gegen UTide, Rohwerte, Ausreißer, Kennwerte und Scheitel der Sturmflut, ERA5 lückenlos über den Sturm, G2 nach F30 A mit Husum und List, die Ablagen mit SHA-256 und die drei Schritte noch einmal in einem leeren Ordner (bitgleich).

Prüfstein 5 (Füllwert) zählt nur gemessene Pixel, die im Stapel den Wert des DGM1 vor dem Füllwert tragen; Höhenlinien von 1,30 m im Saum stehen als Zahl daneben.

Prüfstein 6 (Stufen im Saum) unterscheidet zwei Fälle: Wo die Randpixel, die ein Paar erreichen, auf 0,5 m [G] gleich hoch sind und gleich weit über dem Grund liegen, ist die Stufe höchstens 0,25 m [G] plus die Stufe des Grunds an derselben Stelle. Sonst (Hafen mit gebaggertem Becken vor der Kaje, Warft am Ufer) höchstens der größere der beiden Unterschiede; die Zahl dieser Paare, ihre größte Stufe und die Stelle der größten Überschreitung stehen im Attest.

Prüfstein 7 (Mittel nach oben) verlangt, dass jedes 10-m-Pixel über Land genau das auf float gerundete Mittel trägt; der Abstand zum Mittel in double steht daneben (in den Dünen von Amrum bis 34 m Höhe liegen benachbarte float-Werte 3,8·10⁻⁶ m auseinander).

## Grenzen

- Grund und Land nur in UTM 32 mit quadratischen Pixeln; geographische Quellen (EMODnet, GEBCO) nur in der Stufe 100 m und in G0.
- Die Korrektur nach NHN gilt in der Hülle von G1; außerhalb bleibt GEBCO in G0 in Küstennähe auf LAT (bis zum Abgleich der Tide in P5).
- Geschlossenes Land ist für G0–G2 ein Block von +10 m [G]; Sturmfluten über Landesschutzdeiche kann erst P12 rechnen.
- Der Saum ist gestaltet [G]: Er verbindet Land und Grund, er misst nichts.
- Bauwerke kennen nur Deiche, die OSM als Linie führt; ein Deich ohne Linie bleibt im Inneren von Zellen unsichtbar (Langeneß, 12.4). Ein Ring, den der Rand des Gitters schneidet (Pellworm), ist kein Ring; in G2 ist Pellworm geschlossen.
- Die Vorgabe der Tide ist eine Partialtide aus einem Modell (EasyGSH 2015), keine Messung und keine Rechnung des Renderers; sie hält den Rand fest (keine Abstrahlung) und wird mit P5 ersetzt.
- EOT20 hat 1/8°: Im Watt kommt die Tide dort 10–35° zu früh (Befund 12.6); es taugt für den Rand von G0 im tiefen Wasser, die Küste rechnet die Kette.
- Die Analyse der Pegel kennt nur die Tiden von `core.astro.Partialtide` (ohne L2); bei langen Lücken (Dagebüll, drei zu 8–11 h) weicht M2 darum bis 5 % von UTide mit 14 Tiden ab. Ohne Lücken unter 1 %.
- ERA5 aus dem Climate Data Store braucht ein Konto; der laufende Monat liegt etwa fünf Tage zurück (ERA5T) und wird nach zwei bis drei Monaten durch ERA5 ersetzt.
- Die Rauheit ist gestaltet [G]: Klassen aus Karten, n nach Tabelle. Außerhalb von DTK5 und gemessenem Land entscheidet die Höhe; eingedeichtes Land ohne OSM-Fläche (Pellworm) bekommt so Watt oder Sand.

- **Schritt `morpho` (P9c, nur mit `--schritte morpho`):** Bathymetrie der EasyGSH-DB von 1996 und 2016 (`bathymetrie\easygsh_bathymetrie\<Jahr>\*.tif`, 10 m) über der Hülle von G3 auf 20 m nach `pruef/boden_1996.txt` und `boden_2016.txt` (`core.gitter.Rasterfeld`, m NHN).

**Schritt `gestalt` (P12b, F132–F134 A).** Die Gestalt des Geländes für das Bild (`Gestaltkarte`), läuft nur, wenn
er genannt ist (`--schritte gestalt`). Braucht `gelaende/`, `rauheit/1m` und die OSM der Szene (`gestalt.osm`, Vorgabe
`bauwerke.osm`). Im Gebiet `gestalt.gebiet` (sonst `gebaeude.gebiet`, sonst `land.gebiet`): Karte zu 0,5 m in Kacheln
zu 500 m, nur wo Land ist. Grund aus den Klassen der Rauheit (Fenne, Salzwiese, Deich, Warft, Deckwerk); Fethinge
(`natural=water` ohne Art, `pond`, `reservoir`, `basin`) gefüllt, Stand = Median des Ufers − 0,3 m (`gestalt.unter_ufer`,
F134 A); Gräben (2 m) und Priele (3 m) als Striche, Stand je Stück aus dem Ufer; Wege zuletzt, Breite und Belag aus
den Tags, sonst nach der Art [G]. Körper (F133 A): `groyne` ohne Material oder aus Holz = Lahnung (Pfähle alle 0,8 m in
zwei Reihen, 0,3 m im Watt; dort kein Deckwerk in der Karte), `boulder` und `breakwater` = Steinbuhne, Fläche `pier` =
Anleger (`gestalt.anleger`), Linie `pier` = Steg. Ablage `gestalt/` (gestalt.txt, Kacheln `E…_N….rg8`,
pruefung.txt) und `bau/koerper.bin`. Prüfung: `demo.GestaltCheck --gebiet <ordner>` (Prüfsteine 7, 11, 14 von P12b).

**Schritt `gehoelz` (P12c, F136 A, F137 A).** Die Gehölze aus dem bDOM des LVermGeo SH (Oberfläche, 20 cm; Quelle
`bdom` des Kartenwerkzeugs, `gehoelz.bdom`, Vorgabe `hoehe\bdom`) minus DGM1 und dem NDVI des DOP20 (`Gehoelzkarte`);
läuft nur, wenn er genannt ist (`--schritte gehoelz`). Je Kachel ein Raster zu 0,4 m mit 20 m Rand aus den Nachbarn:
Pflanze, wo die Höhe über dem Boden ≥ `gehoelz.hoehe` (1 m) und der NDVI ≥ `gehoelz.ndvi` (0,15) und kein Haus ist
[G]; Kronen aus den Gipfeln des geglätteten nDOM, vom höchsten an gewachsen (höchstens 0,5 m bergauf, 10 m weit), je
Krone acht Halbmesser; Art nach Höhe und Nässe [G] (Esche, Schwarzerle, Eberesche, Salweide, Holunder, Hundsrose).
Ablage `bau/gehoelz.bin` (`core.szene.Gehoelz`), `bau/gehoelz.txt`, `bau/gehoelz_pruefung.txt`. Prüfung:
`demo.GehoelzCheck --gebiet <ordner>` (Prüfstein 17, Teil der Daten).

**Schritt `zonen` (P13b, F151–F153 A).** Die Zonen der Salzwiese (`Zonenkarte`) aus der Statistik der Überflutung des Laufs B1 (`zonen.ueberflutung`, Vorgabe `_p13\ueberflutung`; `com.dan.lauf.demo.VegetationLaufCheck --teil statistik`), dem DGM1 und der Gestalt: auf Fenne und Salzwiese je Meter Höhe minus MThw (bilinear aus G2), Zone nach `zonen.pionier` (−0,40) und `zonen.oben` (+0,35); die Fenne ohne Tidezone als Zone 4 (F155 A, `zonen.fenne = ohne_tide`; `tide` zoniert sie nach der Höhe wie die Salzwiese); läuft nur, wenn er genannt ist (`--schritte zonen`). Ablage `vegetation/ueberflutung.*` (Kopie der Statistik) und `vegetation/zonen/` (`core.szene.Zonen`). Prüfung: `demo.ZonenCheck` (Prüfstein 11 MThw gegen den Median der Hochwasser der EasyGSH-DB 2015 ± 0,10 m; 12 Überflutungsdauer der EasyGSH je Zone zur Auskunft; 5 NDVI des DOP20 auf dem Vorland Pionier < unten ≤ oben; der Anteil je Klasse ist seit F155 A Auskunft), Attest `docs/status/P13b_Zonen.md`.

## Seit P13c: Schritt brandung

`Kantenkarte`: Böschungen steiler als 1:15 über 12 m, mindestens 0,5 m hoch, Fuß unter 2,5 m NHN, ohne Gräben und Doppelte,
aus Gelände (Raster 2 m) und Gestalt (Deckwerk vor Warft vor Deich). Gebiet `brandung.gebiet` (Vorgabe `gestalt.gebiet`).
Ablage `brandung/kanten.txt` (`core.szene.Kanten`). Läuft nur, wenn er genannt ist.

