# com.dan.wind – Wind über Gelände

Ein Windfeld über einem Höhenfeld. Der Wind der Lage (Stärke, Richtung, Schichtung) kommt von außen, das Gelände über die kleine Schnittstelle `WindField.Ground`, die Rauigkeit über `WindField.Roughness`. Das Package kennt keinen Berg und keine App. Es braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`.

Neu seit Brocken Phase 1 (F6 A) als Hülle. **Stand: Kern (Brocken Phase 4).** Das Feld ist massenkonsistent gelöst; ohne Lösung bleibt die Näherung der Hülle als Rückfall.

Zum Herauskopieren reicht `src/com/dan/wind/`.

## Was es kann

- **Profil über dem Grund:** `profile(h, z0)` nach Monin-Obukhov (Businger-Dyer): logarithmisch bei neutraler Schichtung, steiler bei stabiler, flacher bei labiler; in `refHeight` (10 m) genau `speed`, über der Grenzschicht `top` (1 500 m) gleichbleibend.
- **Schichtung:** `Stability` `LABIL`, `NEUTRAL`, `STABIL`, `INVERSION` (Obukhov-Länge −200 m, ∞, 300 m, 80 m [G]).
- **Wind am Ort:** `windAt(x, y, z, t)` gibt {u Ost, v oben, w Süd}; am Hang entlang dem Gelände geneigt (am Luvhang steigt, im Lee sinkt die Luft; der Anteil nimmt über 300 m ab [G]). Unter dem Grund 0.
- **Turbulenz und Böen:** `turbulenceAt` ≈ 1/ln(h/z0), nach der Schichtung gewichtet; `gustAt` ist ein Rauschen, das mit dem Wind zieht (Taylor), im Mittel gleich dem Wind, Spitzen bis `peakFactor` · I.
- **Gitter (Brocken F7 A):** `grid(xMin, zMin, xMax, zMax)` legt ein geländefolgendes Gitter mit zur Höhe hin gestreckten Schichten an (erste Schicht 10 m, Streckung so, dass die Schichten genau bis `top` reichen); `sample(grid, t)` liefert u, v, w und Böe je Zelle für eine 3D-Textur.
- **Gelöstes Feld (Brocken Phase 4):** `solve(xMin, zMin, xMax, zMax)` rechnet auf einer Kopie ein massenkonsistentes Feld nach MATHEW (Sherman 1978, wie WindNinja) und gibt eine unveränderliche `WindSolution` zurück; mit `field.solution = s` nutzen `windAt`, `coldPoolAt` und `sample` das Feld.
  - Gitter in σ-Koordinaten (Schichten folgen unten dem Gelände, oben flach; Obergrenze höchster Grund + `top`).
  - Startfeld: Profil × Lee-Faktor (Winkel zur Kante luvwärts nach Winstral und Marks 2002, Reichweite `shelterReach` 400 m) × Kaltluft (Mulde gegen Umgebung im Umkreis `coldRadius` 1 500 m, bei stabiler Luft und Nacht) + Hangwind (Prandtl-Strahl, 25 m hoch; nachts hinab, tags hinauf, aus `heating`).
  - Anpassung ohne Quellen: Lagrange-Faktor λ, u = u0 + a·∂λ/∂x, w = w0 + b·∂λ/∂ζ; das Verhältnis b/a folgt der Schichtung (labil 1,2 · neutral 0,7 · stabil 0,35 · Inversion 0,12 [G]; neutral abgestimmt auf die Faustformel von Walmsley, Taylor und Salmon 1989). Gelöst mit vorkonditionierten konjugierten Gradienten (Spalten nach Thomas, parallel).
  - Diagnose: Schritte, Rest, Fehler der Masse je Zelle (Ziel unter 1 %, erreicht etwa 0,001 %), Rechenzeit; `describe()`.
  - `scaled(k)` passt die Stärke an (etwa an den Wind der Lage am Gipfel), ohne neu zu rechnen.
- **Presets:** `calm()`, `breeze()`, `westlage()` (Bft 6 aus 260°), `inversion()` (Bft 2 aus Ost, Inversion), `sturm()`, `beaufort(n)`; `beaufortOf(v)` als Umkehrung.
- **Offene Werte, seed, Qualitätsstufen:** alle Felder öffentlich; gleicher `seed`, gleiche Böen; `Quality.LOW` bis `ULTRA` setzen Zelle (100, 50, 50, 25 m) und Schichten (16, 30, 30, 40).

## Benutzung

```java
WindField w = WindField.westlage();
w.ground = gelaende::height;                 // eigene Schnittstelle, kein fremdes Package
w.roughness = (x, z) -> wald(x, z) ? 0.8 : 0.03;
double[] v = w.windAt(x, y, z, t);           // m/s
double boe = w.gustAt(x, y, z, t);

WindField.Grid g = w.grid(-6700, -5460, 6000, 6440);   // 254 × 238 × 30 bei 50 m
WindGl gl = new WindGl();
gl.upload(gl4, g, w.sample(g, t));           // nur bei Wechsel der Lage
// je Bild, im Shader: #include "/com/dan/wind/gl/wind.glsl"  →  vec4 windSample(vec2 xz, float hUeberGrund)
gl.bind(gl4, program, 9);
```

Andere Packages nehmen das Feld über ihre eigene kleine Schnittstelle an (Brocken F8 A), die App verdrahtet mit `field::windAt`.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `WindField` | Eingänge und Abfragen: Profil, Schichtung, Hang, Turbulenz, Böen, Gitter, Abtastung, Presets, `solve` |
| `MassSolver` | (im Package) Startfeld, Lee, Kaltluft, Hangwind, Anpassung ohne Quellen |
| `WindSolution` | gelöstes Feld, unveränderlich: `at`, `speedAt`, `groundAt`, `shelterAt`, `coldAt`, Diagnose |
| `gl/WindGl` | 3D-Textur RGBA16F (Wind und Böe je Zelle), Uniforms für den Baustein |
| `gl/wind.glsl` | Baustein für fremde Shader: `windSample(xz, h)` mit der Umkehrung der Streckung |
| `demo/WindDemo` | Testgelände (Kuppe, Kante, Tal): Karte in 10 m und Schnitt mit Pfeilen und Rauchfäden; `--bild datei.png [bft] [schicht] [erwärmung]` |
| `demo/WindCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Grenzen

- **Massenkonsistent, nicht dynamisch:** keine Impulsgleichung. Ablösung im Lee und Rückströmung entstehen nicht aus der Rechnung, sondern aus dem Lee-Faktor im Startfeld; Wellen über dem Berg (Föhn, Rotoren) fehlen.
- Die Beschleunigung über der Kuppe hängt am Verhältnis b/a; neutral liegt sie innerhalb von ± 35 % an der Faustformel (geprüft in `WindCheck`).
- Kaltluft ist ein Abbild der Mulden, kein Abfluss über die Zeit; der Hangwind ist ein fester Strahl, kein Wärmehaushalt.
- Die Böen sind ein Muster mit der richtigen Stärke, keine aufgelöste Turbulenz.
- Rechenzeit: etwa 250 × 240 × 30 Zellen in 5 bis 12 s auf zwei bis acht Kernen, Speicher etwa 200 MB; darum im eigenen Faden.
