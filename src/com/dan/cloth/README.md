# com.dan.cloth – Tuch

Tuch im Wind: Flaggen, Wimpel, Segel, Markisen der Strandkörbe, Sonnenschirme und Handtücher als Netz aus Punkten mit Massen, gerechnet nach XPBD.

Der Kern braucht nur Java 21. Der Ordner `gl/` rechnet und zeichnet mit JOGL (OpenGL 4.3, Compute), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/cloth/`.

## Was es kann

- **XPBD in kleinen Schritten** (Macklin, Müller, Chentanez 2016; Macklin u. a. 2019): je Unterschritt Kräfte, Vorhersage, Nebenbedingungen, Geschwindigkeit aus der Verschiebung. Nebenbedingungen sind Abstände zu den 12 Nachbarn im Raster: Dehnung (u, v), Scherung (Diagonalen) und Biegung (über zwei Maschen), jede mit eigener Nachgiebigkeit α (α̃ = α / Δt²).
  - Gelöst wird nach Gauß-Seidel in zwölf Farben. In einer Farbe teilt sich keine Nebenbedingung einen Punkt mit einer anderen, deshalb rechnet die Grafikkarte sie gleichzeitig und kommt zum selben Ergebnis wie der Prozessor.
  - Dazu kommen Fernhalter (Kim, Tsai, Bridson 2012): kein Punkt entfernt sich weiter vom nächsten festen Punkt als in Ruhe. Damit bleibt Fahnentuch auch bei Sturm und großen Schritten undehnbar.
- **Wind als Druck je Dreieck:** ½·ρ·c_N·A·(v_rel·n)·|v_rel·n| auf die Normale, Reibung ½·ρ·c_T·A·|v_t|·v_t längs. Der Wind kommt über die eigene Schnittstelle `Wind`, Böen über die Zeit.
- **Flattern** [G]: eine Welle quer zum Tuch läuft mit 0,6·U von der Befestigung zur freien Kante. Ihre Frequenz folgt der Strouhal-Zahl, f = St·U / A mit St 0,2 und A = 0,2 der Länge in v. Zur freien Kante hin wird sie stärker. Ab etwa 14 m/s meldet `onFlap` hörbares Schlagen.
- **Ränder:**
  - fest (`pinned`, Liek an Mast, Stange; die App bewegt die Punkte mit `moveTo`, `translatePinned`)
  - an Leinen (`tie`, Schot oder Fall, zieht nur straff)
  - lose
  - Reffen und Bergen mit `reef(anteil)`
- **Presets:** `flag`, `beachFlag` (Schleswig-Holstein 1,5 × 1 m auf 6 m Stange), `pennant` (Wimpel), `awning` (Markise eines Strandkorbs an zwei Leinen), `parasol` (Sonnenschirm), `towel` (Handtuch auf dem Grund), `strip` (Band zwischen zwei Punkten).
- **Nässe:** schwerer, bis das Zweieinhalbfache; in `gl/` dunkler und glänzend.
- **Abfragen:**
  - `pinForce()`: Kraft an den festen Punkten, aus dem Impuls, den die Nebenbedingungen nehmen
  - `normals()`, `triangles()`
  - `maxStretch()`, `energy()`, `meanSpeed()`, `flutterFrequency(U)`
- **Grafikkarte (F9 A):** `gl/ClothGpu` rechnet dasselbe im Compute-Shader `cloth.comp`. Je Tuch läuft eine Arbeitsgruppe mit bis 1024 Punkten im geteilten Speicher, alle Unterschritte eines Bildes in einem Aufruf. `selfCheck` vergleicht mit dem Prozessor.
- **Darstellung:** `gl/ClothGl` zeichnet beidseitig mit Durchscheinen im Gegenlicht, Nässe und bis zu drei Farbbändern. Die Lage kommt direkt aus den Puffern der Grafikkarte oder vom Prozessor.
- **Paketvertrag:** offene Felder, `seed` (Phase des Flatterns), Qualitätsstufen `LOW` bis `ULTRA` (8, 12, 20, 32 Unterschritte je 1/60 s), `strength()`.

## Benutzung

```java
Cloth f = Cloth.beachFlag(0, 2.1, 0);
f.wind = (x, y, z, t, o) -> { o[0] = 7; o[1] = 0; o[2] = 0; };   // aus der App, etwa com.dan.wind
f.onFlap = (x, y, z, s) -> klang.flap(x, y, z, s);
f.step(dt);                                                    // Prozessor

// Grafikkarte, viele Tücher:
ClothGpu gpu = new ClothGpu();  gpu.init(gl);  gpu.setCloths(gl, tuecher);
ClothGl  bild = new ClothGl();  bild.init(gl); bild.setCloths(gl, tuecher);
// je Bild:
gpu.step(gl, dt);
bild.draw(gl, view, light, gpu);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Cloth` | Kern: Netz, Ruhelängen, Massen, Fernhalter, Presets, Schritt nach XPBD, Wind und Flattern, Ränder, Abfragen |
| `gl/ClothGpu`, `gl/cloth.comp` | derselbe Schritt auf der Grafikkarte, eine Arbeitsgruppe je Tuch; Vergleich mit dem Prozessor |
| `gl/ClothGl`, `gl/cloth.vert`, `gl/cloth.frag` | Darstellung aus Speicherpuffern: beidseitig, Durchscheinen, Nässe, Farbbänder |
| `demo/ClothGlDemo` | Reihe Strandflaggen an einer Promenade mit Wimpeln und Markise, Regler für Wind, Böen, Richtung, Nässe, Sonne; Grafikkarte oder Prozessor; `--bild datei.png` ohne Fenster |
| `demo/ClothDemo` | dieselbe Reihe von der Seite auf dem Prozessor (Java2D) |
| `demo/ClothCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Prüfung

`ClothCheck` prüft:

- Wiederholbarkeit, feste Punkte, Dehnung unter 3 %
- die Flagge weht aus, hängt ohne Wind und kommt zur Ruhe
- die Stange trägt das Gewicht (auf 5 %)
- Durchhang eines Bands gegen die Kettenlinie (auf 4 %)
- Schwingungsdauer eines Pendels gegen 2π·√(L/g) (auf 2 %)
- die Energie bleibt beschränkt
- die Frequenz des Flatterns gegen St·U/A (auf 25 %), der Ausschlag, das Schlagen und der Zug an der Stange
- Bft 10 mit Böen in Schritten zu 1/20 s
- alle Presets
- Leine, Bergen und wieder Setzen, eine fahrende Stange

## Grenzen

- Das Flattern ist eine vorgegebene Welle nach der Strouhal-Zahl, keine Strömungsrechnung. Der Wind gilt je Tuch an seinem Bezugspunkt (Mitte der festen Punkte); große Segel bekommen mit `sail` einen Wind je Höhe.
- Keine Selbstkollision und keine Kollision mit Körpern außer dem Grund. Auf der Grafikkarte ist der Grund eine Ebene in der Höhe am Bezugspunkt.
- Das Netz ist ein Raster nu × nv. Dreiecksformen (Wimpel) entstehen durch zusammenrückende Punkte mit eigenen Ruhelängen (`setRest`).
- Auf der Grafikkarte höchstens 1024 Punkte je Tuch. Ereignisse (`onFlap`) und `pinForce` gibt es nur bei der Rechnung auf dem Prozessor oder nach `readBack`.
- Schatten auf und von Tüchern zeichnet `gl/` nicht.

## Seit welcher Phase

- **Timmendorf Phase 1:** Hülle nach `muster`.
- **Timmendorf Phase 6:** XPBD mit Dehnung, Scherung, Biegung, Fernhaltern, Wind je Dreieck, Flattern, Leinen, Reffen, Nässe; Rechnung auf der Grafikkarte (F9 A); Darstellung mit Durchscheinen; neue Presets Wimpel, Sonnenschirm, Band; `ClothGlDemo`.
- **Folgt:** Phase 7 und 8 (`sail` nutzt das Tuch nahe der Kamera, F10).
