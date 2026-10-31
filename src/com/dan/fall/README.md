# com.dan.fall – Wasserfälle

**Stand: Engine (Phase 6 von Geiranger), Lauf über Fels seit Phase 7.** In Geiranger zeichnet es seit Phase 7 die Sieben Schwestern, Friaren, Brudesløret und Rinnsale nach Regen.

Fallendes Wasser für jede Gebirgsszene: eine Kante mit Abfluss, der Strahl im freien Fall mit Luftreibung, sein Zerfall in Strähnen und Tropfenpakete, der Schleier im Wind und im Gegenlicht, der Aufprall auf Wasser oder Fels mit Tosbecken, Nebel und Ringwellen, Eis bei Frost. Das Package kennt keinen Fjord: Kante, Boden (als Abfrage), Wasserspiegel, Wind und Temperatur kommen von außen. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` FStyle. Zum Herauskopieren reicht `src/com/dan/fall/`; in Phase 18 geht es nach `Renderer.jar`.

## Was es kann

- **Abriss:** über der Kante die Grenztiefe h<sub>c</sub> = ∛(q²/g); der Strahl ist am Rand 0,715 h<sub>c</sub> dick und fließt mit q / Dicke ab. Die Kante teilt sich nach Breite und Stufe in Strähnen mit eigenem Anteil am Abfluss.
- **Freier Fall mit Luftreibung in geschlossener Form** (kein Schrittverfahren): senkrecht y = (v<sub>t</sub>²/g) · ln cosh(g t / v<sub>t</sub>), auch mit Startgeschwindigkeit über der Endgeschwindigkeit; waagerecht nähert sich das Wasser dem Wind. Die Prüfung rechnet dieselbe Bahn mit Runge-Kutta nach: Abweichung 0,0 mm.
- **Zerfall:** der Strahl (Endgeschwindigkeit 60 m/s [G]) hält oben zusammen und zerfällt nach L<sub>b</sub> = 110 · √Dicke (m) [G] in Tropfenpakete (16 m/s ± 30 % [G]), die seitlich streuen. Daraus wird der Schleier nach unten breiter, und der Wind biegt ihn.
- **Aufprall** auf Wasser oder Boden: Ort, Zeit, Geschwindigkeit; Schaumringe im Tosbecken, Nebel, der aufsteigt und mit dem Wind zieht.
- **Eis:** ab 0 °C friert der Rand, bei −15 °C steht der Fall [G]; weniger Wasser, kein Nebel, die Muster stehen still.
- **Ausgänge für andere Packages:** `ripples()` (Ringwellen für `ocean`: Ort, Höhe, Periode), `wetLine(step)` (nasse Linie für `cliff`), `soundLevel(dist)` (dB für `soundscape`, rund ein Millionstel der Fallleistung als Schall [G]), `mist()` (Nebel 0..1).
- **Lauf über Fels** (seit Phase 7, `overRock`): Wände aus einem groben Höhenfeld stehen selten senkrecht; ein freier Fall von der Kante landete gleich im Hang. Mit `overRock` rechnet jede Strähne ihren Weg über die Abfrage `ground`: frei durch die Luft (Schwerkraft und quadratische Reibung gegen den Wind, gekoppelt, Strahl bis zum Zerfall, dann Tropfenpakete), Aufschlag (je steiler, desto mehr geht verloren: `impactKeep`), Gleiten mit Reibung nach dem Andruck (`slideTerminal`: am Hang mit θ gleitet das Wasser mit slideTerminal · √tan θ, an der senkrechten Wand streift es nur) und Abspringen, wo der Fels schneller wegfällt als die Wurfbahn. `lateralFreedom` hält Strähnen auf ihrer Linie (klein) oder lässt sie in Rinnen zusammenlaufen (1). Der Weg liegt als Tabelle in gleichen Zeitschritten vor (`Strand.path`, 192 Proben: x, y, z, Geschwindigkeit, negativ auf dem Fels); Prozessor (`course`) und Grafikkarte (`fallCourse`) lesen sie gleich. An einer senkrechten Wand fällt der Lauf über Fels so lange wie die geschlossene Form (± 0,1 %).
- **Feste Zahl der Strähnen** (`strandCount`, auf jeder Stufe) und ihre Breite (`strandShare`); `copy()` für Szenen, die einen Fall im Hintergrund neu rechnen und austauschen.
- **Gleicher Seed, gleiches Bild:** Strähnen, Bündel und Nebel kommen aus einem Zufall mit Startwert.
- **Stufen:** `LOW` (bis 5 Strähnen, 1 500 Bündel), `MEDIUM` (7, 4 000), `HIGH` (9, 9 000), `ULTRA` (12, 20 000).

## Benutzung

```java
Fall f = new Fall();
f.lipA = new double[]{x0, 250, z0};  f.lipB = new double[]{x1, 250, z1};
f.headingDeg = 180;                   // Abfluss nach Süden
f.discharge = 2.0;                    // m³/s
f.windSpeed = 3; f.windFromDeg = 270;
f.waterLevel = 0;  f.ground = terrain::height;
f.update();                           // nach jeder Änderung

FallGl gl = new FallGl();
gl.init(gl4);
gl.draw(gl4, f, view, light, sekunden);   // in das gebundene Ziel, HDR, Tiefentest ohne Tiefe zu schreiben

// Lauf über Fels (seit Phase 7): dieselbe Kante auf einer Wand, die nicht senkrecht steht
f.overRock = true;
f.ground = wand::height;                  // die Wand, wie die Szene sie zeichnet
f.update();
```

Ein `FallGl` zeichnet beliebig viele Fälle: die Programme gibt es einmal, Puffer, Weg und Zeitmessung je Fall (`forget(gl, f)` gibt einen ersetzten frei). Fälle außerhalb des Blickfelds fallen weg; in der Ferne zeichnet es weniger, dafür größere Bündel (`lodNear`, `lodMin`, `lodShare`).

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Fall` | Eingänge, Strähnen, Bündel, Bahn in geschlossener Form, Aufprall, Ausgänge |
| `gl/fall.glsl` | Bahn (`fallParcel`) Zeile für Zeile wie `Fall.parcel`, Licht mit Vorwärtsstreuung |
| `gl/FallGl` | Strähnen (`strand.*`), Schleier (`veil.*`), Nebel (`mist.vert`), Tosbecken (`pool.*`), Boden der Vorführung (`ground.*`), Selbsttest (`check.*`); Weg als Textur, viele Fälle, Blicktest, Stufen nach Abstand |
| `demo/DemoCliff` | Wand von 250 m über einem See, der Fall der Vorführung; seit Phase 7 die Stufenwand (senkrecht auf 130 m, Absatz, 63° ans Wasser) für den Lauf über Fels |
| `demo/FallScene`, `demo/FallDemo` | Vorführung mit Reglern; `--bild datei.png [breite]` rechnet sechs Ansichten |
| `demo/FallCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Prüfung

- `com.dan.fall.demo.FallDemo`: Regler für Abfluss (0,05 bis 50 m³/s), Wind, Windrichtung, Temperatur, Sonnenhöhe und -richtung, Stufe; „Lauf über Fels (Stufenwand)“; „Von vorn“, „Am Fuß“, „Von der Seite“. Unten die Zeit des Falls auf der Grafikkarte.
- `FallDemo --bild datei.png`: von vorn, am Fuß, von der Seite, Wind 8 m/s, Gegenlicht, Frost.
- `FallCheck`: Bahn gegen Runge-Kutta, ohne Luft genau √(2h/g), Abriss gegen die Grenztiefe, Fallzeit über 250 m (16,1 s statt 7,1 s frei), Stetigkeit am Zerfall, Schleier wird breiter (40 m oben, 58 m unten), Wind biegt (oben 0,2 m, unten 66 m), Frost, Ausgänge, gleicher Seed. Seit Phase 7 der Lauf über Fels: senkrechte Wand wie die geschlossene Form, Rampe 60° mit slideTerminal · √tan θ, nie im Fels, Stufenwand (frei 122 m, Aufschläge, Gleiten, im See), Tabelle genau gelesen, gleicher Weg, sieben Strähnen auf jeder Stufe, nasse Linie aus dem Weg.
- `FallGl.selfCheck`: 64 Bahnpunkte auf der Grafikkarte gegen den Prozessor (Mesa: ± 0 mm; Lauf über Fels: ± 0,1 mm, auch an den Sieben Schwestern und Friaren in Geiranger).

## Grenzen

- Strahl und Pakete sind voneinander unabhängig. Im Lauf über Fels folgt jede Strähne einer Linie (ein Punkt mit Breite); wie sich Wasser über eine Platte breit auffächert, zeigt nur das breitere Band und die Gischt.
- Der Weg folgt der Abfrage `ground`. Ein Höhenfeld kennt keine Überhänge: eine Nase, die die Szene zeichnet, kennt `fall` nur, wenn die Kante an ihre Front gesetzt wird (so in Geiranger).
- Nischen hinter dem Schleier und die nasse Wand zeichnet `cliff`; `fall` gibt nur die Linie.
- Zerfallshöhe, Endgeschwindigkeiten, Streuung, Gleiten, Aufschlag und Schall sind gesetzt [G], nach Fotos und Faustwerten.
