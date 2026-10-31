# com.dan.surf – die Brandung

Das Package verfolgt die Welle von der offenen See über den Grund bis zum Strand: Sie wird im Flachen höher, an Riffen und Rinnen gebeugt, bricht je nach Hang schäumend oder sich überschlagend und läuft als Weißwasser, Auflauf und Rücklauf auf dem Sand aus. Es braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/surf/`.

**Seit welcher Phase:** neu seit Sylt Phase 1 als Hülle nach `muster` (Sylt Werkbuch, Package-Katalog). **Stand: Sylt Phase 7, der Schwall** (`Swash`: Auflauf und Rücklauf je Querprofil, nasser Sand, Spülsaum, Ereignisse); seit Phase 6 die Brecher (`WaveTrain`, `BreakerShape`, `BreakerMesh`, `FoamField`, `gl/BreakerGl`), seit Phase 5 das Wellenfeld (`WaveField`, `Bars`).

## Was es kann

- **Lineare Wellen:** `wavenumber(T, h)` löst die Dispersion ω² = g·k·tanh(k·h) mit Newton auf 1e-14, `groupVelocity`, `deepLength` (L₀ = g·T²/2π).
- **Shoaling** über die Erhaltung des Energieflusses: `shoaling(T, h)` = √(c_g0 / c_g); Minimum 0,913, im Flachen Green (h^−¼).
- **Brechen nach McCowan:** H ≤ γ·h mit γ = 0,78; `breakingAt`, `heightAt`, `breakPointAlong` (Linie vom Meer zum Ufer).
- **Brechertyp nach Battjes (1974)** aus der Iribarren-Zahl ξ = tan β / √(H/L₀): mit der Höhe draußen Schwall < 0,5 < Sturz < 3,3 < Reflexion; mit der Brechhöhe Schwall < 0,4 < Sturz < 2,0 < Reflexion/Brandung. `typeAt` nimmt den Hang in Laufrichtung der See.
- **Presets:** Beaufort (`calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)`) nach Pierson-Moskowitz, begrenzt [G]; Orte `sylt()`, `ostsee()` (für Thunder), `sockel()` (für Lighthouse).
- **Schnittstellen:** `Bed` (Grund), `Level` (Pegel), `Wind`, `Offshore` (See draußen), Ereignis `Breaking`.
- **Qualitätsstufen** `LOW` bis `ULTRA` (Schritte längs der Linie), `seed` für die Brandungsschwebung ab Phase 6.

## Benutzung

```java
Surf s = Surf.sylt();
s.bed = (x, z) -> gelaende.hoehe(x, z);            // Grund über NHN
s.level = t -> tide.pegel(t);                      // Tide + Windstau
double H = s.heightAt(x, z, t);
Surf.Breaker typ = s.typeAt(x, z, t);              // NONE, SPILLING, PLUNGING, COLLAPSING, SURGING
s.onBreaking = (bx, bz, h, art) -> gischt.ballen(bx, bz, h);
s.breakPointAlong(xSee, zSee, xStrand, zStrand, t);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Surf` | Kern: lineare Wellen, Shoaling, Brechen, Brechertyp, Presets, Schnittstellen |
| `gl/SurfGl` | Hülle: Querschnitt als Vollbild (`surf.vert`, `surf.frag`) |
| `demo/SurfDemo` | Drei Querschnitte (Riff 1:100, Strand 1:30, Hang 1:8) mit Brechpunkt und Typ; `--bild datei.png [bft]` |
| `demo/SurfCheck` | Prüfung ohne Fenster (21 Prüfungen), endet mit „Alles in Ordnung.“ |

## Grenzen

- Die Hülle rechnet je Ort ohne Nachbarn: keine Refraktion, keine Diffraktion, keine Dissipation vor dem Ort, darum ist `heightAt` eine Obergrenze.
- Keine Strömung (Set-up, Längs- und Rückstrom) und keine phasenaufgelöste Welle.
- Der Querschnitt in `gl/` ist nur ein Platzhalter.


## Seit Sylt Phase 5: das Wellenfeld, Riffe und Rinnen

**Was es kann.** `WaveField` rechnet den Seegang über dem Grund auf einem Gitter vor der Küste (F7 A): eine Richtung, eine Periode, Wellenhöhen nach Rayleigh. Von der See zur Küste, Zeile für Zeile: **Refraktion** aus der Wirbelfreiheit des Wellenzahlvektors (auf geraden Tiefenlinien das Gesetz von Snell), **Shoaling** über den Energiefluss E·c_g (im Flachen Green), **Brechen** nach Battjes und Janssen (1978) mit γ nach Battjes und Stive (1985). Daraus der **Wasserstau** (Set-down vor dem Brechen, Set-up in der Brandung) aus den Strahlungsspannungen und die mittlere **Strömung**: Längsstrom aus der Kraft der Brecher und dem Gefälle des Set-up längs, gegen die Reibung am Grund (linear mit den Wellen, quadratisch mit dem Strom), mit seitlicher Vermischung; die Querströmung aus der Masse: Unterstrom unter den Brechern, Rippströmung, wo der Längsstrom in einer Rinne zusammenläuft, jenseits der Brandung breitet sich ihr Kopf aus. `update(t)` rechnet im eigenen Faden neu, wenn sich See, Pegel oder Grund (`bedVersion`) ändern, und tauscht das Ergebnis danach aus. `Surf.field` lässt `heightAt` und `typeAt` aus dem Feld antworten.

`Bars` legt **Riffe und Rinnen** auf einen groben Grund (F10 A) [G]: Rücken mit Trog landseits, in Bögen, alle paar hundert Meter von Rinnen durchbrochen; `storm()` verlagert sie (gleicher Startwert, gleiche Lage).

**Benutzung.**
```java
Surf s = Surf.sylt();
Bars riffe = Bars.sylt();
s.bed = riffe.apply(grund, Bars.Coast.straight(xWasserlinie, z0, 270));   // See im Westen
WaveField feld = new WaveField(s, x0, z0, 90, 1500, 4000, 4);              // Rand zur See bei x0, Blick zur Küste 90°
s.field = feld;
feld.update(t);                                                         // im eigenen Faden, wenn sich etwas geändert hat
double hs = feld.hsAt(x, z), qb = feld.breakingAt(x, z), eta = feld.setupAt(x, z);
double[] strom = feld.currentAt(x, z);                                  // m/s, Welt (x, z)
```

**Prüfung.** `SurfCheck` 34 Prüfungen, neu: Green und Snell im Feld, Sättigung in der Brandung (H_rms/h = 0,46), Set-down nach Longuet-Higgins und Stewart, Set-up auf ebenem Hang nach Bowen, Unterstrom, Längsstrom gegen Komar und Inman, Riffe und Rinnen, Rippströmung in der Rinne, Masse je Zeile, Rechnen im eigenen Faden. **Testbecken:** `SurfBasin` (Fenster mit Reglern für H_s und Richtung, „Sturm“ verlagert die Rinnen) und `SurfBasin --bild datei.png [H_s [Richtung]]`.

**Grenzen.** Eine Richtung und eine Periode statt eines Spektrums; keine Beugung (hinter Buhnen fehlt der Schatten, sie kommen mit Phase 7a), keine Wechselwirkung von Wellen und Strom. Die Strömung ist kein volles Flachwassermodell: der Set-up kommt je Querprofil und wirkt längs nur zum Teil (`feeder`), die Rippströmung folgt aus der Masse. An den seitlichen Rändern des Felds kann eine angeschnittene Rinne zu starke Strömung zeigen; das Feld sollte über den Ausschnitt hinausreichen. Reibung `cf = 0,005` ist an Komar und Inman abgestimmt [G].


## Seit Sylt Phase 6: die Brecher

**Was es kann.** `WaveTrain` ist die phasenaufgelöste Welle je Querprofil (F9 A): einzelne Kämme laufen über den Grund, Höhen nach Rayleigh in Gruppen (Hüllkurve, Mittel der Energie erhalten), Perioden gestreut, der Kamm längs ungleich. Jede Welle wächst nach Green und bricht in jeder Reihe, wo H = γ·h (McCowan); der **Typ** folgt aus ξ_b nach Battjes: Schwall-, Sturz- oder Reflexionsbrecher. `BreakerShape` gibt die Form im Fortschritt des Brechens, immer mit `K` = 128 Punkten, darum passen die Reihen Punkt für Punkt zusammen: beim **Sturzbrecher** fliegt die Lippe im Bogen vor und trifft bei p = 1 den Trog, darunter der **Hohlraum**; beim Schwallbrecher rollt Schaum die Front hinab; beim Reflexionsbrecher kippt der Fuß. Danach die **Walze** (Weißwasser) mit der Geschwindigkeit eines Wassersprungs, ihre Höhe sinkt auf γ_Walze·h. `FoamField` ist der **Schaumteppich**: dicht (τ 6 s), zerfällt zu Schlieren, die nachleuchten (τ 40 s), und treibt zur Küste. Die **Brandungsschwebung** hebt und senkt den Wasserstand am Ufer gegen die Gruppen. Beim Aufschlag der Lippe meldet `onImpact` Ort, Menge und Fallgeschwindigkeit – die Szene macht daraus mit `spray` Gischt (ein Package bindet kein anderes, Paketvertrag Punkt 9). `BreakerMesh` baut daraus je Bild das Netz (Reihen parallel), `gl/BreakerGl` zeichnet es mit Fresnel, Farbe nach Tiefe, durchscheinender Lippe und Schaum. Fern genügen Schaum und Normalen des Wellenfelds; das Netz ist für die Nähe.

**Benutzung.**
```java
WaveTrain zug = new WaveTrain(48, x0, xEnde, 60);      // 48 Reihen auf 60 m Kamm
zug.profile = x -> grund(x);  zug.hs = 1;  zug.tp = 8;
zug.onImpact = (x, y, z, menge, v) -> gischt.impact(x, z, y, menge, v);
BreakerMesh netz = new BreakerMesh();
zug.step(dt);  netz.build(zug, xEnde);                  // je Bild
breakerGl.upload(gl, 0, netz);  breakerGl.draw(gl, 0, blick, licht, zug.time(), nassBis);
```

**Prüfung.** `SurfCheck` 53 Prüfungen, neu: Rayleigh (H_rms, H_1/3) und Gruppen, je Becken Brechen bei H/h = γ und ein Typ, Aufschläge der Lippe nur beim Sturzbrecher und jeder gemeldet, Geschwindigkeit der Walze, Überhang je Typ, die Lippe trifft den Trog, Schaum zerfällt und leuchtet nach, Schwebung am Ufer, Netz ohne NaN, gleicher Samen gleiche Brandung. **Vorführung:** `SurfBreakers` – drei Becken nebeneinander (Riff 1:100 Schwall, Strand 1:20 Sturz, Hang 1:5 Reflexion), Regler für H_s, Periode, Tempo, Zeiten für Prozessor und Grafik; `SurfBreakers --bild datei.png` zeichnet sechs Bilder (brechend und nach 90 s).

**Grenzen.** Die Formen sind gezeichnet [G], keine Strömungsrechnung; die Welle läuft je Becken senkrecht auf die Küste (Richtung aus dem Wellenfeld kommt mit der Szene). Rücklauf und Auflauf fehlen (Phase 7, F8 A). Das Netz wird auf dem Prozessor gebaut, je Becken einige Millisekunden.


## Seit Sylt Phase 7: Auflauf und Rücklauf

**Was es kann.** `Swash` rechnet je Reihe eines `WaveTrain` ein eigenes Querprofil (F8 A) mit den Flachwassergleichungen in einer Dimension: finite Volumen, HLL-Flüsse, hydrostatische Rekonstruktion nach Audusse u. a. (2004), sodass ein ruhender See auf jedem Grund ruhig bleibt und Zellen sauber trocken fallen, MUSCL mit minmod, Heun in der Zeit, Reibung halbimplizit (c_f 0,012 [G]), Versickern in ungesättigten Sand. Der **Rand zur See** liegt, wo das Wasser 1,25·H_s tief ist, und wandert mit dem Pegel. Herein kommt die Welle aus dem Wellenzug (gebrochen als Walze, höchstens 0,78·h), dazu Ruhewasser, Brandungsschwebung (verstärkt, [G] an Stockdon abgestimmt) und der Set-up seewärts nach Bowen; hinaus geht der Rücklauf ohne Spiegelung (Riemann-Invarianten). Ohne Wellenzug (`step(dt, null)`) ist der Rand eine Wand – für Versuche.

**Sand.** Je Zelle ein **Wasserfilm**, der glänzt und versickert (hoch über dem Grundwasser in Sekunden, nahe daran eine Minute und mehr), die **Nässe** der obersten Schicht, die nach dem Wetter trocknet (`Weather` wie in `mudflat`: Halbwertszeit 12 min bei Sonne und Wind, 4 h im Schatten, bei Regen nicht) und über dem Grundwasser im Kapillarsaum nie ganz trocken wird; das **Grundwasser** im Strand steht über dem Ruhewasser (Anteil des mittleren Auflaufs). **Schaum** fährt mit dem Wasser und bleibt als **Blasen** liegen. Der **Spülsaum**: Tang und Schill legt jeder Auflauf an seinem höchsten Punkt ab, kräftige Strömung nimmt sie wieder auf.

**Ereignisse** (`onSwash`): `RUNUP` am höchsten Punkt jedes Auflaufs (Ort, Höhe über Ruhewasser) – für Sanderlinge, Spülsaum, Klang; `COLLISION`, wo der Rücklauf auf die nächste Walze trifft (Gischt, Klang); `BORE`, wenn eine Walze den Rand passiert. Die Szene koppelt daraus `spray` und `mudflat` (ein Package bindet kein anderes, Paketvertrag Punkt 9).

**Benutzung.**
```java
WaveTrain zug = …;                                         // wie seit Phase 6
Swash schwall = new Swash(zug, xSeewaerts, xLandEnde, 0.5); // Zellen 0,5 m; xSeewaerts tief genug für den Rand
schwall.weather = new Swash.Weather(sonne, wind, luftC, regen);
schwall.onSwash = (x, y, z, art, s) -> { … };
zug.step(dt);  schwall.step(dt, zug);                       // je Bild
netz.build(zug, xEnde, schwall);                             // Wasser bis zum Rand aus den Brechern, danach aus dem Schwall
BreakerMesh sand = BreakerMesh.bed(zug, schwall, 120);       // einmal; je Bild sand.updateBed(schwall) und breakerGl.refreshBed(…)
breakerGl.draw(gl, 0, blick, licht, zeit, Double.NaN);       // NaN: Nässe je Ecke aus dem Schwall
double r2 = schwall.r2(), soll = Swash.stockdon(h0, tp, hang);
```

**Prüfung.** `SurfCheck` 72 Prüfungen, neu 19: ruhender See auf welligem Grund mit trockenem Ufer (|u| ≈ 1e-15), Dammbruch nach Ritter (4/9 am Damm, Profil auf 0,1 m, Masse auf 1e-16), Auflauf gegen Stockdon u. a. (2006) auf 1:20 (H_s 1 und 0,5 m), 1:30 und 1:100 (76–109 %), eine Spitze je Welle und jede gemeldet, Stöße, Massenbilanz am offenen Rand, Spülsaum oben zwischen Median und R_1%, Film versickert, Halbwertszeit der Nässe, unter dem Grundwasser bleibt es nass, gleicher Samen gleicher Schwall, Netz mit Schwall, Budget. **Vorführungen:** `SurfSwash` (ohne Grafikkarte: Querschnitt, Strand von oben, Spur der Spitze; `--bild datei.png [sekunden]`) und `SurfBreakers` (Taste S: Blick auf den Strand).

**Grenzen.** Jedes Querprofil für sich, ohne Austausch längs: Zungen und Strandhörner entstehen nur aus der Ungleichheit der Kämme (in der Szene zusätzlich aus der schrägen See). Der Rand nimmt die Höhe der Walzen und die Schwebung genähert [G]; die Schwebung ist auf Stockdon abgestimmt, nicht gerechnet. Auf sehr steilem Hang (1:5, Kliffuß) liegt der Auflauf unter Stockdon, dessen Daten dort nicht mehr gelten – der Kliffuß kommt mit Phase 12. Kein Sedimenttransport (der Strand ändert sich nicht).


## Seit Sylt Phase 7a: seitliche Glättung im Wellenfeld

`WaveField.spread` (0..0,5, Vorgabe 0 wie bisher) glättet den Energiefluss je Zeile seitlich, genähert für Beugung und die Streuung der Richtungen einer echten See [G]. Auf echtem Grund (DGM1 und BAW vor Sylt, dazu Riffe und Rinnen) bündelt ein Feld mit einer Richtung und einer Periode die Energie sonst zu Brennlinien und lässt dahinter Schatten, in denen die Brandung fast ausbleibt. Sylt setzt `spread = 0,5` und `kvSmooth = 0,5` und glättet den Grund über ±12 m. Grenze bleibt: Die Querströmung aus der Masse wird auf dem echten Grund weit draußen unruhig, darum nimmt Sylt die Strömung nur in der Brandung und bis 120 m dahinter und deckelt sie auf 1,2 m/s.

## Seit Sylt Phase 13: Meeresleuchten

- `Bioluminescence`: das blaugrüne Leuchten von *Noctiluca scintillans* (um 475 nm), wo das Wasser geschert wird. `bloom(Monat, Wassertemperatur)` (Deutsche Bucht: Juni bis September, am stärksten im August, erst über etwa 14 °C), `northSeaTemperature(Monat)`, `luminance(Blüte, Aufwühlung, Verstärkung)` in cd/m² (frisch aufgewühlt bei voller Blüte 6 · 10⁻³ cd/m² [G], dunkeladaptiert gut sichtbar).
- `gl/BreakerGl.Light.glow` (vorbelichtet, 0 aus): im Netz leuchten der stürzende Kamm und frischer Schaum (flackernd), am Spülsaum funkeln die Blasen des Schwalls.
- `SurfCheck` prüft Jahreszeit, Farbe und Leuchtdichte.

## Seit Lighthouse Phase 17: Grund je Reihe (ein Bauwerk im Meer)

**Was es kann.** `WaveTrain.rowProfile` gibt jeder Reihe ihren eigenen Grund (`bed(x, r)`), etwa den Schnitt durch Caisson und Steinschüttung eines Leuchtturms (Lighthouse, `Surf.sockel()`). Die Kämme laufen weiter mit `profile` (der See um das Bauwerk) und bleiben gerade; Shoaling, Brechen, Brechertyp, Walze, Aufschlag und Netz nehmen die Tiefe der Reihe (`depth(x, r)`, `bed(x, r)`). Wo eine Reihe trocken ist (im Bauwerk), ruht sie; wo sie dahinter wieder tief wird, bleibt die Walze so niedrig wie über der flachsten Stelle (`gammaBore` · kleinste Tiefe). An einer Kante (der Grund springt) bricht keine Welle höher als 1,25 · γ · h [G]. Ohne `rowProfile` ist alles wie bisher (Sylt unverändert).

**Benutzung.**
```java
WaveTrain zug = new WaveTrain(40, 0, 46, 30);
zug.profile = x -> -7;                                  // die See um das Bauwerk
zug.rowProfile = (x, r) -> grund(welt(x, zug.rowY(r)));  // Schnitt je Reihe
zug.slopeBase = 3;                                      // steile, kurze Schüttung
```

**Prüfung.** `SurfCheck` „Bauwerk im Meer“: gleicher Grund je Reihe wie der gemeinsame gibt dieselben Wellen; eine Reihe über einer Schüttung bricht (höchstens 1,25 · γ · h), dahinter bleibt sie niedrig, die freie Reihe läuft weiter.

**Grenzen.** Keine Beugung und keine Reflexion am Bauwerk: die Reihen wissen nichts voneinander, das Lee bekommt keine gebeugte Welle, die Wand wirft nichts zurück. Die Szene mischt das Netz mit ihrer See (Lighthouse: das weiße Wasser reitet auf `ocean`) [G].
