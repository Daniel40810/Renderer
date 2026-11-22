# com.dan.lauf – Läufe über ein Gebiet

**Stand: Teil B (16.11.2026), Version 0.3.** Seit Teil B (Werkbuch HalligHooge Teil B, B.3; F70 A) liegt hier auch die Bühne: der allgemeine Teil einer Anwendung über einem Lauf. Ein Projekt braucht dazu nur ein Fenster, eine Szenendatei und die Bedienung (Beispiel: `HalligHooge`).

**Stand davor: P6e (14.11.2026).** Neue Schicht nach F29 A (Werkbuch HalligHooge, Teil A, Abschnitte 4 und 12.6): verdrahtet die Module eines Gebiets am Bus und führt sie im Takt. Generisch – Gebiet, Gitter und Zeit kommen aus der Einstellung, nichts davon ist Hooge; derselbe Lauf taugt für Lituya oder Rungholt, sobald es dort ein Gebiet gibt.

## Schicht

```
com.dan.lauf          core + core.gpu + alle physics und render        Lauf, Verdrahtung, Bild am Lauf
com.dan.lauf.demo     dazu FStyle                                       Prüfungen
```

Kein Modul kennt diese Schicht. `check-packages` prüft sie gegen die übersetzten Klassen aller Module darunter.

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Lauf` | baut die Gitter (Packung, Bauwerke, G4 aus dem Stapel), die Module `antrieb`, `tide`, `hydro` und weitere Leser, verdrahtet den Bus, setzt alle auf die Zeit 0 |
| `Lauf.Einstellung` | Gebiet, Start (UTC), Stufen (`"G0 \| G1 \| G2 G3 G4"`), mit oder ohne Antrieb, Gitter mit Gleichgewichtstide (seit P5d nach F33 A alle), Prozessor oder Karte, Kopplung, Anlauf, Mittelwasser, G4, G3 als Fenster (`fenster3`, seit P5d), Windschub (`schub`: `WU`, `ERA5`, `CHARNOCK`; seit P5e, Vorgabe nach F34 B `CHARNOCK`; `LaufCheck` Teile 1–8 rechnen mit `WU`) |
| `Lauf.Einstellung.seegang` (seit P8b) | Seegang aus, auf der Karte oder dem Prozessor: Modul `waves` über G2w (500 m) und G3w (100 m, über dem G3 des Laufs, also auch dem Fenster); `seegangStuetzen` (28 × 36), `seegangAb` (davor null), `seegangKopplung` (1 200 s). Braucht `antrieb/seegang.txt` (Aufbereitung, Schritt `seegang`). Mit Archiv legt `ArchivModul` (0.2) `waves.hs`, `.tp`, `.dir`, `.mss`, `.diss` in `waves_G2w/`, `waves_G3w/` ab, alle 20 min; `Archivquelle` und `Bild` geben sie als `Seegangsnetz` ans Wasser. Seit P8c `seegangRueckwirkung` (Vorgabe an): hydro liest auf G2 und G3 `waves.force` |
| `Lauf.Einstellung.sediment` (seit P9b, Lauf 0.4) | Sediment aus, auf der Karte oder dem Prozessor: Modul `sediment` (`physics.sediment.SedimentModul`) auf dem Gitter `sedimentGitter` (G3, auch als Fenster), wie hydro es rechnet; liest den Seegang, wenn es über dem Gitter eins des Seegangs gibt (G3w); Boden am Anfang aus `gebiet/sediment` (`core.gitter.Kornfeld`, Aufbereitung Schritt `sediment`), sonst überall Sand 0,2 mm; `sedimentAb` (rechnet ab dieser Modellzeit), `sedimentHintergrund` (Schlick am Rand, 20 g/m³), `bodenKopplung` (Boden im Archiv alle 1 200 s). Der Kern bildet `bed.dz` aus dem Beitrag des Sediments (`core.bus.Verbund`). Im Archiv (`ArchivModul` 0.3): `sediment_<Gitter>` mit `sed.c` (Sand, Schlick; float16) zu jedem Stand und `boden_<Gitter>` mit `bed.ripple`, `bed.mud` (float16), `bed.dz` (float32); `Archivquelle.sediment(...)`, `.boden(...)` lesen sie |
| `demo.SedimentLaufCheck`, `demo.SedimentFeldCheck`, `demo.SedimentBildCheck` (seit P9b) | Prüfsteine von P9b, Teil Lauf: Teil 1 Sturm und ruhiger Tag mit Seegang und Sediment ins Archiv (`_p9b/archiv`, `_p9b/archiv_ruhig`; `--lauf sturm\|ruhig\|beide`, `--fenster3`): Verdrahtung, Tiefe aus der Kontinuität gegen V(η)/Fläche, Massenbilanz, Rechenzeit gegen hydro; ein Lesemodul `pruefung` nimmt in `<archiv>/pruef_sediment` je Tide den größten Flut- und Ebbstrom und die Kornschubspannung des Stroms auf und für 150 × 120 Zellen den Eingang des Sediments der letzten drei Stunden. Teil 2 aus den Archiven: Karte gegen Prozessor mit dem aufgenommenen Eingang, Feldvergleiche gegen EasyGSH 2015 und Sentinel-2, Rippel, Status-Bild `P9b_Sediment.png`. Teil 3 Bild aus dem Archiv des ruhigen Tags: ein Netz mit überall 30 g/m³ gegen das bisherige Bild, eine Stufe der Tafeln gegen den bisherigen Weg, Bilder/s, Status-Bild `P9b_Bild.png` (Fahnen von oben, Rippel im Watt). Attest `docs/status/P9b_Sediment.md` (nur Teil 1: `P9b_Sediment_Lauf_<Lauf>.md`). Für die Arbeitsumgebung: `--prozessor`, `--seegang aus`, `--sediment prozessor`, `--start`, `--ab`, `--ende`, `--probe` |
| `Bild` (seit P9b) | speist dazu das Sediment ein, wenn die Quelle eines hat (`Bild.Quelle.sedimentGitter()`, `.sediment(...)`, `.boden(...)`; `Archivquelle` liefert es, live noch nicht): Trübung in zwei Ständen ins `Wasserbild`, den jüngsten Stand des Bodens ins `Gelaendebild`. `Szenario.schwebstoff` (`szenario.<k>.schwebstoff`): Schwebstoff der Szene, wo kein Netz die Trübung gibt |
| `demo.StauCheck` (seit P8c) | Prüfsteine 1 und 2 von P8c: Stau am Strand, `Waves` und `Hydro` gekoppelt gegen die stationäre Lösung derselben Bilanz; Kraft auf der Karte gegen die Referenz. Attest `docs/status/P8c_Stau.md` |
| `demo.KimmungCheck` (seit P10a) | Prüfsteine 9–11 von P10a: Lichtstrahlen (`render.reference.Strahlgleichung`) durch das Profil von `physics.atmos` – Kreisbogen im linearen Gefälle der Brechzahl, gerader Strahl bei −3,42 K je 100 m, Grenzwinkel der unteren Spiegelung über warmem Wasser gegen die Erhaltungsgröße der geschichteten Kugel. Ohne Gebiet und ohne Karte. Attest `docs/status/P10a_Kimmung.md` |
| `Nachlauf` (seit P10b, 0.1) | rechnet `physics.atmos` zu einem abgelegten Lauf nach (F88 A): `Archivspeiser` gibt als Modul `hydro` die Stände `water.eta`, `water.h`, `water.wetAge` des Archivs auf den Bus, `Antrieb` mit `mitLuft` das Wetter, `AtmosModul` rechnet, `AtmosArchiv` legt ab: `atmos_<G3>/` mit `surf.wet` (float16), `surf.T` (float32) und `luft_<G3>s/` mit `air.n` (float32, n − 1 in 64 Höhen je Säule), zu den Zeiten der Stände. `Einstellung`: `gebiet`, `archiv`, `ziel`, `teiler` (10), `vorlauf` (86 400 s: vor dem ersten Stand läuft die Tide des Archivs um ganze Tage versetzt), `parallel`, `pruefen` (water.h am Bus gegen das Archiv). `Archivquelle` übergeht die Ordner `atmos_`, `luft_` bei den Gittern des Wassers |
| `demo.AtmosLaufCheck` (seit P10b) | Prüfsteine 1–8 von P10b Teil 1: Wetter (`antrieb/luft`), Antrieb gegen die Datei, Speisen, Wiederholbarkeit, Energiebilanz und Vorlauf, Flüsse über Wasser gegen ERA5, Rechenzeit, Nässe bei Niedrigwasser; dazu je zwei Stunden Wetter, Wasserstand, Nässe und die Kimm gegen die Normalatmosphäre (für die Wahl des Tags, F91). `--gebiet`, `--archiv a[,b…]`, `--name`, `--ohne-ablage`, `--auge`. Attest `docs/status/<name>.md`; Skript `tools/lauf_P10b_A.cmd` |
| `Bild` (seit P10b) | liest von der Quelle auch atmos (`Bild.Quelle.atmosGitter()`, `.naesse(...)`, `.luftGitter()`, `.luft(...)`; `Archivquelle` liest `atmos_<G3>`, `luft_<G3>s`): der jüngste Stand bis zur Szenenzeit – `surf.wet` ins Gelände (`nassAlsSaettigung`), und mit `blick(Blick, Seitenverhältnis)` je Bild die Luft im Blickfeld an `kimmung` (`render.air.Kimmung`): Mittel von `air.n` über die Säulen über Wasser im Blickfeld, Fläche = mittlerer Wasserstand dort, Sperrhöhe = halbe mittlere Wellenhöhe des feinsten Seegangs, mindestens 2 cm. `Buehne` und `BildCheck.Szene` zeichnen die Kimmung als letzten Teil; ohne atmos in der Quelle bleibt alles, wie es war |
| `demo.KimmungBildCheck`, `demo.KimmungKarteCheck` (seit P10b) | Prüfsteine 9–14 von P10b: ohne Karte die Tafel gegen die Strahlgleichung, die Normalatmosphäre, die untere Spiegelung an einer ebenen See mit Insel und der Durchgang gegen die Referenz mit gekrümmten Strahlen; auf der Karte der Kernel gegen den Prozessor, die Nässe, die Kimm im Gebiet, die Kosten. `ReferenzCheck --kimmung <archiv> --kimmung-zeit … --kimmung-blick …` rechnet die eine Ansicht gegen die Referenz (Prüfstein 13). Atteste `docs/status/P10b_Kimmung.md`, `P10b_KimmungKarte.md` (mit `.png`), `P10b_Referenz.md` (mit `.png`); Skripte `tools/lauf_P10b_B.cmd`, `lauf_P10b_C.cmd` |
| `demo.SeegangCheck` (seit P8b) | Prüfsteine von P8b: Teil 1 Sturm und ruhiger Tag mit Seegang ins Archiv (`_p8/archiv`, `_p8/archiv_ruhig`), Teil 2 Rand, Schachtel, Sturm gegen ERA5, Sättigung, NaN, Status-Bild `P8b_Seegang.png`; Teil 3 Bild (Stufe 2 ohne Seegang gegen Stufe 1, lange Wellen Karte gegen Prozessor, Bilder/s, `P8b_Bild.png`). Attest `docs/status/P8b_Seegang.md`. Seit P8c `--phase P8c` (Namen der Ausgaben), Prüfstein 7 gegen das Zeitbudget (48 s je 20 min), Hs/h über beide Archive, Feldvergleich gegen EasyGSH 2015 (`--easygsh`, Bild `P8c_Feld.png`) |
| `Bild` (seit P10d) | gibt dem Himmel (`Bild.himmel`, gesetzt von `Buehne` und `BildCheck.Szene`) je Bild `wasserAnteil`: Anteil der Zellen mit mehr als 5 cm Wasser im Gitter der Nässe im Umkreis von 15 km um das Auge, aus dem jüngeren Stand; daraus nimmt `render.air.Himmel` die Albedo der Luft (Messung d); seit F105 A dazu `wasserOptik` und `wind` für die Albedo des Wassers; seit F106 B gibt es den Anteil nur mit `ALBEDO_AUS_ANTEIL` (Vorgabe aus) weiter, der Himmel rechnet mit 0,23 fest |
| `demo.ReferenzCheck` (seit P10d) | `--sediment <archiv>` (`--sediment-zeit`, `--sediment-blick`, `--sediment-schwebstoff`): die Ansicht „Prielmündung im Ebbstrom“ mit Trübung am Ort in Echtzeit und Referenz und noch einmal mit überall gleicher Trübung (ein Viertel der Pfade); Prüfstein 17 (S8). Mit Sediment in der Quelle bekommt die Referenz jeder Ansicht das Netz (`Gelaendeszene.sediment`); seit F105 A rechnet P7h die Kugel mit der Albedo je Kanal, mit der der Himmel gemischt hat (Java ebenso gemischt), Prüfstein 19: Kugel mit der Albedo des Himmels gegen Szene, alle Bildpunkte mit Himmel, 1 ± 0,03 (bei Tag); das Statusbild heißt wie `--name` |
| `Bild` (seit P6e) | bringt die Stände einer `Bild.Quelle` in die Bildmodule: je Bild die zwei Stände um die Szenenzeit an die `Spiegel` von `render.water` (vorwärts nur der neue, rückwärts oder gesprungen beide), Zeit und Wind am Auge ans Wasser, die Nässe des jüngeren Stands von G3 ans Gelände |
| `BildModul` (seit P6e) | das Bild am Bus, live: Modul `render`, nur lesend, ohne Takt; liest `water.eta`, `water.h`, `water.wetAge` auf den Gittern in UTM und `air.u10`, `air.v10` auf G2, je Stand genau zu dessen Zeit |
| `ArchivModul` (seit P6e, F40 A) | Modul `archiv`, getaktet wie hydro: legt `water.eta`, `water.h` (float16) und `water.wetAge` (float32) von G2–G4 und den Wind (float16) ab, je Gitter ein Lauf nach `core.archiv` im Unterordner (`<ordner>/G3/`, `<ordner>/wind/`); liest im Intervall, damit hydro vorher bis zum Ende rechnet |
| `Archivquelle` (seit P6e) | ein abgelegter Lauf als `Bild.Quelle`: alle Stände, vor- und rückwärts, gesprungen |
| `demo.BildCheck` (seit P6e) | Prüfung von P6e ohne Fenster: Teil 1 Lauf mit Archiv, Teil 2 aus dem Archiv (Prüfsteine 3, 5, 6, 10), Teil 3 live (8, 9) |
| `Buehne` (seit Teil B) | alles, was ein Projekt für das Bild über einem Lauf braucht: Gerät, Fläche auf einer AWT-Leinwand, Rahmen, Himmel, Gelände, Wasser, `Bild`, Uhr und Bildschleife; Wünsche von außen (`szenario`, `ansicht`, `zeit`, `zeitraffer`, `rueckwaerts`, `pause`) gelten ab dem nächsten Bild, `zustand()` gibt den Stand je Bild; ohne Fenster Standbilder auf dem Weg von `BildCheck` |
| `Szenen`, `Ansicht`, `Szenario` (seit Teil B) | die Szenendatei eines Projekts (Format des Rezepts): Gebiet, Pegel, benannte Blicke (Höhe über Boden oder NHN, Richtung als Azimut oder zur Sonne) und benannte Quellen (Archiv oder live) |
| `Steuerung` (seit Teil B) | Kamera mit Maus und Tasten auf der Leinwand |
| `Pegelreihe` (seit Teil B) | Wasserstand an der tiefsten Zelle im Umkreis eines Orts über alle Stände einer Quelle, Sonnenhöhe je Stand, Marken für Hoch- und Niedrigwasser, Zeiten über einer Höhe; live wächst sie |
| `demo.BuehneCheck` (seit Teil B) | Prüfung der Bühne ohne Fenster: Szenendatei, Pegelreihe gegen `BildCheck.zeiten`, Standbild der Bühne gegen den Weg von `BildCheck` |
| `demo.BildDemo` (seit P6e, seit Teil B auf der Bühne) | Demo der Bühne ohne Projekt im FFrame: Szenario und Ansicht aus einer Szenendatei, Kamera, Zeitraffer, vor und zurück |
| `demo.ReferenzCheck` (seit P7c) | die Referenz (`render.reference`) gegen das Echtzeitbild in fünf Ansichten aus dem Archiv: Prüfsteine 6–10 von P7, Zerlegung nach Himmel, Gelände und Wasser, Wassersäule für F55 |
| `demo.LaufAuszug` (seit P6e) | Auszug für die Arbeitsumgebung: G0–G2, Tide, Antrieb, G3 als Fenster (Packung, Kanten und Siele relativ zum Fenster) |
| `demo.LaufCheck` | Prüfung von P5c ohne Fenster |
| `demo.TideCheck`, `demo.TideBild` | Prüfung der Tide (P5d): Tide allein über das Fenster der Pegel, Sturmflut mit ERA5 und G3 um Hooge; Status-Bild `P5_Tide.png`. Seit P8c `--seegang`: die Sturmflut ohne Seegang (Vergleich) und mit Seegang und Rückwirkung, dazu je Pegel und am Hafen von Hooge der Beitrag des Seegangs zum Scheitel; `--name` für Attest und Bild (z. B. `P8c_Tide`) |

## Benutzung

```java
Lauf.Einstellung e = new Lauf.Einstellung();
e.gebiet = new File("D:\\Daten\\HalligHooge\\gebiet");
e.start = Instant.parse("2026-10-25T12:00:00Z");
e.karte = true;                                  // hydro auf der Karte
e.stufen("G0 | G1 | G2 G3 G4");                  // Vorgabe: G0 | G1 | G2
try (Lauf l = Lauf.bauen(e)) {
    l.bis(6 * 3600);                             // Modellzeit in s seit dem Start
    double b = l.bilanz(2);                      // Bilanz der Kette
}
```

Mit Bild und Archiv (seit P6e):

```java
e.bild = true;                                   // Modul render am Bus (live)
e.archiv = new File("D:\\Daten\\HalligHooge\\_p6\\archiv");   // Modul archiv: G2–G4 und Wind
try (Lauf l = Lauf.bauen(e); Wasserbild w = …; Gelaendebild t = …) {
    Bild b = new Bild(l.bild, l.gebiet, w, t);   // oder new Bild(new Archivquelle(ordner), …)
    l.takt.laufen(uhr);
    // je Bild: double ts = l.takt.fuehre(uhr, dt); b.zu(ts, blick.ost, blick.nord); rahmen.bild(…)
}
```

Vorgaben [G]: Kopplung 300 s für alle Module, Anlauf 3 h, ruhender See bei 0 m NHN, Gleichgewichtstide auf allen Gittern (F33 A, seit P5d; in P5c G0 und G1).

## Prüfung

```bash
java -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BildCheck --gebiet D:\Daten\HalligHooge\gebiet --archiv D:\Daten\HalligHooge\_p6\archiv [--archiv-ruhig D:\Daten\HalligHooge\_p6\archiv_ruhig] [--nur 1,2,3] [--neu]
java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BildDemo --szenen ..\HalligHooge\szenen\Hooge.szenen [--szenario sturm] [--sekunden 20]
java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BuehneCheck --szenen ..\HalligHooge\szenen\Hooge.szenen [--nur 1,2,3]
java -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet D:\Daten\HalligHooge\gebiet [--archiv D:\Daten\HalligHooge\_p7\archiv] [--archiv-ruhig D:\Daten\HalligHooge\_p7\archiv_ruhig] [--pfade 256] [--nur 12345]
```

`BildCheck` (P6e): Teil 1 rechnet G0 | G1 | G2 G3 (G3 als Fenster um Hooge, 600, 850, 500 × 400) vom 23.10.2026, 00:00 UTC (wie die Sturmflut in `TideCheck`, damit sich der Stau aufbaut) bis 26.10., 00:00 UTC und legt ab 25.10., 06:00 UTC ins Archiv (`--ab`; liegt es schon, wird es nur gelesen); dazu den ruhigen Tag (F48 A): vom 28.10., 00:00 bis 31.10., 00:00 UTC gerechnet, der 30.10. abgelegt in `archiv_ruhig` neben dem Archiv (`--archiv-ruhig`, `-` ohne); Teil 2 prüft aus den Archiven Prüfstein 5 (Wasserlinie mit den Ständen von hydro; Sturm: Niedrigwasser, halbe Tide, Hochwasser, Landunter; ruhiger Tag: Niedrigwasser, halbe Tide), 6 (Zeit, Wechsel des Intervalls), 3 in vier Ansichten (Deich an der Westspitze bei Niedrigwasser des ruhigen Tags und bei Hochwasser des Sturms, Vogelblick im Landunter, Südwestdeich zur Abendsonne des ruhigen Tags) und schreibt `docs/status/P6_Bild.png` (Prüfstein 10); Teil 3 rechnet live mit dem Bild am Bus in 1080p: erst das Gelände für das feste Auge ganz geladen, 10 Bilder zum Anlaufen, dann `--bilder` Bilder, jedes mit Gelände (Prüfstein 8, auf llvmpipe und mit verlangter Validierung nur Auskunft; 9 mit `-Dcore.gpu.validierung=true`, braucht die Schicht des Vulkan SDK). Attest `docs/status/P6_Bild.md`. Für die Arbeitsumgebung: `--start`, `--ende`, `--anlauf`, `--breite`, `--hoehe`, `--bilder`, `--ansicht-breite`, `--ansicht-hoehe`, `--prozessor`; ein Auszug von `LaufAuszug` (Zeile `auszug` in `gebiet.txt`) läuft ohne Fenster, weil dort G3 das Fenster ist.

`ReferenzCheck` (P7c): fünf Ansichten aus den Archiven (Vorgabe `_p7/archiv` und `_p7/archiv_ruhig` neben dem Gebiet, je zwei Stände): Westspitze bei Niedrigwasser (ruhiger Tag) und Hochwasser (Sturm), Vogelblick im Landunter, Abendsonne (ruhiger Tag), nah auf das Wasser bei Hochwasser. Je Ansicht das Echtzeitbild in 1920 × 1080 (`--faktor` 4), gemittelt auf die Referenz in 480 × 270 mit `--pfade` (Vorgabe 256) und 16 Wellenlängen je Pfad, beide durch dieselbe Belichtung, Stäbchen und Filmkurve. Prüfstein 6 (Treffer auf `Gelaende.hoehe` und η_Bild), 7 (Rauschen zwischen zwei Hälften der Pfade), 8 (ΔE₀₀ gegen das Echtzeitbild), 9 (Zeiten), 10 (`docs/status/P7_Referenz.png`: je Ansicht Referenz, Echtzeit, Karte des ΔE₀₀); dazu die Zerlegung nach Himmel, Gelände und Wasser, dieselben Maße in Blöcken zu 8 × 8 Bildpunkten und die Wassersäule (F55). Attest `docs/status/P7_Referenz.md`.

```bash
java -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.LaufCheck --gebiet D:\Daten\HalligHooge\gebiet [--karte] [--kette] [--stunden 6] [--start 2026-10-25T12:00:00Z]
```

Teile: 1 Katalog und Verdrahtung (mit Gegenproben), 2 tide, 3 antrieb, 4 Takt, 5 Lauf (Bilanz, NaN, `water.q` gegen die Masse, Pegel zur Auskunft, Zeiten), 6 Wiederholbar, 7 Tide allein, mit `--kette` 8 Lauf mit G2 ↔ G3 ↔ G4 über 1 h, 9 Windschub vom Bus (seit P5e: ERA5 und Charnock, je verdrahtet und gegen eine eigene Rechnung auf G0 und G2; Gegenprobe ohne die Größen). Attest `docs/status/P5_Lauf.md` bzw. mit `--karte` `P5_LaufKarte.md`.

```bash
java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.TideCheck --gebiet D:\Daten\HalligHooge\gebiet --karte [--nur tide|sturm] [--tage d] [--ohne-hooge] [--reihen pegel.csv]
```

Teil 1, Tide allein: G0 | G1 | G2 ab 10.10.2026 12:00 UTC, 48 h Vorlauf, dann das Fenster der Pegel (rund 30 Tage); harmonische Analyse je Zelle von G0 (`Harmonik.Sammler`) und an den Pegeln, Lauf und Beobachtung über dieselben Zeiten. Prüfsteine 1 (M2-Amphidromien gegen EOT20 ≤ 50 km), 2 (M2 an Helgoland, Husum, Wittdün, Pellworm ±10 %, ±10°), 3 (RMSE gegen die astronomische Tide < 15 cm an Husum und Wittdün); zur Auskunft M2 über der Nordsee gegen EOT20 (RMS und Mittel je Teil: Deutsche Bucht, Südliche Bucht, mittlere und nördliche Nordsee) und M2, MThw, MTnw an weiteren Pegeln. Teil 2, Sturmflut: 23.10. 00:00 bis 26.10. 06:00 UTC mit ERA5, G3 als Fenster um Hooge; Prüfstein 4 (Scheitel an vier Pegeln ±30 cm), Hooge zur Auskunft. In beiden 5 (Bilanz) und 6 (kein NaN). Attest `P5_Tide.md` (Karte) bzw. `P5_TideReferenz.md`, Status-Bild `P5_Tide.png`. `--tage`, `--tide-start`, `--stufen`, `--sturm-start`, `--sturm-ende` kürzen für Proben. Seit P5e: `--schub wu|era5|charnock` (Windschub im Lauf der Sturmflut, Vorgabe seit F34 B `charnock`), `--zerlegen` (ein zweiter Lauf ohne Antrieb; am beobachteten Scheitel Tide und Stau, Lauf gegen Beobachtung, zur Auskunft), `--reihen <datei>` schreibt auch die Reihen der Sturmflut (`.sturm.csv`, mit `--zerlegen` `.ohne.csv`); mit `--ohne-hooge` gilt `--stufen` auch für die Sturmflut. Liegt im Gebiet `wind/` (Aufbereitung, Schritt `wind`), vergleicht Teil 2 vorab den Wind von ERA5 mit dem der Stationen des DWD (Auskunft, F36 A).

## Seit P9c (Lauf 0.5, Archiv 0.4)

- `Lauf.Einstellung`: `bodenRueck` (hydro verschiebt die Tabellen des Gitters des Sediments um `bed.dz`), `bodenRand`, `bodenDeckel`, `morphFaktor`, `auflage`, `spirale`, `rauheitSediment` (meldet den Verbund `surf.z0` an), `bodenAnfang` (Boden aus dem letzten Stand eines Archivs).
- `ArchivModul`: in `boden_<Gitter>` liegt dazu `bed.fluff` (float32); `Archivquelle.auflage(ts, ziel)` liest sie (ältere Archive haben sie nicht).
- `demo.MorphoCheck`: Prüfsteine 1–8 von P9c (Attest `docs/status/P9c_Kern.md`); 1–7 ohne Karte und ohne Gebiet, 8 mit `--gebiet`.
- `demo.SedimentLaufCheck`: rechnet auch die Läufe der Morphologie (`--faktor`, `--boden-aus`, `--sediment-ab`, `--seegang-gitter`, `--rauheit`, `--attest`; `--p9b` wie in P9b). Skripte `tools/lauf_P9c_A.cmd`, `lauf_P9c_M1.cmd`, `lauf_P9c_M2.cmd`.

- **Teil 2 (P9c):** `Bild.Quelle.bodenBewegt()` (Archiv: aus dem Kopf des Laufs; live: aus der Einstellung); `BildModul` 0.2 liest live `sed.c`, `bed.ripple`, `bed.mud`, `bed.dz` (Szenario mit `sediment = an`, `Szenario.sediment`); `demo.MorphoFeldCheck` (Prüfsteine 13, 14, 15: Japsand, Auftrag und Abtrag gegen EasyGSH 1996–2016 aus `gebiet/pruef/boden_1996.txt`, `boden_2016.txt`, Tidenhub; Attest `P9c_Feld.md`); die Status-Bilder von `SedimentBildCheck` und `SedimentFeldCheck` heißen nach dem Attest (`--attest P9c_M` gibt `P9c_M_Bild.png`); `tools/lauf_P9c_C.cmd`.

## Seit P10c Teil 1 (Nachlauf 0.2, AtmosArchiv 0.2)

- `Nachlauf.Einstellung`: `regen` (atmos liest `air.rain`, F97 A), `aeolian` (`physics.aeolian.AeolianModul` rechnet im
  selben Zug vom ersten Stand an; der Kern bildet `bed.dz` aus `bed.dz.aeolian` allein), `faktor` (F98 A: 1).
- `AtmosArchiv`: mit `mitAeolian` dazu `<archiv>/aeolian_<Gitter>/` mit `bed.dz` (float32; hier allein der Anteil des
  Winds, ein Kopplungsintervall hinter dem Modul) und `aeol.q` (float16), alle 1 200 s.
- `demo.AeolianLaufCheck`: Prüfsteine 11–15 von P10c. Lauf 1 ohne Regen in einen Prüfordner (Vorgabe
  `<archiv>_ohneRegen`, bleibt liegen) und Byte für Byte gegen atmos im Archiv; Lauf 2 mit Regen ins Archiv.
  `--vergleich <ordner>` nennt, wogegen Prüfstein 11 misst, sobald das Archiv atmos mit Regen hält.
- `demo.AtmosLaufCheck` rechnet atmos jetzt mit Regen und ohne Flugsand. Skript `tools/lauf_P10c_A.cmd`.

## Seit P10c Teil 2 (Bild)

- `Archivquelle`: Wassergitter ist, was `water.eta` hält (davor galt jeder unbekannte Unterordner als Wasser – mit
  `aeolian_<Gitter>` öffnete das Archiv nicht mehr). Liest `aeolian_<Gitter>`: `aeol.q` (`flugGitter`, `flugZeiten`,
  `flugsand`) und zählt den Anteil des Winds zu `bed.dz` (`boden`).
- `Bild`: `Quelle.flugGitter()`, `flugZeiten()`, `flugsand(ts, q)`; der jüngste Stand geht an
  `Gelaendebild.flugsand` (Sandfegen). `mitFlugsand()`, `flugGitter()`, `flugsand()`.
- `demo.SandfegenKarteCheck`: Prüfstein 17 (Deckung gegen die Handrechnung, von oben, Kosten), Status-Bild
  `docs/status/P10c_Sandfegen.png`. Skripte `tools/lauf_P10c_A2.cmd`, `lauf_P10c_B.cmd`.
- Projekt: Szenario `flugsand` (17.10.2026, 15:40 UTC, Zeitraffer 10), Ansicht `sandfegen`.

## Seit P11b: das Foto (`Foto` 0.1)

- `Foto`: die Ansicht in Referenzqualität, gerechnet mit der Referenz Stufe 1 (F112 B) über denselben Ständen wie das
  Echtzeitbild (`Foto.referenz`, seit P11b auch der Weg von `demo.ReferenzCheck`: Seegang, Sediment, Kimmung). In Runden
  zu einem Pfad je Bildpunkt; gerade und ungerade Runden sind die Hälften für das Rauschen. Ende bei Rauschen je
  Bildpunkt ≤ 1, nach der Zeitgrenze (180 s [G]; eine Runde beginnt nur, wenn sie noch hineinpasst; mindestens zwei
  Runden), nach `rundenMax` oder durch `abbrechen()`. Eigener Pool mit allen Kernen außer einem. Abbildung wie das
  Echtzeitbild (Belichtung zur Zeit des Fotos, Stäbchen, Filmkurve). Ablage `lege(ordner)`: PNG, PFM (float32, lineares
  sRGB in cd/m²) und Beschreibung.
- `Buehne.foto(titel, breite, hoehe, ablage)`: hält die Zeit an, baut mit dem nächsten Bild die Referenz und rechnet in
  einem eigenen Faden; am Ende legt sie ab. Solange ein Foto rechnet, steht die Zeit, Sprünge gelten nicht; ein Wechsel
  des Szenarios bricht es ab. `foto()` gibt den Stand, `fotoAbbrechen()`. Ohne Fenster: `foto(blick, t, titel, b, h)`.
- `demo.FotoCheck`: Prüfsteine 9–12 von P11b (Attest `docs/status/P11b_Foto.md`), je Szenario aus dem Archiv ein Foto
  (`--ablage`). Skript `tools/lauf_P11b_B.cmd`.

## Seit P11c1: die Strömung aus dem Nachlauf (`Stroemung` 0.1)

- `Stroemung`: hydro rechnet G3 eines abgelegten Laufs noch einmal, allein, mit dem Rand einseitig aus G2 im Archiv
  (`HydroModul.vorgabe0`); Anfang aus dem ersten Stand in Ruhe; Wind aus ERA5 wie im Lauf (Schub nach dem Kopf des
  Archivs), ohne Anlauf; ohne Gleichgewichtstide und ohne Kraft des Seegangs (F116 A); mit `boden_G3` der Boden je
  Intervall (`HydroModul.bodenquelle`), ohne Rauheit aus Rippeln. Ablage `<archiv>/hydro_G3/`: `water.u`, `water.v`
  (float16), `water.q` (float32, Mittel über das Intervall), `water.eta` des Nachlaufs (float32), zu den Zeiten des
  Archivs. Prüft nebenher Prüfstein 15 (Wasserstand gegen das Archiv) und 16 (Bilanz des Teilbeckens aus `qSumme`).
- `Archivquelle`: `hydro_<Gitter>` ist kein Wassergitter des Laufs; `stroemungGitter()`, `stroemungZeiten()`,
  `stroemung(ts, u, v)`, `fluss(ts, q)`, `stroemungEta(ts, eta)`. `Bild.Quelle` hat die vier ersten als Vorgaben.
- `demo.StroemungLauf`: der Nachlauf über eine Liste von Archiven, Attest `docs/status/<Name>.md`; meldet jede Stunde
  Szenenzeit eine Zeile; teilt Prüfstein 15 zur Auskunft in Randstreifen (10 Zellen), innen flach (Tiefe im Archiv
  unter 0,5 m) und innen tief und nennt den Ort der größten Abweichung. `--probe`: Exit 1 nur bei Fehlern. Skripte
  `tools/lauf_P11cC1a.cmd` (P6, P8c, P9b), `lauf_P11cC1b.cmd` (P9c).
- Die Archive beginnen nicht bei 0 (nach dem Einschwingen, z. B. 172 800 s): `Stroemung` setzt die Uhr der Gitter
  (`Hydro.zeit`) auf den ersten Stand, bevor die Rechnung gebaut wird.

## Seit P11c2: der Messblick in der Bühne

- `Messquelle`: die Felder des Messblicks aus einer `Bild.Quelle` – Gitter der Strömung (`hydro_G3`) oder das feinste
  Wassergitter; je Stand u, v, η, h, dazu die Trübung (g/m³), wenn das Sediment auf demselben Gitter liegt.
- `Buehne.messblick()` legt den `render.analysis.Messblick` an (Ebenen und Art der Linien stellt der Aufrufer),
  `messblick(boolean)` schaltet ihn; aus ist das Bild bitgleich. Im Standbild laufen die Partikel über eine Spur vor.
  `bildzeiten(blick, t, n)` misst Bilder mit Zeitstempeln je Durchgang (Prüfung).
- `demo.MessblickCheck`: Prüfstein 19 (Kosten auf der Karte, Bilder/s, bitgleich aus) und die Bilder zur Sichtprüfung
  `docs/status/P11c2_Messblick_<n>.png`. Skripte `tools/lauf_P11cC2*.cmd`.

## Seit P11c3: Schnitt und Messblick in der Anwendung

- `Schnitt`: die Seite von `render.analysis.Querschnitt`, die das Projekt sehen darf (Enden, Länge, `Profil`, `Bilanz`).
- `Buehne`: `ort(fx, fy)` (erste Fläche längs des Sehstrahls: Gelände oder Wasser des Messblicks), `schnitt(a, b)`
  (weiß im Bild, auch bei ausgeschaltetem Messblick), `schnittWeg()`, `schnittProfil(s)`, `schnittReihe(s)` (Q(t) über alle
  Stände aus `water.q`), `messblickEbenen(...)`, `messHinweis(t)`, `messGitter()`, `validierung()`.
- `Messquelle`: `hinweis(t)` – ohne Strömung „nur Isolinien“, in den ersten 2 h „schwingt ein“, dazu die Zeilen aus
  `hydro_<Gitter>/hinweis.txt` (Stunden nach dem ersten Stand von, bis, Text; F118 A für M1b); `wasser`, `profil`,
  `durchfluss`.
- `demo.MessblickCheck`: seit P11c3 `--attest`, mit `-Dcore.gpu.validierung=true` die Fehler der Schicht je Probe (Zeiten
  dann nur zur Auskunft), Probe 5 Schnitt durch die Rinne südlich von Hooge im Ruhigen Tag mit Sediment.

## Seit P12a: Häuser

Hat das Gebiet `bau/gebaeude.bin`, legt `Buehne` das Bildmodul `render.bau.Hausbild` an und zeichnet es vor dem
Gelände (`List.of(haeuser, gelaende, wasser, himmel, kimmung)`): Die Häuser legen beim Zeichnen ihren Schatten ab, den
Gelände und Wasser lesen. `haeuser(boolean)` schaltet sie an und aus (die Anwendung: Knopf „Häuser“), mit
`-Dlauf.haeuser=aus` sind sie von Anfang an aus; aus ist das Bild wie ohne Häuser. Prüfung in der Szene:
`demo.HausCheck --szenen <datei>` (Prüfsteine 6, 9, 10 von P12a: bitgleich außerhalb der Häuser, Kosten, Bilder der
Warften `docs/status/P12a_Warften.png`).

**Xaver (P13a, seit 20.11.2026).** Der Lauf nimmt Wind, Druck, Schub und Seegang aus dem Zeitraum unter `antrieb/`, der seinen Start enthält (`Wetterfeld.lies(gebiet, name, start)`; Oktober 2026 in `antrieb/`, Dezember 2013 in `antrieb/201312/`); ebenso `Nachlauf` und `Stroemung`. Das Archiv der Sturmflut rechnet `demo.SedimentLaufCheck --start 2013-12-03T06:00:00Z --ab 2013-12-05T06:00:00Z --ende 2013-12-06T18:00:00Z`. Prüfung: `demo.XaverCheck --teil antrieb` (Lauf A1: Zeitraum vorhanden, Wind über der Deutschen Bucht) und `--teil scheitel,bild` (Lauf A2: Scheitel an den Pegeln gegen die Berichte von LKN-SH und BSH, Wasser auf der Hallig gegen die AG Halligen 2050, Freibord der Häuser, Stau an Husum; Bilder des Szenarios `xaver`), Attest `docs/status/P13a_Xaver.md`.

## Seit P12b: Gestalt

`gestalt(boolean)` schaltet die Gestalt an und aus (Karte im Gelände und Körper in `render.bau`; die Anwendung: Knopf
„Gestalt“), mit `-Dlauf.gestalt=aus` ist sie von Anfang an aus; aus ist das Bild wie in P12a. `hatGestalt()`,
`gestaltAn()`. Prüfung in der Szene: `demo.GestaltLaufCheck --szenen <datei>` (Prüfsteine 15 und 10 von P12b: Kosten
mit gegen ohne Gestalt, Bilder `docs/status/P12b_Gestalt.png`); Ansichten `fething`, `lahnungen`, `hafen`.

## Seit P12c: Bewuchs

Der Teil `render.bewuchs` (Gehölze und Halme) steht nach den Häusern und vor dem Gelände; er wird geladen, wenn das
Gebiet `bau/gehoelz.bin` hat. `bewuchs(boolean)` schaltet ihn an und aus (die Anwendung: Knopf „Bewuchs“), mit
`-Dlauf.bewuchs=aus` ist er von Anfang an aus; aus ist das Bild wie in P12b. `hatBewuchs()`, `bewuchsAn()`,
`bewuchsbild()`. Der Wind am Boden geht je Bild an den Teil; das Standbild hält die Zeit des Winds an. Prüfung:
`demo.BewuchsCheck` (Prüfsteine 17, 16, 9) und `demo.BewuchsLaufCheck --szenen <datei>` (Prüfsteine 18 und 10:
Kosten mit gegen ohne Bewuchs, Bilder `docs/status/P12c_Bewuchs.png`); Ansichten `gehoelz`, `halme`.

## Seit P12c3: das Foto mit Bauten und Gehölzen

`Foto.referenz(…, Hausbild, Bewuchsbild)` gibt der Referenz Häuser, Körper, Gestalt und Gehölze mit, wie sie im Bild an
sind (`render.reference.Bauszene`); die Bühne baut Fotos so. `tiefe()` gibt die Tiefe des letzten Standbilds (für
Prüfungen). `Bewuchsbild.tagFest` hält das Laub auf einem Tag fest. Prüfung: `demo.ReferenzBauCheck --szenen <datei>`
(Prüfsteine 8, 19, 9, 10 von P12c; Fotos der Hanswarft am Tag und nachts und der Gehölze, `docs/status/P12c_Foto.png`).

**Salzwiese (P13b, seit 20.11.2026).** `Einstellung.vegetation` (Vorgabe aus) meldet `physics.vegetation.VegetationModul` auf G3 vor sediment an, `surf.z0` als Verbund (Maximum) aus sediment und vegetation, hydro liest ihn auf G3 (`rauheitGitter`), sediment und aeolian lesen `veg.cover`; ebenso `Stroemung` und `Nachlauf` (dort auch atmos mit `surf.z0`). `Buehne.salzwiese(boolean)` schaltet die Zonen im Bild (Gelände und Halme) an und aus (die Anwendung: Knopf „Salzwiese“); an, solange nicht `-Dlauf.vegetation=aus`. Prüfung: `demo.VegetationLaufCheck --teil verdrahtung` (kleiner Bus mit hydro auf dem Prozessor und dem Verbund `surf.z0`; Schalter wie `rauheitGitter` gelten nur, wenn sie vor `bus.anmelden` stehen; die Salzwiese wird vor hydro und atmos angemeldet, weil `Takt.beginn` der Anmeldung folgt und beide `surf.z0` schon in `beginn` lesen können), `--teil statistik` (Lauf B1: G0–G2, 15 Tage ab 1.10.2026, Statistik nach `_p13\ueberflutung`), `--teil nachlauf` (Strömung auf dem Archiv von Xaver ohne und mit, Prüfstein 6) und `--teil bild` (Prüfsteine 7 und 14, Bilder Juli und Januar `docs/status/P13b_Salzwiese.png`), Attest `docs/status/P13b_Salzwiese.md`.

**Einblendungen (W2, Steuerhaus, seit 20.11.2026).** `Buehne.einblendung()` gibt die Einblendung der Bühne (`render.overlay`, mit der Krümmung des Geländes); sie liegt nach dem Messblick im Fenster und in `bildzeiten` über dem Bild, nie im Foto und nie im Standbild (`standbild(blick, t, true)` nur für Prüfungen). Ohne Kacheln oder ausgeschaltet bleibt das Bild bitgleich. `Buehne.Laden` meldet die Schritte beim Öffnen (`oeffnen(gebiet, leinwand, laden)`, `ohneFenster(gebiet, w, h, laden)`; Gebiet, Gerät, Fläche, Rahmen, Himmel, Gelände, Häuser, Bewuchs, Wasser, Brandung, Kimmung, Fertig mit steigendem Anteil bis 1). Für die Minikarte `wasserRaster(o0, n0, o1, n1, nx, ny)` (η des letzten Bilds, NaN trocken), `nassAnteil(…)` (Zellen über 5 cm im Gitter der Nässe, `Bild.nassAnteil`) und `nassGebiet()`; für die Legende `messLeiter()`, `messArt()`, `messStufen()`; im Zustand `windU`, `windV`. Die Stoppuhr im Fenster hat 48 Abschnitte (vorher 32). `demo.MessblickCheck`: Probe „morpho“ auf `japsand_oben` (W2-a).

**Kamera (W3, Steuerhaus, seit 20.11.2026).** `Kamera` (eine Methode, `Blick weiter(dt, szenenzeit, jetzt)`) führt den Blick, wenn sie gesetzt ist: `Buehne.kamera(Kamera)` (null: aus), `kamera()`. Die Bühne fragt sie je Bild nach der Szenenzeit des Bilds und vor dem Zeichnen; der Blick geht in die Steuerung (`steuerung.setze`), Breite und Höhe in Bildpunkten bleiben die der Fläche; die Maus übernimmt danach ohne Sprung. Gibt die Kamera null, gilt der Blick der Steuerung. Wirft sie, ist sie aus und `fehler()` meldet „Kamera: …“. Benutzer: `com.dan.steuerhaus` (Regisseur: Fahrten, Flüge zu Lesezeichen und Orten).

**Lange Bilder (W3b, seit 20.11.2026).** Dauert ein Bild der Bildschleife länger als `Buehne.LANG` (0,5 s), meldet die Bühne es in der Ausgabe mit seinen Teilen (vorher, `zu` nach `Bild.ZU_TEILE`, Auflagen, Karte) und vier Bilder später mit den längsten Abschnitten der Stoppuhr; `langesBild()`, `laengstesBild()`. `zuMessung()` gibt die Teile des längsten `Bild.zu` seit dem letzten Aufruf (für Prüfungen ohne Fenster), `fensterAbschnitt(name)` die Zeit eines Abschnitts der Stoppuhr im Fenster. Anlass: A47 (Werkbuch 118), das Bild stand nach einem Sprung in der Zeit um 10,5 h rund 18 s.

**Filmbild (W4, Steuerhaus, seit 20.11.2026).** `Buehne.filmbild(dt, zeichnen)` ist ein Schritt der Bildschleife ohne Fenster mit festem Takt: Szenario wechseln, Ansicht, Uhr mit Zeitraffer und Sprung, Kamera, `zu`, Auflagen (mit den Einblendungen, solange `filmMitEinblendung`), Rahmen. Wind der Halme und Flugsand laufen auf der Filmzeit (`filmZeit()`), nicht auf der Uhr des Rechners; das Gelände lädt im selben Faden. Dieselbe Folge gibt darum dieselben Bilder. `zeichnen = false` rückt nur Uhr und Kamera vor (Verschluss zu); das nächste Bild bekommt die ganze Zeit seit dem letzten als Takt des Rahmens. Nur aus dem Archiv; live wirft. `filmBytes()` gibt die Bildpunkte (RGBA8 sRGB), `speicher()` belegt und Spitze des Kontos. `ruhen(true)` hält die Bildschleife im Fenster an (eine Ausgabe rechnet auf derselben Karte). Benutzer: `com.dan.steuerhaus.ausgabe`.

**Sonde (W5, Steuerhaus, seit 20.11.2026).** `Sonde` hält die Reihen eines Orts über alle Stände einer Quelle: Wasserstand und Wassertiefe in der Zelle der `Pegelreihe` (Umkreis 0), Strömung (Betrag aus `Bild.Quelle.stroemungGitter`) und Wind (aus `luftGitter`), je als `Sonde.Reihe` (Name, Einheit, Zeiten, Werte; `bei`, `min`, `max`). `Sonde.aus(quelle, start, gebiet, name, ost, nord)`, `Sonde.ausArchiv(szenario, gebiet, …)` öffnet das Archiv selbst; `csv()`. `Buehne.sonde(name, ost, nord)` rechnet sie zur laufenden Quelle. `Pegelreihe.nassAlle()` gibt die Nässe aller Stände.

**Platzhalter in der Szenendatei (W5, seit 20.11.2026).** Werte dürfen `${name}` enthalten; eingesetzt wird `-Dszenen.<name>` oder der Schlüssel `name` derselben Datei (bis zu vier Runden). So steht der Ort der Daten nur einmal in der Datei (`daten = …`), und ein Abbild der App findet sie woanders.

**Bildzeiten (W5, seit 20.11.2026).** `bildzeitMerken(jetztNs, ms)` (ein Ring über 4096 Bilder, die Bildschleife schreibt ihn), `bildzeitVerlauf(sekunden)` und `fensterAbschnitte()` (die Abschnitte der Stoppuhr je Bild, absteigend): Futter für das Leistungs-Paneel des Steuerhauses.

**W5b (seit 20.11.2026).** `Bild.Quelle` hat `standZelle`, `windZelle`, `stroemungZelle`: eine Zelle zu einer Zeit (Vorgabe: den ganzen Stand lesen; `Archivquelle` liest mit `Archiv.wert` nur die Bytes der Zelle). `Pegelreihe` und `Sonde` lesen so je Stand wenige Werte statt ganzer Felder; `Buehne.sonde` öffnet für ein Archiv eine eigene `Archivquelle`, die Sperre und die gehaltenen Sätze der laufenden bleiben dem Bild. Das lange Bild (W3b) nennt seit W5b, woran `zu` hing (`Archiv.Messung`: Lesen aus den Dateien, Warten auf die Sperre, langsamster Satz) und die Zeit der anhaltenden Speicherbereinigung im Bild (A47, Werkbuch 128).

**Vorleser (W5c, seit 20.11.2026; A47, F221 A).** Eine externe Platte schläft ein, sobald die Stände im Dateipuffer liegen, und braucht beim nächsten Stand außerhalb bis zu 20 s (W5b: 19,46 s für einen Satz). Seit W5c liest ein eigener Faden je `Archivquelle` (`vorlesen(ts, zeitraffer, sekunden)`) die Stände aller Archive des Bilds voraus – rund `Buehne.VORLESEN_S` = 60 s Echtzeit beim Zeitraffer, mindestens drei Stände, höchstens 200 – und `bereit(ts)` sagt, ob die Stände um ts vorgelesen sind (ohne zu warten). Die Bildschleife fragt je Bild: nicht bereit, hält die Uhr an, das Bild zeichnet die letzte Zeit weiter (Kamera und Einblendungen laufen); `Zustand.datenWarten`, `datenGewartet`, `datenWarteZahl`. `Bild.Quelle` hat dazu `bereit` und `vorlesen` (Vorgabe: immer bereit, nichts vorlesen). Ausgabe und Standbild lesen wie bisher (warten, aber gleiche Bilder). Prüfung: `demo.VorleseCheck` mit nachgestellter Platte (`Archiv.platteSimulieren`, `-Dlauf.platte.schlaf=<nach s>:<dauer s>`).


## Seit X1 (Werkbuch HalligHooge 145)

| Klasse | Aufgabe |
|---|---|
| `Karte` | die Karte von oben in einem Rechteck (UTM) mit Schritt `zelle`: Albedo des Bodens (`core.szene.Boden`), Schummerung aus der Höhe (Sonne aus Nordwest, 35°, Überhöhung 3), Dächer und Körper der Gestalt (`core.szene.Bauten`) mit Schlagschatten; `wasser(Buehne, t)` gibt den Wasserstand eines Stands je Bildpunkt (über ein kleines Standbild), `bild(eta)` das Bild mit Wasser; Abfragen `kuppe`, `fuss`, `haeuser`. Für den Atlas des Steuerhauses |

## Seit Y2 (Werkbuch HalligHooge 153): Regler im Live-Lauf

`Buehne.regler(windFaktor, windDrehung, aufschlag, meeresspiegel)` setzt die Ziele für den Live-Lauf (true: ein Live-Lauf läuft und nimmt sie jetzt; sonst gelten sie, sobald einer beginnt): Faktor und Drehung (Grad, im Uhrzeigersinn) auf den Wind aus ERA5 im `Antrieb` (0.5), Aufschlag und Meeresspiegel zusammen als Wasserstand am Rand der innersten Stufe in hydro (`HydroModul.aufschlagZiel`, 0.7; im Live-Lauf G2). Die Module fahren die Ziele in der Modellzeit an: Wind × 2 und 180° je Stunde, Wasserstand 1 m je Stunde [G]. `reglerIst()` gibt {Faktor, Drehung, Wasserstand am Rand} (null ohne Live-Lauf). Ohne Ziele bleibt alles bitgleich. Der Rand von G0 liegt im Atlantik (−15° bis 13° O); ein Aufschlag dort wäre erst nach rund einem Tag an Hooge – darum der Rand von G2. Prüfung: `demo.ReglerCheck --gebiet …` (drei Läufe wie live: Basis, Aufschlag +1 m, Wind × 2 und 30° ab 3 h; Pegel Hooge und Wind bis 7 h; Attest `docs/status/Y2_Regler.md`).
