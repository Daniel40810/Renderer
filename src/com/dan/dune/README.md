# com.dan.dune – Flugsand und Dünen

Das Package rechnet, wann der Wind Sand aufnimmt, wie viel er trägt und wo er ihn wieder ablegt: auf dem trockenen oberen Strand, über den Dünenkämmen, im Windschatten, im Strandhafer. Es zeigt den Flug als Körner, Sandfahnen und Kammfahnen und die Windrippel auf dem Sand. Es braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/dune/`.

**Seit welcher Phase:** Hülle seit Sylt Phase 1, Flugsand seit Phase 8 (F11 A), **Stand: Sylt Phase 9 (dune II – Höhenfeld, Dünenwanderung im Zeitraffer, Strandhafer, Sandfangzäune, F12 A).**

## Was es kann

- **Wind am Boden:** u* = κ·U / ln(z/z₀) aus dem Wind in 10 m (`ustarFromWind`, `windFromUstar`, `windAt` für das Profil).
- **Schwelle nach Bagnold (1941):** u*t = A·√((ρs − ρ)/ρ · g·d), A = 0,1; bei 0,3 mm 0,25 m/s, also etwa 7 m/s in 10 m. Gegen Shao und Lu (2000) 12–18 % niedriger (Bagnold kennt keine Haftung der Körner).
- **Feuchte nach Belly (1964):** Faktor 1,8 + 0,6·log10(W in %); bei 1–5 % 3–21 % über Fécan u. a. (1999). Der nasse Auflauf fliegt nicht, nach Regen der ganze Strand nicht.
- **Gebunden (seit Sylt Phase 9a):** ab `boundMoisture` (6 % Wasser) fliegt der Sand gar nicht mehr (`wetFactor` unendlich; Belly allein ließe nassen Sand bei Sturm fliegen) [G nach McKenna Neuman und Nickling 1989].
- **Bewuchs nach Raupach (1992):** u*t,veg / u*t = √((1 − mσλ)(1 + mβλ)) mit β = 90, σ = 1, m = 0,5 [G].
- **Hang nach Dyer (1986):** u*t(θ) = u*t·√(cos θ + sin θ / tan φ), φ = 34°.
- **Fluss:** q = C·√(d/D)·ρ/g·u*²·(u* − u*t) mit C = 1,8 (Dünensand), D = 0,25 mm; weit über der Schwelle Bagnolds u*³.
- **Feld (`FluxField`):** ein Rechteck in Windrichtung. Je Zeile die Hülle des Windschattens (14° vom Kamm), die Störung der Schubspannung durch den Boden nach Kroy, Sauermann und Herrmann (2002), τ̂ = A·(ℋ[h'] + B·h') mit A = 3, B = 0,2 [G], also Beschleunigung etwas luvwärts des Kamms und Bremsung am Fuß; der Fluss läuft dem Sättigungsfluss mit der Sättigungslänge ℓ = 2,2·(ρs/ρ)·d nach (Andreotti u. a. 2010; 1,4 m bei 0,3 mm). Daraus die Ablagerung (`depositAt`, `depositRateAt`), wo der Wind den Sand nicht mehr tragen kann. Der Sand bleibt erhalten (`massBalance`). Ereignisse `onDeposit` und `onScour` über `Deposit`.
- **Saltation als Körner (`Grains`):** Absprung im Mittel 1,2·u* senkrecht, 40° flach [G], Flug mit Schwere und Widerstand (Schiller und Naumann) im logarithmischen Wind; jedes Korn steht für ein Gewicht, so dass die Körner genau den Fluss tragen. Mittlere Höhe des Flusses 2–6 cm, Sprungweite 10–70 cm. **Sandfahnen:** Böen als gestrecktes Rauschen (3 m lang, 0,45 m breit, σ = 0,22), mit dem Wind ziehend (`gust`, `fluxGusty`): nahe der Schwelle fliegt Sand nur in den Fahnen. **Kammfahnen:** Körner, die über den Kamm springen, fliegen weit ins Lee; ein Anteil Feinkorn (3 %, 0,1 mm) wird von der Turbulenz gehoben.
- **Windrippel (`Ripples`):** λ = λ₀·(1 + (u* : u*t − 1)) mit λ₀ = 7,5 cm (Andreotti u. a. 2006: linear in u*), Index λ/H = 18, Bildung in 5 min, ab u* : u*t = 3,5 eingeebnet, Wandern c = 0,25·q/(ρ_b·H). Im Nassen und im Windschatten verwischen sie langsam.
- **Bild (`gl/DuneGl`):** Boden mit Rippeln (Profil Luv lang, Lee kurz, Kämme gröber; Wandern als zweilagige Flusskarte), Schleier der Sandfahnen, nasser Sand, Bewuchs; Körner als kurze Striche.
- **Staub (seit Sylt Phase 12, F15 A):** `Grains.burst(x, y, z, n, Tempo)` lässt Feinkorn aus einem Punkt aufsteigen (wo ein Stück Kliff zerfällt); es treibt danach wie der Schweb mit dem Wind.
- **Presets:** Beaufort und `sylt()` (0,3 mm, Westwind 7 m/s); **Qualität** bestimmt die Zahl der Körner (4 000 bis 50 000).

## Seit Phase 9: Höhenfeld, Zeitraffer, Strandhafer, Zäune

- **Höhenfeld des losen Sands (`SandBed`):** Raster in Windrichtung, je Zelle fester Grund und loser Sand als ganze Zahl von Platten (5 cm). Ist selbst `Ground` (Oberfläche) und `Cover` (λ des Strandhafers), so rechnen `FluxField` und `Ripples` darauf. Dazu der Strand, den das Meer hält (`sea`).
- **Dünenwanderung im Zeitraffer (`Werner`):** Plattenmodell nach Werner (1995): Abheben, wo Sand liegt, nicht im Windschatten (15°), nicht nass; Sprung um 4 Zellen; Liegenbleiben 0,6 auf Sand, 0,4 auf festem Grund, 0,5 auf nassem Sand, 1 im Windschatten; Lawinen bis zum Schüttwinkel (34°, mit Platten von 5 cm auf 1 m: 33°). Zeit aus dem Fluss nach Bagnold: je Zelle r = q/(ρ_b·h·D) Abhebungen je Sekunde, der Wind weht so stark 8 % der Zeit [G]. Eine Düne der Höhe H wandert c = q·Anteil/(ρ_b·H) (Prüfung: 2,7 m/Jahr bei 9 m und 14 m/s). Die Tide setzt den nassen Strand und den Strand, den das Meer hält, zurück; der Sand bleibt in der Bilanz (herein, hinaus).
- **Strandhafer:** Bedeckung und Übersandung je Zelle (`SandBed.grow`): wächst bis 1 m im Jahr nach oben durch den Sand, mit Sand kräftiger (Maun 1998) [G], vergreist ohne Sand, stirbt, wenn mehr Sand kommt, als er durchwachsen kann, und wenn Abtrag die Wurzeln freilegt; breitet sich mit Ausläufern aus. Im Plattenmodell hält er Sand fest (90 %) und fängt ihn (80 %) [G nach Nield und Baas 2008]. `onDeposit` meldet übersandeten Strandhafer, `onScour` freigelegten. Für die Halme in `reed` gibt `burialAt` die Übersandung.
- **Sandfangzäune (`SandBed.Fence`):** Reisig mit Höhe und Durchlässigkeit; der Zaun hält den Sand, dahinter bleibt bis zur zehnfachen Höhe mehr liegen; er sandet ein, bis er voll ist.
- **Zeitraffer im eigenen Faden (`TimeLapse`):** Jahre je Sekunde, Kopie für die Darstellung (geglättet), `ground()` und `cover()` aus der Kopie.

## Benutzung

```java
Dune d = Dune.sylt();
d.wind = new Dune.Wind() { … };                    // aus com.dan.wind über die App
d.moisture = (x, z) -> naesse(x, z);               // Massenanteil, z. B. aus dem Auflauf
d.cover = (x, z) -> hafer.lambda(x, z);
d.ground = (x, z) -> gelaende.height(x, z);
FluxField f = new FluxField(d, cx, cz, 300, 200, 0.5).compute();   // neu, wenn Wind oder Nässe sich ändern
Ripples r = new Ripples(f).settle();
Grains g = new Grains(d);
// je Bild:
g.step(dt, f, auge.x, auge.z);
r.step(dt);
duneGl.draw(gl, view, light, f, r, g, zeit);       // oder nur drawGrains, wenn die App den Boden selbst zeichnet

// seit Phase 9: Zeitraffer
SandBed bed = new SandBed(d, cx, cz, 300, 200, 1.0, 0.05, festerGrund, oberflaeche);
bed.plant(hafer, 1);
bed.fence(new SandBed.Fence(x0, z0, x1, z1, 1.0, 0.5));
TimeLapse lapse = new TimeLapse(new Werner(bed));
lapse.start();                                     // eigener Faden
d.ground = lapse.ground();                         // die Darstellung liest die Kopie
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Dune` | Kern: Profil, Schwelle (Feuchte, Bewuchs, Hang), Fluss, Böen, Parameter, Presets, Schnittstellen |
| `FluxField` | Feld in Windrichtung: Beschleunigung, Windschatten, Sättigungslänge, Ablagerung, Bilanz |
| `Grains` | Saltation als Körner: Absprung, Flug, Fahnen, Schweb |
| `Ripples` | Windrippel je Zelle: Wellenlänge, Höhe, Wandern |
| `SandBed` | Höhenfeld des losen Sands, Strandhafer, Zäune, Strand des Meeres (seit Phase 9) |
| `Werner` | Plattenmodell: Dünenwanderung, Lawinen, Bewuchs, Zäune, Tide, Bilanz (seit Phase 9) |
| `TimeLapse` | Zeitraffer im eigenen Faden mit Kopie für die Darstellung (seit Phase 9) |
| `gl/DuneGl` | Boden mit Rippeln (`dune.vert`, `dune.frag`), Körner (`grains.vert`, `grains.frag`) |
| `demo/Strand` | Bühne der Vorführung: Strand mit Auflauf, Vordüne mit Strandhafer und Windrissen |
| `demo/DuneDemo` | Fenster (Tasten 1–3, Z Zeitraffer, F Zäune, R neu; Regler Wind, Richtung, Regen, Böen, Jahre/s); `--bild datei.png`, `--profil datei.png`, `--zeitraffer datei.png [jahre]` (die letzten beiden ohne Grafikkarte) |
| `demo/DuneCheck` | Prüfung ohne Fenster (56 Prüfungen), endet mit „Alles in Ordnung.“ |

## Grenzen

- In Echtzeit ändert sich der Boden nicht; die Ablagerung aus `FluxField` ist eine Rate. Den Boden bewegt nur der Zeitraffer, und der kennt keine Beschleunigung über dem Kamm (Werner: nur Windschatten), der Wind weht dort aus einer Richtung (in Windrichtung des Rasters).
- Platten von 5 cm auf 1 m: Hänge in Stufen, Schüttwinkel 33°; die Darstellung glättet.
- Der Wind gilt im Feld aus einer Richtung; Ablenkung längs der Düne fehlt. Der Windschatten ist eine Hülle, keine Strömung.
- Die Körner prallen nicht ab (kein Splash), sie landen und verschwinden; der Absprung ist aus dem Fluss gesetzt.
- Die Rippel im Bild haben eine Wellenlänge je Feld; die Werte je Zelle gehen nur in Höhe und Wandern ein.
- Kroy A, B, Absprung, Böen und die Rippelzahlen sind gesetzt [G].
