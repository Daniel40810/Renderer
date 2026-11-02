# com.dan.landslide – Bergsturz, Lawine, Murgang

Ein Strom aus Fels, Eis, Schnee oder Schutt als tiefengemittelte Schicht auf einem Gitter: Savage-Hutter mit Erddruck, Reibung nach Voellmy (Coulomb plus Turbulenz), finite Volumen mit HLL. Er läuft über beliebiges Gelände, bleibt liegen, wo der Hang flacher ist als seine Reibung, geht ins Wasser und meldet, was über die Uferlinie kommt – die Schnittstelle zur Welle.

Der Kern braucht nur Java 21; der Ordner `demo/` zusätzlich `FStyle.jar`. Es gibt noch keinen Ordner `gl/`: Die Rechnung auf der Grafikkarte (Compute Shader) kommt mit der Welle in LituyaBay1958 Phase 11. Zum Herauskopieren reicht `src/com/dan/landslide/`.

**Stand: LituyaBay1958 Phase 9** (Werkbuch 6.2, 9.2).

## Was es kann

- **Gleichungen:** ∂h/∂t + ∇·(h u) = 0; ∂(h u)/∂t + ∇·(h u⊗u + ½ k g′ h² I) = − g′ h ∇b − S, waagerecht projiziert mit g′ = g cos² α (ein Block gleitet reibungsfrei mit g sin α längs des Hangs). S = (μ g cos α + g |u_s|² / (ξ h_n)) in Richtung der Bewegung.
- **Erddruck (`Material.earthPressure`):** Savage und Hutter, k = 2/cos²φ · [1 ∓ √(1 − cos²φ/cos²δ)] − 1, aktiv beim Auseinanderlaufen, passiv beim Stauchen; `earthPressure = false` gibt k = 1 (Voellmy wie RAMMS), `pressure = 0` den starren Block.
- **Verfahren:** HLL mit trockenen Zuständen, Hangabtrieb zentral, Schritt nach CFL, h ≥ 0; Turbulenz implizit, Coulomb als Bremse bis zum Stillstand. Ränder: was hinaus will, geht hinaus (`lost()`), sonst Wand. Parallel über die Zeilen; gleicher Lauf, gleiches Ergebnis.
- **Wasser:** `water(Spiegel)` – unter Wasser Auftrieb (g · (1 − ρw/ρ) im untergetauchten Anteil) und Formwiderstand (`waterDrag`). Je Schritt ein `Entry` an der Uferlinie: Rate (m³/s), Volumen bisher, Geschwindigkeit (nach Masse), Mächtigkeit senkrecht und normal zum Hang, Breite. `entrySummary()` fasst zusammen.
- **Masse:** `addPlate` (Platte auf dem Hang mit Form (1 − |ũ|^p)(1 − |ṽ|^p)), `scaleTo(V)`, `inject(x, z, V, vx, vz)` für die Übergabe aus einem Bruch (Volumen und Impuls), Felder der größten Mächtigkeit und Geschwindigkeit.
- **Auswertung:** Schwerpunkt, mittlere Geschwindigkeit, Bewegungsenergie, `settled()` (Energie unter 0,1 % der Spitze), Fahrböschung nach HEIM32 mit Abrisskante, Volumenregel `scheidegger(V)`.
- **Stoffe:** `rock()`, `ice()`, `snow()`, `debris()` [G].
- **Presets (`Presets`):** `gilbertInlet1958` (Hang 40°, Platte 823 × 970 m, bis 92 m, 30,6 Mio. m³, Schwerpunkt 610 m ü. M., Sohle 122 m, Gegenufer 1 342 m; Fels μ 0,06, ξ 8 000 [G] auf die 96–110 m/s nach FRITZ01), `vajont1963` (270 Mio. m³), `spiritLake1980` (0,43 km³). Das Gelände ist gerechnet; das echte gibt die App.
- **Qualität:** LOW 8 m, MEDIUM 4 m, HIGH 2 m (Nahfeld).

## Benutzung

```java
Landslide s = Presets.gilbertInlet1958(Landslide.Quality.MEDIUM);
while (!s.settled()) s.step();                    // Schritt nach CFL
for (Landslide.Entry e : s.entries()) { ... }     // was über die Uferlinie ins Wasser geht
double[] f = s.fahrboeschung(0.5);                // Grad, H, L

// eigenes Gelände, Masse aus einem Bruch
Landslide t = Landslide.over(xa, za, xb, zb, 4).bed(gelaende::hoehe).water(0);
t.inject(x, z, volumen, vx, vz);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Landslide` | Kern: Gitter, Gleichungen, HLL, Reibung, Wasser, Uferlinie, Auswertung |
| `Material` | Dichte, μ, ξ, φ; Erddruckbeiwerte; Presets |
| `Presets` | Gilbert Inlet 1958, Vajont 1963, Spirit Lake 1980 auf gerechnetem Gelände |
| `demo/LandslideDemo` | Draufsicht mit Mächtigkeit, Schnitt mit Geschwindigkeit, Zufluss s(t); Fenster mit Zeit, Fall und Qualität; `--bild datei.png [t] [preset] [LOW\|MEDIUM\|HIGH]` |
| `demo/LandslideCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Prüfung

`LandslideCheck` prüft:
- **Erddruck:** φ = δ gibt k = (1 + sin²φ)/cos²φ; φ < δ isotrop.
- **Block:** starrer Block auf 30° und 40° gegen v = √(2 g L (sin α − μ cos α)) und g (sin α − μ cos α) t, auf 1 %.
- **Coulomb:** eine Schicht steht am Hang, der flacher ist als δ, und beschleunigt am steileren nach der Handrechnung.
- **Übergabe:** `inject` bringt Volumen und Impuls genau ins Gitter.
- **Fahrböschung:** die Platte der Lituya trocken über 35° in die Ebene gegen SCHEIDEGGER73 (15,8°) und HEIM32 (15–20°), ±3° [G].
- **Gilbert Inlet:** Volumen bleibt (auf 10⁻⁹), Aufprall nach Rate 96–110 m/s (FRITZ01), alles geht ins Wasser; Dauer und Mächtigkeit des Zuflusses als Hinweis gegen FRITZ01.
- **Vajont, Spirit Lake:** laufen, Volumen bleibt.
- **Wiederholbarkeit:** zwei Läufe gleichen sich Zelle für Zelle.

## Grenzen

- Tiefengemittelt: keine Zerlegung in Blöcke, kein Springen, keine Verdichtung beim Aufprall. Die Platte läuft als Strom länger und dünner über die Uferlinie ein (90 % in rund 15 s, bis gut 30 m) als der kompakte Block im Versuch von FRITZ01 (6,8 s, bis 134 m).
- Erste Ordnung im Raum: an den Rändern des Stroms verschmiert er über einige Zellen; das Ergebnis hängt etwas vom Gitter ab (Aufprall 102 m/s bei 8 m, 105 m/s bei 4 m).
- Wasser nur als Auftrieb und Widerstand; die Rückwirkung der Welle auf den Strom kommt mit Phase 11.
- Dicke Schollen, die als Ganzes gleiten (Vajont: 250 m), fallen im tiefengemittelten Strom zusammen wie ein Damm: rund 50 m/s statt 20–30 m/s, fast unabhängig von μ. Spirit Lake 80–90 m/s gegen 50–70 m/s. Beide Presets sind Grenzfälle, keine Rückrechnung.
- Die Reibungsbeiwerte sind Rückrechnungen [G], keine Messung.
