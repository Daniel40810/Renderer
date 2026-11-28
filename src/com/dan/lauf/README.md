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
| `Szenen`, `Ansicht`, `Szenario` (seit Teil B) | die Szenendatei eines Projekts (Format des Rezepts): Gebiet, Pegel, benannte Blicke (Höhe über Boden oder NHN, Richtung als Azimut oder zur Sonne; seit S3c-5 `ansicht.<name>.wasser`: mindestens so hoch über Spiegel plus Hs/2, Vorgabe 1,5 m, `Ansicht.hebe`; die `Steuerung` hält das Auge über dem Wasser, `Buehne.wasserUndSeegang`) und benannte Quellen (Archiv oder live) |
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

## Seit Seegang S0 und S1 (Seegang Werkbuch, SF6 A, SF13 A): der Seegang aus dem Nachlauf, feste Bilder

- `SeegangNachlauf` (0.1): `physics.waves` rechnet die Gitter des Seegangs eines abgelegten Laufs noch einmal (G2w,
  G3w wie im Archiv, Stützstellen aus dem Kopf), mit `water.h` aus G2 und G3 des Archivs, der Strömung auf G3 aus
  `hydro_G3` (`Stroemung`; auf G2 keine), Wind und Rand aus ERA5 wie im Lauf. Anfang [G]: 2 h vor dem ersten Stand
  ein JONSWAP je Zelle aus Hs, Tp und Richtung des ersten Stands (`Waves.jonswap`, s = 10), im Vorlauf das Wasser des
  ersten Stands. Ablage je Gitter in `<archiv>/seegang_<Gitter>/` zu den Zeiten von `waves_<Gitter>`: `waves.hs`,
  `.tp`, `.dir`, `.ubot`, `.mss`, `.diss`, `.sea` und `.swell` (je Hs, Tp, woher, Breite), `.dwc` (Schaumkronen,
  Rate), `.dbr` (Brechen und Kappung: `diss` − `dwc`), `.dbf` (Reibung, Rate); alles float16. Prüft nebenher Hs gegen
  das Archiv, Windsee und Dünung gegen m₀ und die Werte des Spektrums.
- `demo.SeegangNachlaufLauf`: der Nachlauf über eine Liste von Archiven, Attest `docs/status/<Name>.md` (Vorgabe
  `S1_Nachlauf`); Prüfsteine S1.1–S1.3; `--hoechstens`, `--vorlauf`, `--stuetzen`, `--prozessor`, `--probe`.
- `demo.SeegangBilder`: feste Bilder (Szenario/Ansicht/Zeit) in voller Größe und die Zeiten der Karte mit allem, ohne
  Brandung und ohne Seegang; `--kennung S0` heute, später dieselbe Liste als „nachher“. Ablage
  `docs/status/seegang/<Kennung>_<nn>.png`, `_Uebersicht.png`, Attest `docs/status/<Kennung>_Bilder.md`.

```
java -cp dist\Renderer.jar com.dan.lauf.demo.SeegangNachlaufLauf --gebiet <daten>\gebiet --archive <daten>\_p8c\archiv,<daten>\_p13\archiv_xaver
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.lauf.demo.SeegangBilder --szenen ..\HalligHooge\src\szenen\Hooge.szenen --kennung S0
```

## Seit Seegang S2b (Seegang Werkbuch, SF17 A): das Nahfeld mit den Daten der Szene

- `Nahfeldquelle` (0.1): baut ein `physics.waves.nah.Nahfeld` um einen Ort und füllt es zur Szenenzeit t aus den Daten
  eines Szenarios. Grund aus dem Feingelände (`gebiet/gelaende`); Spiegel aus `water.eta` von G3 (sonst G2), Strömung
  aus `hydro_G3`, beide bilinear über nasse Zellen und linear in der Zeit; Wind aus `wind` (G2), ins Gitter gedreht;
  Pflanzen aus den Zonen der Salzwiese (`Salzwiese`, C_D b_v N_v = 5/m · Bedeckung [G]). Rand und Bindung aus
  `seegang_G3w` (außerhalb `seegang_G2w`): Windsee und Dünung als zwei JONSWAP je Zelle (`Nahfeld.partitionen`),
  bilinear, linear in der Zeit. Gebunden wird je Stützstelle an dieses Spektrum (τ = 120 s [G, SF18 A]), nur im tiefen,
  gleichmäßigen Wasser; das Gitter liegt auf den 100-m-Linien von G3w. Stufen `GROB` (50 m × 100) und `FEIN` (10 m × 100).
- `demo.NahfeldLauf`: je Fall (Szenario/Ansicht/Zeit) grob 500 m und fein 300 m vor der Kamera, Anlauf auf der Karte,
  der feine Rand aus dem groben (`Nahfeld.schachtel`). Prüfstein S2.1: Hs im Mittel über 100 m gegen G3w, wo gebunden
  ist (≤ 10 % [G]; im feinen Gitter ohne die Blöcke am Rand, die aus dem groben kommen, und erst ab 20 Blöcken,
  SF24 A); für Watt, Rinnen, Kanten, Lee und den Rand eine Auskunft. S2.2: Karte gegen Referenz mit echten Daten
  (≤ 2 · 10⁻³). Zeit je Schritt, je Fall ein Bild `docs/status/seegang/<Kennung>_<nn>.png`, Attest
  `docs/status/<Kennung>_Nahfeld_Daten.md`. Vorgabe: fünf Fälle (Sturmflut Brandung und Hafen, Xaver Brandung und
  Hafen, ruhiger Tag Prielmündung).

```
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.lauf.demo.NahfeldLauf --szenen ..\HalligHooge\src\szenen\Hooge.szenen --kennung S2
```

## Seit Seegang S3a (Seegang Werkbuch, Datei 9): die Oberfläche, Referenz

- `demo.OberflaecheCheck`: die Prüfsteine der Oberfläche (`core.szene.Oberflaeche` 0.1) ohne Grafikkarte, gegen
  Lehrbuch, Eingabe und ein Nahfeld auf einer Rampe: Hüllen, Phasenfeld, S3.1 Varianz, S3.2 Spektrum und Richtung,
  S3.3 Phasen- und Gruppengeschwindigkeit, S3.4 Schiefe und Vorneigung (Ruessink), S3.5 Höhen (Rayleigh,
  Battjes–Groenendijk), Faltung, S3.6 mss und Stetigkeit über fein, grob und fern, S3.7 keine Wiederholung, Wechsel
  des Phasenfelds, gebundene lange Welle (Auskunft), Richtung gegen ein schräg einlaufendes Nahfeld. Teile einzeln
  über ihre Nummer. Rund 5 min auf 2 Kernen.

```
java -cp dist\Renderer.jar com.dan.lauf.demo.OberflaecheCheck [Teil …]
```

**Seit S3c (Seegang Werkbuch):**

- `Seegangsflaeche` (0.1): die Fläche zu einem Standbild (SF26 A). Nahfeld grob 500 m vor dem Auge, fein über der
  Brandungslinie im Blick (größtes `waves.diss` längs der Blickachse bis 1,5 km, sonst 300 m vor dem Auge; SF27 A;
  `ansicht.<name>.fein = Ost Nord` in der Szenendatei geht vor), beide auf der Karte der Bühne angelaufen, Phasenfelder
  auf dem Prozessor, Fernfeld aus den Netzen des Seegangs (`Seegangsnetz.wert`), dazu `SeegangKarte` und `Seegangbild`.
- `Buehne.seegang(Bildteil)` hängt die Clipmap vor das Wasser; `Buehne.seegangsnetze()`.
- `demo.SeegangBilder --kennung S3`: je Bild die neue Fläche; Zeiten: alles mit Netz, wie bisher ohne Netz, ohne Seegang.
- `demo.SeegangNetzCheck`: das Netz im Bild in einer Szene mit bekannten Werten (N1–N3), ohne Daten.
- `demo.QuellenMessung` (SF14): `physics.waves` über einen Ausschnitt mit Quellen je 60 s und je 10 s, Hs am Ende
  verglichen; `SeegangNachlauf.Einstellung.ab` beginnt dafür bei einem späteren Stand.

```
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.lauf.demo.SeegangNetzCheck
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.lauf.demo.QuellenMessung --gebiet D:\Daten\HalligHooge\gebiet --archiv D:\Daten\HalligHooge\_p8c\archiv
```

**Seit S3c-5 (Seegang Werkbuch 19/20):**

- `demo.SeegangBilder`: das Auge mindestens `ansicht.<name>.wasser` über dem Wasser (SF29 A, Prüfstein „kein Auge unter
  dem Wasser“); N6 – Netz und Schattierung je Bild aus den Zeitstempeln (`Wasser, Scheitel des Netzes` und `Wasser,
  Netz`), dazu ohne Abbruch über die Tiefe; N8 – Helligkeit beidseits der Nähte (Rand der Clipmap, Ränder des feinen und
  des groben Nahfelds), ≤ 5 % [G]; `--diagnose 5,…`: Karte von Hs des Nahfelds (`<Kennung>_<nn>_Hs.png`) und dasselbe
  Bild mit doppeltem Anlauf (`Seegangsflaeche.ANLAUF_DOPPELT`).
- `demo.SeegangNetzCheck`: dazu N3b (Teich hinter einem Deich, gegen den Strahl) und N6b (Abbruch bitgleich).
- `demo.SeegangNachlaufLauf`: Quellen je 10 s (SF30 A), im Attest genannt.

**Seit S3c-6 (Seegang Werkbuch 22):**

- `Buehne.wasserUndSeegang` liest den Spiegel am Auge aus η_Bild (`Spiegelbild.etaBildUtm`) statt aus der Messquelle
  (B51): SF29 und N8 wirken auch ohne Messblick.
- `Seegangsflaeche.wert` und `Nahfeldquelle` (Rand und Bindung des Nahfelds) mischen am Rand von G3w über 500 m in G2w
  (SF31 A, `Seegangsnetz.wertGemischt`).
- `demo.SeegangBilder`: N6 dazu mit Vorlauf der Tiefe, der alte Weg ohne Netz zum Vergleich, ein Bild mit Zählern je
  Bildpunkt (Überzeichnung, Zeit je Bildpunkt); ein Eintrag zweimal in der Liste vergleicht kalt gegen warm.
- `demo.SeegangNetzCheck`: dazu N6c (Vorlauf und Zähler bitgleich, Überzeichnung) und N9 (Übergang am Rand eines
  feineren Netzes, Karte gegen Prozessor, stetig).

**Seit S3c-7 (Seegang Werkbuch 24):**

- `Nahfeldquelle`: Rand und Bindung nach `Seegangsnetz.RAND_ART` (SF33 A, Vorgabe außen: in G3w nur G3w, außerhalb
  über 500 m vom Randwert von G3w in G2w).
- `demo.SeegangBilder`: N6 (SF32 A) = Mehrkosten des Wassers mit Netz gegen den Weg ohne Netz im selben Bild (Scheitel
  und Netz plus alter Weg daneben, minus alter Weg allein), ≤ 2,5 ms je Bild [G]; die ganze Schattierung nur noch als
  Angabe; Zähler B47 und alter Weg je Bild.
- `demo.SeegangNetzCheck`: N9 je Art des Übergangs (außen, innen, aus).
- Seit S3c-8: `demo.SeegangNetzCheck` dazu N6d (Maske des Netzes für den alten Weg bitgleich; der alte Weg sucht in
  keinem Bildpunkt des Netzes mehr).

**Seit S3d (Seegang Werkbuch 28, SF35–SF37 A): die neue Fläche in der Anwendung**

- `Flaechenwart` (neu): hält die `Seegangsflaeche` der Bühne in der Bildschleife (`vorBild`, `nachBild`). Ein eigener
  Faden baut sie; den Anlauf rechnet er in Stücken zwischen den Bildern (höchstens 4 ms je Bild [G], die Runden je Stück
  nach der gemessenen Zeit). Anlass (SF36 A): Öffnen eines Szenarios und Sprung der Zeit sofort; das Auge mehr als 1 km
  vom Bauort oder der Blick mehr als 60° gedreht, nach 2 s Ruhe; alle 30 min Modellzeit. Wechsel (SF35 A): das alte
  Nahfeld blendet in 1,5 s ins Fernfeld aus, das neue in 1,5 s ein; das Fernfeld nimmt seine Werte am Auge und zur Zeit
  des Bilds. Bis zur ersten Fläche zeichnet der Weg ohne Netz. Nur für Szenarien aus einem Archiv.
- `Buehne.seegangFlaeche(an)`, `seegangFlaecheAn()`, `hatSeegangFlaeche()`, `wart()`; eigenes Speicherkonto
  „Seegangsfläche“ (1 GB). Das Steuerhaus schaltet sie mit der Qualität (Teil SEEGANG, ab Mittel) und dem Knopf
  „Seegang“ (SF37 A).
- `Seegangsflaeche.baue(…, Takt)`: der Anlauf in Stücken; das Fernfeld zur Zeit des Bilds (`Seegangbild.zeit`).
- `demo.SeegangAppCheck` (neu): eine gespielte Sitzung (Öffnen, Ruhe, Fahrt 1,2 km, Sprung 3 h, Wechsel nach Xaver)
  über den Wart; bereit ≤ 25 s, Bildzeit im Bau ≤ +25 %, Sprung der Helligkeit beim Überblenden ≤ 5 %, vier Baue, Konto
  ohne Leck, ruhende Fläche bitgleich zum Standbild. Attest `docs/status/S3d_App.md`.
- `demo.SeegangBilder`: N6 als Median aus drei Durchgängen. `demo.SeegangNetzCheck`: N6d höchstens 0,1 % (B57).

**Seit S3d-2 (Seegang Werkbuch 31, SF38 A, SF39 A): schneller bereit, Zielzeit bei laufender Zeit**

- `Flaechenwart` 0.2: der Anlauf rechnet je Bild eine Runde, solange noch keine Fläche steht drei [G] (die Wandzeit eines
  Stücks wartet hinter dem Bild und taugt nicht als Maß, L2m). Ein Bau rechnet für die Mitte der Zeit, in der seine
  Fläche gezeigt wird: Beginn + (Dauer dieses Baus + Ausblenden + halbe Dauer und Einblenden des nächsten) × Zeitraffer;
  den Zeitraffer misst der Wart aus den Bildern (Hinweis beim Öffnen: `szenario(…, zeitraffer)`), die Dauer der Baue auf
  der Uhr der Bildschleife. Anlass „Alter“: die gezeigte Fläche wiche, wenn ein neuer Bau fertig wäre, um 30 min oder mehr
  von der Zeit des Bilds ab; bei Zeitraffer 300 baut er also laufend. Den Blick nimmt er beim Start eines Baus als Kopie.
- `Seegangsflaeche.baue`: das grobe Phasenfeld rechnet der Prozessor, während die Karte den feinen Anlauf rechnet
  (dasselbe Ergebnis).
- `demo.SeegangAppCheck`: Modellzeit mit dem Zeitraffer des Szenarios; Abweichung der gezeigten Fläche von der Zeit des
  Bilds ≤ 1,5 h [G]; Überblenden in der Pause mit fester Belichtung gemessen (die laufende Zeit bewegt das Bild von selbst,
  L2n); bitgleich mit fester Belichtung und demselben Ort des feinen Felds; Protokoll `S3d_App_Hell.csv`.

**Seit S3d-3 (Seegang Werkbuch 32, SF40 A): Anhalten bricht einen Bau ab, der zu weit vorausliefe**

- `Flaechenwart` 0.3: die Bühne meldet je Bild den gewollten Zeitraffer (`zeitraffer(r)`; rückwärts negativ, angehalten
  0; im Live-Lauf NaN). Ändert er sich, springt der gemessene Zeitraffer auf ihn, statt über 1 s zu gleiten. Ein
  laufender Bau oder eine fertige, noch nicht überblendete Fläche fällt weg, wenn sie zur Mitte ihrer Zeit im Bild mit dem
  neuen Zeitraffer um 30 min oder mehr danebenläge (Rest bis dorthin × Unterschied der gewollten Zeitraffer). Die gezeigte
  Fläche bleibt, bis die neue steht; die Bildschleife wartet nicht, der neue Bau startet, wenn der alte Faden ausgelaufen
  ist. Gilt für Anhalten, Verlangsamen und Rückwärtslauf. Wartet die Uhr auf Stände (W5c), bleibt der gewollte
  Zeitraffer, darum bricht dort nichts ab; ein Bau, der länger dauert als erwartet, auch nicht. Ein verworfener Bau setzt
  keinen Fehler. `verworfen()` zählt.
- `Buehne`: ruft je Bild `wart().zeitraffer(uhr.zeitraffer)` vor `vorBild`.
- `demo.SeegangAppCheck`: führt den gewollten Zeitraffer wie die Bühne. Teil 3 hält bei laufender Zeit an: nach dem
  Anhalten passt das Nahfeld zur Zeit des Bilds nach ≤ 30 s Uhr [G], und keine eingesetzte Fläche weicht um 30 min oder
  mehr ab (L2o vorher: 36,1 s Uhr, bis nichts mehr baute). Dazu als Auskunft (Befund L2o): Bildzeit im Bau getrennt nach
  laufender Zeit und Pause; Stücke des Anlaufs je Bild und Bildzeit beim Öffnen.

**Seit S4a (Seegang Werkbuch 35, SF42–SF45 A): das Schaumfeld in der Anwendung**

- `Seegangsflaeche.drift(Nahfeld)`: je Zelle die Drift des Schaums, Strömung + Stokes-Drift an der Oberfläche
  (Σ σ k E cosh(2kh)/sinh²(kh) · e) + 3 % U10 [G]; `baue` gibt sie der Karte (fein und grob). Zur Prüfung
  `Seegangsflaeche.SENKEN`: nach dem Anlauf ein Schritt des Nahfelds (10 s) und dessen Senken je Zelle
  (`senkenFein`, `senkenGrob`: dwc, dbr, dbf, dveg; im Anlauf sind sie 0); die Fläche kommt aus dem Stand davor.
- `Flaechenwart` 0.4: ein `render.seegang.Schaumfeld` über alle Flächen (`schaumfeld()`), angehängt an das Bild der
  gezeigten Fläche; ein Wechsel der Fläche lässt den Schaum stehen, ein neues Szenario und „aus“ leeren es.
- `demo.SchaumCheck` (neu): Energie der brechenden Kämme gegen dwc (± 20 %), Bedeckung gegen Monahan (× 0,5 bis × 2,
  Sturm und Xaver), Masse (± 1 %), Konvergenz gegen e^{a t} (± 5 %), Kosten (≤ 2 ms), Bilder mit Feld und Statistik.
  Attest `docs/status/S4a_Schaum.md`. `demo.SeegangAppCheck`: der Vergleich „bitgleich“ hängt dasselbe Feld an.

**Seit S5b-4 (Seegang Werkbuch 46): Kämme am Fuß der Kanten**

- `Flaechenwart`: eine `render.seegang.Fusssuche` und eine `core.szene.Fusswellen` über alle Flächen (`fusssuche()`,
  `fusswellen()`), angehängt an das Bild der gezeigten Fläche; ein neues Szenario und „aus“ leeren sie.
- `Buehne`: gibt die Folge je Bild an die Brandung (`Brandungsbild.fuesse`).
- `demo.WalzeCheck`: Teil „Blick auf eine Kante“ – seit S5b-4b die Kante mit dem höchsten Seegang am Fuß unter allen
  aktiven des Gebiets (Befund L3a: im Umkreis der Orte nur Kanten des Watts, im Sturm unter Wasser), davor eine eigene
  Fläche, 240 Bilder mit Fußsuche; Anteil der gekoppelten Abschnitte im Netz (≥ 0,9), feste Kämme gegen die Referenz.

Seit S5b-6 (Seegang Werkbuch 55): `Seegangsflaeche` koppelt die reflektierten Stützstellen (`core.szene.Reflexion`):
Partner aus der groben Stufe vor den Phasenfeldern, Richtungen gespiegelt, Konstanten im groben und (als Rest) im feinen
Feld, gemeinsame Hüllen im `Profilpuffer` vor der Karte. `Seegangsflaeche.REFLEXION` (Vorgabe an,
`-Dseegang.reflexion=aus`: wie bis S5b-5). Im Bericht der Fläche: Paare und Kohärenz an den Wänden.
`demo.OberflaecheCheck` Teil 14: vor einer senkrechten Wand und einem Hang nach Battjes ein Bauch an der Wand, K_r aus
Bauch und Knoten gegen das Nahfeld (das Muster über Band und Richtungen verwischt gerechnet).

Seit S5b-6b (Seegang Werkbuch 57): Partner aus grober Stufe und feinem Gitter (`Seegangsflaeche.geometrie`: Tiefe vor dem
Anlauf), `Reflexion.wand` statt der Konstanten; `Seegangsflaeche.reflexion` mit der Bilanz der Wände (grob, fein), im
Bericht Paare, Wandflächen und Kohärenz vorher/nachher. `demo.OberflaecheCheck` Teil 15: zwei um L/4 versetzte Wandstücke
(Bauch vor beiden, K_r, Wellenzahl der reflektierten), Auskunft runde Warft und Küste unter 45°. `demo.WalzeCheck`:
Paare, wo das feine Gitter Wände hat; Kohärenz fein vor der Kante ≥ 0,7 [G].
Seit S5b-6b2: `demo.OberflaecheCheck` Teil 15 mit Tiefe längs der Wand und Wand nur im feinen Gitter; `demo.WalzeCheck`
zusätzlich Kohärenz fein in den geraden Stücken ≥ 0,8 [G].

## Seit Seegang S6b-2 (Seegang Werkbuch 67/68, SF52–SF55, SF61–SF64 A): der Auflauf am Blick

- `Auflauf` (neu, 0.1): ein Rechteck von 256 × 1024 Zellen zu 0,25 m (64 m quer, 256 m längs) an der ersten
  Wasserlinie im Blick (15–300 m vor dem Auge), in dem `physics.swash` auf der Karte rechnet (`SwashKarte`), am
  seeseitigen Rand angetrieben aus der Synthese der gezeigten Fläche (`render.seegang.Randwelle`, 129 Punkte alle 2 m).
  Physik und Bild kennen einander nicht; hier treffen sie sich.
  - Lage: Richtung und Neigung aus der Ausgleichsgeraden durch die Wasserlinie (Gelände = Ruhewasser) an 17 Stellen
    über die Länge; landwärts 2 × max(R2 % nach Stockdon u. a. 2006, Ru2 % nach EurOtop 2018) / tan β, 8–48 m [G], der
    Rest seewärts (SF64 A); Hs und Tp aus den Netzen 30 m seewärts. Grund aus dem Gelände (0,25 m; ohne Wert Fels),
    Boden Sand d50 0,2 mm bis S6c (A8), Grundwasser im Ruhewasser. Seiten: W Antrieb, E Wand, S und N offen. Ist der
    seeseitige Rand im Mittel flacher als 0,3 m, bleibt der Löser aus (A7); das prüft das Setzen an 33 Stellen, bevor
    es das Gitter baut.
  - Neu gesetzt nach 40 m Weg des Auges, 30° Drehung, 0,25 m Unterschied des Spiegels zum Ruhewasser (Tide) oder einem
    Sprung der Zeit [G]; ohne Rechteck alle 5 s Modellzeit ein neuer Versuch. Nur bei Zeitraffer 1, angehalten ruht er
    (SF63 A).
  - Je Bild als `Seegangbild.Anhang` nach der Clipmap: Antrieb zur Zeit des Bilds in den einen Stand, n Schritte von der
    vorigen Zeit bis zu dieser mit dem Antrieb linear dazwischen; Zeitschritt aus dem zuletzt fertigen `tempo` × 1,5,
    höchstens 4 Schritte je Bild (`verpasst` zählt Bilder mit zu wenigen).
  - `bericht`, `lage()`, `wasserlinie`, `swash()`, `karte()`, `randwelle()`, Zähler `bilder`, `schritte`, `verpasst`,
    `gesetzt`; `-Dlauf.auflauf=aus`: keiner.
- `Flaechenwart`: hält den Auflauf (`auflauf`), hängt ihn an jede neue Fläche (`bild.anhang`), gibt ihm je Bild Blick,
  Zeit, gewollten Zeitraffer und Netze (`vorBild`); ein neues Szenario vergisst das Rechteck.
- `Buehne`: baut ihn mit dem Wart (Gelände über eine eigene `Abfrage`, Spiegel des zuletzt gezeigten Stands über
  `spiegel(ost, nord)`); `auflauf()`.
- `demo.AuflaufCheck` (neu): die Bildschleife nachgeführt (Zeit, Clipmap, Auflauf in einem Ablauf, 60 Bilder je s,
  60 s Modellzeit) an der Kante mit dem höchsten Seegang am Fuß im Sturm und bei Xaver, dazu die Ansicht „brandung“
  im Sturm. Prüfsteine je Rechteck: gesetzt (Kanten), Bilanz des Wassers ≤ 1e-4, seeseitiger Rand nass ≥ 90 %,
  landseitige Spalte nie nass ≥ 90 % der Zeilen, kein Bild mit zu wenig Schritten, Kosten je Bild ≤ 1 ms auf der 680M;
  Auskunft der Auflauf gegen die Schätzung (Maßstab in S6b-3). Attest `docs/status/S6b_Auflauf.md`. Das Bild zeigt
  den Löser erst mit S6c.

## Seit Seegang S6b-2b (Seegang Werkbuch 69/70, SF65–SF67 A): Auflauf 0.2

- Rechteck 128 m statt 256 m längs (256 × 512 Zellen), Sicherheit 1,2 statt 1,5 auf das Tempo, Courant 0,4 (Swash
  0.2; gewählt war 0,45, siehe `physics.swash`): rund 2 statt 3 Schritte je Bild bei 60 Bildern je s, je Schritt rund
  die Hälfte (SF65 A).
- Höchstens 8 statt 4 Schritte je Bild (SF66 A); in der Anwendung bei 26–38 ms je Bild sind 2–4 nötig.
- Landwärts ab der landwärtigsten gefundenen Stelle der Wasserlinie (senkrecht zur Ausgleichsgeraden) statt ab P;
  wäre der seeseitige Rand dann flacher als 0,3 m, ab P (der Bericht sagt es); seewärts mindestens 8 m (SF67 A).
  `hs`, `tp`: Seegang aus den Netzen beim Setzen.
- `demo.AuflaufCheck`: 120 s je Fall; Auskunft Tempo je Bild (Median, 99 %, höchstes mit Zeile), Bilder je Zahl der
  Schritte, Ort der Spitzen über 8 m/s (Zelle, Tiefe, Geschwindigkeit, Grund, ob hinter der Krone), Zeitreihe des
  Wassers alle 10 s, Hs am seeseitigen Rand aus der Synthese neben dem aus den Netzen.

## Seit Seegang S6b-3 (Seegang Werkbuch 71/72, SF68–SF71 A): Auflauf 0.3, Maßstäbe

- `Auflauf` 0.3: Landseite offen statt Wand. Was dort hinausfließt, ist Überlauf (SF70 A). Eine Krone (höchster Grund,
  hinter dem er um mindestens 0,2 m fällt) innerhalb der Reichweite verlängert das Rechteck bis mindestens 5 m dahinter,
  solange der seeseitige Rand tief genug bleibt. `landLinie`, `kroneX`, `kroneZ`; `wasserstand()` öffentlich.
- `demo.AuflaufCheck`:
  - Die landseitige Spalte zählt nur Zeilen, für die das Rechteck gelegt ist: in Ruhe trocken, nicht hinter einer
    Krone, Wasserlinie mindestens 2 R / tan β vor der Landseite (SF69 A). Die Auskunft nennt die übrigen und, für die
    nass gewordenen, den Abstand ihrer Wasserlinie.
  - Überlauf über die Landseite als Auskunft.
  - Die Schritte je Bild zählt die Prüfung um `vorbereiten` herum (F2).
  - `kante(…, arten)`: nur Kanten bestimmter Arten.
- `demo.AuflaufMassstabCheck` (neu, SF71 A):
  - Fälle: Sand (ruhiger Tag mit Seegang, 30.10.2026 14:00), Deckwerk und Deich (Sturm, 25.10.2026 12:30), je 900 s.
  - Je Bild der Saum je Zeile (`SwashKarte.saum`), relativ zum Spiegel am Rand in diesem Bild.
  - Je vierte Zeile Welle für Welle ausgewertet (Maxima zwischen Aufwärtsdurchgängen, nach 60 s Anlauf); R2 % ist das
    98-%-Quantil aller Maxima.
  - Gegen Stockdon u. a. (2006) am Sand (H₀ linear zurückgerechnet) bzw. gegen EurOtop 2018 am Bauwerk (tan α, γ_f und
    Krone der Kante; H_s aus der Synthese am Rand), je ± 20 %. Liegt die Krone niedriger als 1,2 Ru2 %, zählt der
    Überlauf über die Landseite gegen EurOtop (Faktor 3).
  - Attest `docs/status/S6b_Massstab.md`.

## Seit Seegang S6b-3b (Seegang Werkbuch 73): Auflauf 0.4, Außenzustand

- `Auflauf` 0.4 (F3): alle vier Seiten mit einem Außenzustand je Randzelle, den die Randwelle schreibt. Wo innen die See
  ist (in Ruhe mit dem seeseitigen Rand verbunden), an W, S und N die Synthese (Punkte auch längs S und N über deren
  Seeteil), an E ruhendes Wasser am Spiegel; wo innen Land ist, ein trockenes Außen (nur Ausfluss). Vorher ließen die
  offenen Seiten (Kopie der Zelle innen) Wasser ohne Grenze hinein: in L4f/Diagnose D flutete so über N das Land hinter
  dem Deich, und dieses Wasser lief über E ab (als Überlauf gezählt).
- F4: In Ruhe ist nur gefüllt, was mit dem seeseitigen Rand unter dem Spiegel verbunden ist (`seeVerbunden`); Senken
  hinter dem Deich bleiben trocken. `seeZellen`, `aussen` (Auskunft, auch im Bericht des Setzens).
- `demo.AuflaufMassstabCheck` (SF72, SF73; Änderungen nach der Messung, angesagt):
  - Sand: ein Strand nach Regeln [G] (Art 0–2, mindestens 100 m von Deich, Warft und Deckwerk, tan α ≤ 0,15, H_s am Fuß
    ≥ 0,3 m), zuerst im Sturm, sonst am ruhigen Tag.
  - Überlauf: was über die Krone ins Land dahinter kam (Zunahme dort, dort versickert, über das trockene Außen hinaus).
  - Seegang für die Formeln aus dem Antrieb: einlaufend je Frequenz nach linearer Theorie getrennt (0,04–2 Hz), H_m0,
    T_m−1,0, Tp; dazu gesamt und die Reflexion in der Synthese als Auskunft.
  - EurOtop mit der Neigung des Profils im Bereich SWL − 1,5 H_m0 bis SWL + min(Ru2 %, R_c) (EurOtop 2018, 5.3.4; je
    vierte Zeile, Median, iteriert) und R_c als Median der Kronen je Zeile; zum Vergleich die Rechnung wie in L4f.
- `demo.AuflaufDiagnose` (neu): zeichnet die Fälle am echten Ort auf (Grund, Saum, Querprofile an vier Sondenzeilen,
  Randwelle, Bilanz je Seite) nach `docs/status/diag_L4g/<Fall>/`; `--fall Kanten` listet die aktiven Kanten.

## Seit Seegang Werkbuch 74 (SF74–SF76 A)

- `demo.AuflaufMassstabCheck`: der Strand der Anwendung nur noch als Auskunft gegen Stockdon (SF75 A; der Prüfstein
  steht als Teil 12 im SwashKarteCheck). Neu als Auskunft (SF76 A): die Quellen des Seegangs am seeseitigen Rand –
  Netze (Hs, Tp), die Oberfläche der Synthese (H_m0, T_m01, Gewichte fein, grob, fern), je Stufe H_m0 der Zelle nach
  Bändern (unter 0,2 Hz, 0,2–0,5 Hz, darüber), das Fernfeld und die Synthese am Rand nach denselben Bändern.
- `Auflauf` 0.5 (F5): an S und N im Seeteil ruhendes Wasser am Spiegel statt der Synthese (an W bleibt sie). Die Punkte
  längs S und N reichten bis an die Uferlinie, wo die gebundene lange Welle der Synthese nicht gilt (A7): Seit 0.4 lag das
  Tempo dort bis 22–41 m/s (Zeilen 0 und 511).
- `Auflauf` 0.6 (F6, nach L4i): an S und N im Seeteil die Zelle innen (Art 3 der Randwelle) statt ruhenden Wassers am
  Spiegel. Ruhe an S und N ist ein See daneben, in den der Stau abläuft (Container: viermal so viel Verlust); die Zelle
  innen gleicht einer langen, gleichförmigen Küste, Strömung längs geht hindurch. Synthese an S und N (A7) bleibt aus.
  Befund L4j: Stau und Überlauf von L4g/L4h kamen damit nicht wieder (Deckwerk Saum im Mittel +0,03 m, Überlauf 4e-5
  m³/s je m). Sie stammten aus der Synthese an S und N (A7), nicht aus der Physik am Bauwerk.
- `demo.AuflaufMassstabCheck` (Datei 76): der Überlauf am Bauwerk gegen EurOtop nur noch Auskunft (SF81 A; die
  Richtungsverteilung der Synthese trägt den Fehlbetrag, S6c); am Strand die Wasserlinie bei 5 cm (SF82 A).
- `demo.AuflaufMassstabCheck` (Datei 75, nur Auskunft):
  - SF78 A: Wind am Ort (`Nahfeldquelle.windBei`), Fetch luvwärts über Wasser und mittlere Tiefe darauf
    (`Nahfeldquelle.fetch`, 25 m, bis 50 km), H_m0 und Tp nach Young und Verhagen (1996) gegen die Windsee des Nahfelds
    (Bänder ab 0,2 Hz).
  - SF80 A: Richtung der einlaufenden Synthese am Rand je Frequenz aus den Kreuzspektren von η, q_n, q_t (mit der
    Reflexion), nach Energie gemittelt, und EurOtop am Fuß mit γ_β = 1 − 0,0033 |β| (EurOtop 2018, 5.29/5.30).
- `demo.AuflaufMassstabCheck` (nach L4h, angesagt): EurOtop bekommt wie vorgeschrieben H_m0 und T_m−1,0 **am Fuß**. Stand
  und Fluss werden in 8 Zeilen an jeder zweiten Zelle aufgenommen (wo tiefer als 0,3 m), je Zelle die einlaufende Welle
  getrennt (im Gitter mit der Beziehung des Lösers k/ω = 1/√(g h), seit L4i; am Rand weiter linear dispersiv wie die
  Synthese). Fuß je Zeile: die erste Zelle mit Grund ≥ SWL − 1,5 H_m0 (am Rand), Median der Zeilen. Die Rechnung mit dem
  Seegang am Rand steht als Auskunft daneben. Neu als Auskunft: der Seegang längs (alle 4 m) und quer (je Zeile am Rand).
- `Auflauf` 0.7 (S6c-1b, Seegang Werkbuch 78, SF85 A): Richtung im Löser erhalten. Nach `randwelle.punkte` setzt
  `setze` den Bezug (`Randwelle.bezug`: Mitte der Linie der Punkte, Tiefe des Rands im Mittel). Befund in Datei 77:
  Ohne ihn lief am Deich nur rund die Hälfte der Energie in den Löser, der Überlauf am Bauwerk war darum zu klein.
  `-Dseegang.randrichtung=aus`: wie 0.6.
- `demo.AuflaufMassstabCheck` (Datei 78, Auskunft): „Frei laufend im Löser“, der Anteil der Energie am Rand mit
  |k_y| < ω/√(g h) je Band und gesamt, dazu linear (`freiLaufend`; Hann in Zeit und Ort, die letzten 2^n Aufnahmen).
- `Bodenkarte` 0.1 (neu, S6c-3, Seegang Werkbuch 80, SF89 A; A8): Boden des Auflaufs aus den Klassen des Geländes. Gestalt
  (`gestalt/`): Deich und Warft Klei mit kurzer Grasnarbe (n 0,020, k_f 1e-8 m/s), Fenne, Salzwiese, Grasweg Klei mit Gras
  (0,030), Deckwerk gesetzte Steine dicht (0,018, 0), Asphalt, Beton, Klinker, Holz glatt dicht (0,013), Schotter (0,025,
  1e-3), Graben, Priel, Fething Schlick; sonst das Korn des Gebiets (`sediment/`, d50 bilinear: Manning aus 2,5 d50,
  Durchlässigkeit nach Hazen, Schlick 1e-9); ohne Kornfeld Sand 0,2 mm. Werte in `physics.swash.Boden`.
- `Auflauf` 0.8: `Bodenquelle` (n, k_f, Klasse je Ort) und Feld `boden`; ohne sie Sand wie bis 0.7. Der Bericht des Setzens
  nennt die Anteile der Klassen ab SWL − 1 m (`bodenBericht`). `Buehne.wart()` gibt `Bodenkarte.aus(gebiet, gestalt)`;
  `-Dlauf.boden=sand`: wie bis S6c-1b.
- `demo.AuflaufMassstabCheck` (Datei 80): der Überlauf am Bauwerk wieder Prüfstein, gegen EurOtop am Fuß mit γ_β
  (kurzkämmig), Faktor 3 (SF89 A); ohne γ_β Auskunft. Stockdon am Strand mit Tp = 1,1 T_m−1,0 am Rand statt des Gipfels
  (SF90 A).
- `Auflauf` 0.9 (S6c-2, Seegang Werkbuch 82, SF93 A): nach den Schritten jedes Bilds das Bild des Rechtecks
  (`SwashKarte.bild`, Kontur des Spülsaums `KONTUR_BILD` 1 cm) und im Bildziel unter `Auflauf.BILD` („Swash/Bild“) Puffer
  und Werte {Ecke Ost, Nord (UTM), Richtung x Ost, Nord, Richtung y Ost, Nord, Zelle, nx, ny, Ruhewasser, Zeit, Kontur};
  ohne Rechteck oder ohne Rechnen fehlen beide. Gelände und Wasser zeigen es (render.terrain, render.water); `bildAn`,
  `-Dlauf.swashbild=aus`; `bildGilt()`. Dieselbe Zeit zweimal (Standbild) rechnet nicht weiter, statt neu zu setzen.
  `Grundaenderung grundAendern` (nur Prüfungen): ändert den Grund nach dem Setzen. `Buehne.seegang(null)` nimmt auch das
  Bild weg.
- `demo.AuflaufCheck` (S6c-2): Prüfsteine „Bild des Rechtecks gegen den Löser“ (bitgleich mit `bildReferenz`) und
  „Spülsaum an der höchsten Linie des Lösers“ (± 0,5 m in 95 % der Zeilen mit Auflauf, gegen die Linie, an der der höchste
  Stand der Zeile den Grund schneidet); `bildPruefen`.
- `demo.AuflaufBildCheck` (neu, S6c-2): der Auflauf im Bild der Anwendung (Bildschleife mit Wart, Zeitraffer 1, Sturm,
  Kante mit dem höchsten Seegang am Fuß): Fläche bereit, Rechteck gesetzt, Bild gegen Löser, Spülsaum; Standbilder zur
  Sichtprüfung in `docs/status/seegang/S6c2_kante_*.png`, `S6c2_nah_*.png` (Auge 30 m seewärts der Wasserlinie) und
  `S6c2_nah_ohne.png` (ohne das Bild des Auflaufs). Attest `docs/status/S6c2_Bild.md`.
- `demo.AuflaufMassstabCheck` (Datei 82, SF94 A): EurOtop bekommt H_m0 und T_m−1,0 am Fuß der Böschung (je Zeile von der
  Krone seewärts das Ende des steilen Stücks, wo der Grund über 1 m flacher als 1:4 steigt) aus einer zweiten Rechnung
  desselben Rechtecks ohne Bauwerk (landwärts des Fußes ein Strand 1:30, 300 s nach 60 s Anlauf; `ohneBauwerk`). Die Regel
  aus Datei 74 (SWL − 1,5 H_m0, mit Bauwerk getrennt) steht zum Vergleich daneben. Der Prüfstein bleibt (Faktor 3, SF95 A).

- Seit Seegang Werkbuch 83 (S6c-2b): `Bodenkarte` 0.2 rechnet Durchlässigkeiten unter `K_DICHT_UNTER` (1e-7 m/s) als
  dicht (Klei 1e-8, Schlick 1e-9: rund 1 mm in einer Viertelstunde Sturm, aber jede einmal nasse Kachel blieb als
  Sickerwasser im Löser wach); `werteRoh` gibt die Werte davor. `demo.AuflaufCheck.bildPruefen`: Prüfstein jetzt
  „Spülsaum wie gezeichnet an der Auflaufgrenze des Lösers“ (± 0,5 m in 95 % der Zeilen), danach die Zeilen neu und alles
  bitgleich; die Regel bis S6c-2 und die Zeilen mit Überlauf über die Krone
  als Auskunft. `demo.AuflaufBildCheck`: dazu der Blick schräg längs des Ufers (`S6c2_seite_*.png`, Auge 6 m seewärts und
  25 m längs von P, 2,5 m über dem Ruhewasser), das letzte Standbild (60 s) fehlt nicht mehr.
  `Auflauf` 0.10: das Bild der Zellen nur im letzten Schritt eines Bilds (`karte.bildImSchritt`, nur mit `bildAn`; aus:
  `karte.bildNeu()`, beim Wiedereinschalten füllt es neu), das Tempo je Zeile nur jedes `TEMPO_JE`-te (2.) Bild (es wird
  ohnehin einige Bilder alt gelesen und mit `SICHERHEIT` genommen). `demo.AuflaufCheck`: die Kosten getrennt – Antrieb und
  Löser ohne das Bild des Auflaufs (wie S6b; `kostenOhneBild`, 30 s nach dem Fall) ≤ 1 ms, das Bild (mit gegen ohne)
  ≤ `KOSTEN_BILD_MS` 0,1 ms [G]; mit Bild Auskunft. In L4o–L4r zählte das Bild im selben Prüfstein mit.
  `demo.AuflaufMassstabCheck.ohneBauwerk`: Diagnose (Stand am Rand, Fuß trocken, H_m0 je Zeile) und unbrauchbar, wenn am
  Fuß weniger als `OHNE_MIN` (25 %) des Stands am Rand ankommt oder der Fuß meist trocken liegt (L4o, Deich: 0,00 m) –
  dann EurOtop mit der Regel aus Datei 74.

## Seit Seegang S6d (Seegang Werkbuch 84, SF57 A, SF58 A): Wellenrippel und Flutkante

- **`Wellenrippelfeld`** (0.1): die Wellenrippel als Gedächtnis auf dem Gitter des Bodens.
  - Stand für Stand des Seegangs: H_s, T_p und Richtung bilinear aus dem feinsten Gitter des Seegangs (nur nasse Zellen, H_s höchstens 0,78 h), die Tiefe aus dem feinsten Wassergitter, die Strömung aus `hydro_<Gitter>`, das Korn aus dem Kornfeld.
  - Regel und Formeln in `physics.sediment.Wellenrippel`. Trocken oder ohne Bewegung des Korns bleibt der alte Stand: Bei Niedrigwasser liegen die Rippel der letzten Welle.
  - Im Schlick flacher (1 − Schlickanteil/0,3), auf festem Boden keine.
  - Neuanfang mit 12 h Vorlauf, wenn die Zeit springt.
- **`Bild`**: rechnet das Gedächtnis nebenher in einem eigenen Faden (`wellenSofort` für Standbild und Prüfungen) und gibt es dem Gelände (`Gelaendebild.wellenrippel`).
  - `Buehne.standbild` rechnet es gleich.
  - `wellenAn` schaltet es ab.
- **`Auflauf` 0.11 – Flutkante:** die zweite Form des Rechtecks für das Watt.
  - Ist der seeseitige Rand der ersten Form zu flach oder das Ufer flacher als 1:200 (`NEIGUNG_WATT`), dieselben 256 × 512 Zellen zu 1 m (256 m quer, 512 m längs).
  - Seewärts ab 16 m in Schritten von 8 m, bis der Rand im Mittel 0,1 m tief ist; landwärts mindestens 64 m.
  - Antrieb: der Spiegel der Hydro mit der Synthese darauf. Steigt er, läuft die Flut übers Watt.
  - Seiten: W überall die Welle; S und N die Zelle innen; E wie sonst.
  - Neu gesetzt, wenn die Front das Rechteck verlässt (`frontRaus`: Spiegel der Hydro im Median der Zeilen weniger als 16 m vor der Landseite, oder Rand flacher als 5 cm), nicht nach dem Pegel.
  - Je Form eine `SwashKarte`.
  - Aus mit `-Dlauf.flutkante=aus`.
- **Prüfungen:**
  - `demo.WellenrippelCheck`: Formeln, Feld gegen eine unabhängige Rechnung, Bild von oben mit Spektrum, nebenher.
  - `demo.FlutkanteCheck`: Japsand bei auflaufendem Wasser. Der Fall wie `AuflaufCheck`, die Front des Lösers gegen die der Hydro, neu setzen, Bilder.

## Seit S7b-2 (Seegang Werkbuch 88): die Schwimmer

- **`Schwimmerangabe`**, **`Szenen`**: `schwimmer.<name>.art | ort | kurs | fest | name | farbe` oder
  `schwimmer.<name>.datei` (je Zeile `art Ost Nord Farbe Kette Name [Kennung]`), dazu `szenario.<name>.schwimmer = a b c`.
- **`Flotte`** (neu): die Körper eines Szenarios (`physics.auftrieb`) im eigenen Faden. Je Bild (`Buehne.flotteBild`):
  Lagen für das Bild (mit der Geschwindigkeit auf die Zeit des Bilds fortgeschrieben), Anfrage an die Karte
  (`core.szene.Wasserpunkte`), Pegel je Körper aus dem Spiegel des Wassers, Wind am Auge; der Faden rechnet bis zur Zeit des
  Bilds. Bis Zeitraffer 4 [G] in der Zeit der Szene mit den Wellen der Karte, darüber in der Zeit der Uhr im ruhigen Wasser
  auf dem Pegel (die Körper steigen und fallen mit der Tide, fallen trocken). Fest: `anleger` (Leinen in Ringen an Pfählen,
  Fender), `anker`, `kette`, `frei`. Strom fehlt noch (die Anwendung hält keinen Strom am Ort).
- **`Buehne`**: baut beim Öffnen eines Szenarios Flotte und `render.schwimmer.Schwimmerbild` (nach Häusern und Bewuchs im
  Rahmen); `-Dlauf.schwimmer=aus`: keine. **`Flaechenwart`** hängt `Koerpersuche` und Wasserpunkte an jede gezeigte Fläche.
- Prüfung: `lauf.demo.SchwimmerCheck` (Attest `docs/status/S7b_Schwimmer.md`, Bilder `S7b_*.png`).

## Seit S7b-4, S7c, S7d (Seegang Werkbuch 89): Strom, Bordkamera, Fahrplan, Kielwasser

- **Flotte 0.2:** Punkte der Karte nach der Art (`Flotte.raster`: Fähren 2 m, Kutter je Säule, Tonnen und Kleines ein Punkt);
  Strom je Körper am Ort (`Umwelt.strom`; `Stromort` liest `water.u`, `water.v` aus dem Nachlauf der Strömung, den neuesten
  Stand bis zur Zeit, in einem eigenen Faden); Bugschlag je Schiff; `bildLagen()` (die Lagen des letzten Bilds).
- **Fahrplan (SF115 A):** `schwimmer.<n>.route` (Datei, je Zeile Ost Nord ab dem Anleger) und `.fahrplan` (Ankunft Abfahrt,
  UTC). `Lotse` (neu): kommt (2 Längen voraus auf dem Weg, langsam am Ende, fest unter 8 m vom Anleger), liegt, fährt ab
  (60 s rückwärts, herum über Steuerbord, hinaus), sonst am Ende des Wegs; nicht bei Wind über 14 m/s oder Pegel unter
  −1,0 m NHN [G]. Im Zeitraffer und nach Sprüngen steht die Fähre, wo der Plan sie hat.
- **Kielwasser:** `Flotte.kielwasser` (`core.szene.Kielwasser`), je Bild im Faden der Bilder gesetzt; die Bühne gibt es mit
  den Wasserpunkten an den Wart (`Flaechenwart.wasserpunkte(w, kw)`), der Wart an die Fläche (`Seegangbild.kielkarte`).
- **Bordkamera (SF110 A):** `ansicht.<name>.bord = <schwimmer> [x y z]`; `Buehne.bord(Ansicht)` setzt die `Bordkamera` (eine
  `Kamera`): Auge fest am gezeichneten Rumpf, ruhiger Blick (Rollen 70 %, Stampfen 50 %, 0,3 s Verzug), senkrechte
  Beschleunigung am Auge nach ISO 2631-1 (`core.szene.Seekrank`). `core.raum.Blick.rollen` (neu). Die Bildschleife setzt
  die Schwimmer jetzt vor der Kamera (`flotteBild`, dann `kameraBlick`).
- Prüfung: `demo.SchwimmerCheck` (S7b4), `demo.KielwasserCheck` (S7d), `demo.BordkameraCheck` (S7c).

## Seit S7d2 (Seegang Werkbuch 90): Nacharbeit aus L5d

- **Flotte 0.3:** Kielwasser zieht nur ein Schiff in eigener Fahrt (Hebel ≠ 0 oder der Lotse kommt oder fährt ab), mit der
  Fahrt durchs Wasser längs des Kurses, über `KIEL_MITTEL` = 10 s geglättet [G]; ein Kutter vor Anker, den die Wellen hin
  und her tragen, zieht keins (Befund L5d 3). Die Anfrage an die Karte nennt je Körper seine Quelle: An seinen Punkten fehlt
  sein eigenes Kielwasser. Je Schwimmer `kraengungMax` und, über `KENTERN` = 45° [G], `kentern`: was die Flotte in dem
  Augenblick wusste (Lage, Fahrt, Stand der Karte und sein Alter, η und Kielwasser an den Punkten, Kette, Strom, Wind, Pegel).
- **Fahrplan nach der Tide (SF117 A):** `schwimmer.<n>.fahrplan = tide -2:00 +1:30`: je Hochwasser am Pegel ein Umlauf
  (Ankunft 2 h vor, Abfahrt 1 h 30 nach) [G], nur bei Tag (Ankunft ab 05:00, Abfahrt bis 20:00 UTC) [G]. Die Hochwasser gibt
  die Bühne aus ihrer Pegelreihe (`Flotte.hochwasser`; live neu, wenn die Reihe wächst); vor dem ersten und nach dem letzten
  geht es mit 12,42 h weiter. Liegt sie wegen des Wetters, nennt die Phase den Grund.
- **Bordkamera 0.2 (SF118 A):** Auge des Kutters auf dem Dach des Ruderhauses, der Fähre auf dem Oberdeck vor der Brücke
  (in S7c lag es im Block des Oberdecks); `BordkameraCheck` prüft, dass jedes Auge frei liegt.
- Prüfung: `demo.SchwimmerCheck`, `demo.KielwasserCheck`, `demo.BordkameraCheck` (Lauf L5d2).

## Seit S7d8 (Seegang Werkbuch 97, SF142–SF145 A): Verholen, Abfahrt, Plan

- **Flotte 0.6, Verholen (SF142 A, Befund L5d7 2: 3,45 m und 8,2° neben dem Platz):** Wurde die Fähre vor der Zeit des
  Plans fest (Phase noch KOMMT), steuerte der Lotse weiter, und die Flotte machte sie in jedem Schritt neu fest; im
  Container hingen nach 40 s 5324 Leinen an ihr, und das Verholen setzte aus (es erwartet genau vier). Jetzt liegt sie dann
  wie in LIEGT und verholt; `festmachen` nimmt vorher ab, was es zuletzt angelegt hat (`Schwimmer.angelegt`). Im Container
  mit Strom und Wind wie am 30.10. (0,48 m/s quer zum Anleger, 2,8 m/s Wind) nach 60 s 0,43 m und 0,8° neben dem Platz,
  ohne 0,37 m und 0,0°. `Schwimmer.leinen()` für die Prüfung.
- **Lotse, Abfahrt (SF144 A, Befund L5d7 4: im Bogen 22 m am Kutter vorbei):** rückwärts mit der Führung auf der Linie, bis
  die Mitte `DREH_AB` = 80 m draußen ist (höchstens `V_RUECK` = 1,5 m/s), stoppen; mit der Führung auf der Stelle drehen
  (Linie durch die Mitte, die sich mit `DREH_RATE` = 1,5 °/s auf den Weg dreht, der kürzere Weg) bis auf `DREH_FERTIG` = 8°;
  dann Fahrt [G]. Die Phase nennt die Stufe („FAEHRT_AB, rückwärts“, „…, dreht“, „…, in Fahrt“). Für den Plan `ABLEGEN_S`
  = 240 s bis in Fahrt. Im Container 148 m (mit Strom und Wind) und 171 m (ohne) am Kutter vorbei.
- **Lotse, Anlauf (Befund L5d7 5: kurz vor `BREMS_AB` sprang der Hebel auf 1):** Fehler gegen den Plan über mindestens
  `GLATT_S` = 20 s ausgeglichen: Soll = `FAHRT_PLAN` + (s − `BREMS_AB` − `FAHRT_PLAN` · Rest) / max(Rest, 20 s); gleich
  wie bisher, solange der Rest über 20 s liegt. Im Bremsen nicht schneller als das Soll an `BREMS_AB` (`bremsSoll`).
- `physics.auftrieb.Festmacher.Fuehrung.vSoll`: Geschwindigkeit des Punkts quer zur Linie, gegen die der Dämpfer wirkt
  (für die sich drehende Linie; sonst 0, wie bisher).
- Prüfung: `demo.SchwimmerCheck --teil 2` (Prüfstein 16 in den ersten 20 s, SF143 A; Attest `S7b4_Schwimmer_teil2.md`),
  `demo.KielwasserCheck` (genau 4 Leinen; Abstand zum Kutter bei der Abfahrt ≥ 50 m [G]; Stufen der Abfahrt). Lauf L5d8.

## Seit S7e1 (Seegang Werkbuch 99, SF146–SF149 A): Seezeichen, Lichter, Fähre nach Foto

- **Pricken (SF149 A):** 26 Pricken aus OSM/OpenSeaMap (`seamark:type = beacon_lateral`) im Umkreis von 5 km um den
  Anleger, in `HalligHooge\src\szenen\Hooge.pricken`; in `Hooge.szenen` `schwimmer.pricken.datei` und in allen Szenarien
  `pricken` in der Liste der Schwimmer. Zeile der Datei: `pricke Ost Nord Seite Länge(m) Name`, Seite `backbord` oder
  `steuerbord` nach `seamark:beacon_lateral:category`. Die Flotte rechnet sie nicht: Ein Schwimmer mit `steht` wird einmal
  gestellt (Fuß `PRICKE_IM_GRUND` = 1 m im Grund, an trockenem Grund ohne Gelände `PRICKE_GRUND` = −1 m NHN [G]), hat keine
  Punkte auf der Karte (`see_koerper`) und keinen Schritt im Faden; im Bild ist er ein Körper wie die anderen.
- **Lichter (SF148 A):** `Lichter` gibt je Art die Lichter im Körper (in Fahrt nach KVR Regel 23 mit Sektoren, vor Anker
  Regel 30, am Anleger Decksbeleuchtung, Tonnen mit Kennung nach `core.szene.Kennung`, je Tonne ein eigener Versatz im Takt
  [G]). `Flotte.lichter(t, lagen)` legt sie je Bild in die Welt (11 double je Licht, wie `render.schwimmer.Lichterbild`);
  der Zustand kommt aus der Phase des Lotsen (WEG: keine; fest oder LIEGT: fest; sonst in Fahrt) oder aus `fest`
  (anker, anleger, kette). Die `Buehne` legt `Lichterbild` als letzten Bildteil nach Himmel und Kimmung an und setzt die
  Lichter in `flotteBild` und `standbild`.
- **Plan (SF146 A):** `Lotse.ANLEGEN_S` von 180 auf 95 s (L5d8: ab `BREMS_AB` 92 s bis fest; im Container jetzt +0,5 s).
- **Fähre nach Foto (SF151 A, nur das Netz):** `physics.auftrieb.Schiffbau` – Portal, Fahrgastraum, Brücke, Mast, Schornstein.
- Prüfung: `demo.SeezeichenCheck` (Prüfsteine 30, 31, 33: Lagen gegen den OSM-Auszug ± 1 m, Kennungen gegen die Tags, das
  Feuer von SA 13 bei Nacht an und aus, Bilder bei Nacht und in der Dämmerung, Attest `S7e_Seezeichen.md`), dazu
  `demo.SchwimmerCheck --teil 4` und `demo.KielwasserCheck --teil 4`. Lauf L5e1.

## Seit S7e2 (Seegang Werkbuch 101, SF150 A, SF151 A): Adler-Express, Treibgut, Eiderenten – Flotte 0.8

- **Lotse je Schiff:** Fahrt, Plan, Anlegen, Hebel und kleinster Pegel aus `schwimmer.<n>.lotse = fahrt … plan … anlegen …
  hebel … pegel …` (sonst die Werte der Fähre); ein eigener Weg der Abfahrt (`route_ab`); der Fahrplan in Ortszeit
  (`fahrplan = 13:20 18:30 Europe/Berlin`, mit Sommerzeit); `sturm weg`: bei Sturm nicht da statt am Anleger. Hinter dem
  Ende des Wegs fährt das Schiff geradeaus weiter (bis S7e1 kreiste es um den letzten Punkt). Berichtigt: das Soll der Fahrt
  beim Kommen nahm seit dem Umbau die eigene Fahrt statt der Fahrt des Plans als Grenze (im Container gefunden).
- **WEG:** Ein Schiff, das der Lotse WEG sagt, rechnet die Flotte nicht, es fragt keine Punkte der Karte und liegt im Bild
  10 km unter dem Wasser; kommt es, beginnt es am Ort des Plans.
- **Adler-Express** (`Hooge.szenen`): Wittdün → Hooge an 13:20, Hooge → Nordstrand ab 18:30 Ortszeit (Fahrplan 2026), Weg
  aus OSM (`Hooge.route_adler_wittduen`, `Hooge.route_adler_nordstrand`), Liegeplatz am Pierkopf, 12,3 m/s (24 kn), Plan
  11 m/s, Anlegen 175 s [im Container geeicht], Raster der Karte 6 × 4 m (R20).
- **Treibgut** (`Hooge.treibgut`, Zeilen `art Ost Nord Farbe frei Name`): 12 Stück, frei; **Eiderenten** (`Hooge.enten`,
  Zeilen `trupp Ost Nord Anzahl Halbmesser Name`): zwei Trupps, 19 Tiere; `Ente` hält paddelnd ihren Platz, taucht alle
  30–60 s für 20–40 s, ist bei Hs über 1 m, Sturm und unter 0,4 m Wasser nicht da [G]. Beide ohne Punkte der Karte
  (Datei 100, R20): sie schwimmen auf dem Pegel des Bilds mit dem Strom, ohne die Wellen. `Umwelt.hs` gibt das Hs bei den
  Enten (die Bühne aus der Seegangsoptik).
- Prüfung: `demo.KielwasserCheck --teil 5` (Adler-Express kommt an: pünktlich ± 2 min, nicht gekentert, 4 Leinen; Attest
  `S7e_Adler.md`), `demo.SeezeichenCheck` mit Bildern des Adler-Express bei Tag und Nacht, der Enten und des Treibguts.
  Läufe L5e2 und L5e3 (Abnahme S7).
