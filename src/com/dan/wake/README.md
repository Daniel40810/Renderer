# com.dan.wake – Kielwasser

Kielwasser und Bugwelle: das Kelvin-Muster hinter jedem Boot und Schiff, der Schaum im Kielwasser und der Schwell der Fähren, der Minuten später am Strand aufläuft.

Der Kern braucht nur Java 21. Der Ordner `gl/` rechnet mit JOGL (GL 4.3, Compute), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/wake/`.

**Stand: Timmendorf Phase 8** (Hülle seit Phase 1).

## Was es kann

- **Das Muster nach der stationären Phase** (Lighthill 1978): ein Druckpunkt am Bug, ein Sog am Heck. Für jeden Punkt hinter der Quelle die beiden Kelvin-Wellen (quer laufend und schräg) mit Wellenzahl k = g/(V² cos² θ), geschlossen aus tan α = tan θ / (1 + 2 tan² θ).
  - Höhe nimmt mit 1/√r ab, am Rand des Keils (19,47°) mit r^(−1/3) (Airy), dahinter klingt sie ab.
  - Die Breite des Druckfelds dämpft kurze Wellen; die Überlagerung von Bug und Heck lässt bei hoher Froude-Zahl die langen Wellen ausfallen, der sichtbare Keil wird enger wie bei Rabaud und Moisy 2013 (ohne eigene Formel).
- **Kurven:** jede Quelle schreibt die Spur ihres Bugs; ein Punkt wird in Spurkoordinaten (Weg hinter dem Bug, Abstand quer, Alter) gerechnet. Das Kielwasser folgt einer Wende.
- **Höhe** in einer Bootslänge Abstand 0,12 · T · Fr² / (0,08 + Fr²) [G]: Fähre 200 m bei 18 kn rund 0,3 m in 100 m, J/70 2 cm, Optimist knapp 1 cm.
- **Flaches Wasser:** über Fr_h ≈ 0,7 bricht der Keil zusammen [G].
- **Schaum:** Streifen hinter dem Heck, der breiter wird und vergeht (Motorschiffe mit Schraube 2,5 min, Segelboote 25 s, nur bei Fahrt).
- **Schwell am Ufer:** `watch(linie)` und `onArrival` melden, wann der Rand des Keils einer Fähre den Strand erreicht (Höhe als Amplitude).
- **Kacheln:** `tile(x0, z0, size, n, out)` rechnet Höhe und Schaum auf dem Prozessor genau wie der Compute-Shader; `gl/WakeGl` rechnet zwei Kacheln um die Kamera (nah 256 m, weit 2 048 m, RG16F) für die See.
- **Grundgrößen** wie in der Hülle: `KELVIN_DEG`, `transverseWavelength`, `froude`, `depthFroude`, `wakeAngleDeg` (Rabaud und Moisy, zum Vergleich), `inWedge`.
- **Presets:** `luebeckerBucht()` (12 m), `harbour()`.
- **Paketvertrag:** offene Felder, `seed`, Qualitätsstufen `LOW` bis `ULTRA` (Kacheln 256² bis 768²), `strength()`.

## Benutzung

```java
Wake w = Wake.luebeckerBucht();
Wake.Source f = w.add("Fähre", x, z, kurs, 9.3, 200, 30, 6.5, true);   // Länge, Breite, Tiefgang, Schraube
w.moveTo(f, x, z, kurs, fahrt);         // die App fährt die Quelle (oder Autopilot wie in der Hülle)
w.watch(strandpunkte);
w.onArrival = (q, sx, sz, h, t) -> meldung(q.name + " am Strand");
w.step(dt);
double h = w.heightAt(px, pz, t), schaum = w.foamAt(px, pz);
// auf der Grafikkarte, für com.dan.ocean
wakeGl.update(gl, w, kameraX, kameraZ);
oceanLight.kielTexture = wakeGl.texture();
oceanLight.kielRect0 = wakeGl.rect(0);
oceanLight.kielRect1 = wakeGl.rect(1);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Wake` | Kern: Quellen mit Spur, Kelvin-Wellen, Höhe, Hüllkurve, Schaum, Schwell am Ufer, Kacheln |
| `gl/WakeGl` | Compute-Shader `wake.comp`: zwei Kacheln um die Kamera als Textur-Array RG16F (Höhe, Schaum) |
| `demo/WakeDemo` | von oben: ein Boot, das alle 40 s wendet, und eine Fähre mit ihrem Schwell; Regler für Länge und Fahrt; `--bild datei.png` |
| `demo/WakeCheck` | Prüfung ohne Fenster (23 Prüfungen), endet mit „Alles in Ordnung.“ |

## Prüfung

- Druckpunkt: abseits der Achse die stärkste Welle bei 19,45° (Kelvin 19,47°), außerhalb nichts; am Rand ×0,50 auf achtfachen Abstand (r^−⅓), auf der Achse ×0,50 auf vierfachen (r^−½)
- Muster aus Bug und Heck: bei Fr 0,4 endet der Keil bei 20,5°; bei Fr 1 und 2 liegt die stärkste Welle unter 8,5° und 4° (Rabaud und Moisy: 11,3° und 5,7°)
- Querwellen hinter der J/70 bei 3 m/s: 5,80 m (2πV²/g = 5,76 m)
- Höhen von Fähre, J/70 und Optimist in Bereichen [G]; im flachen Wasser niedriger
- Spur folgt der Wende; Schraubenwasser der Fähre; Kachel gleich Punkt auf 1e-9 m; wiederholbar
- Schwell einer Fähre mit 18 kn erreicht ein Ufer 3 km quer nach der Zeit, die der Keil verlangt (auf 15 %)

## Grenzen

- Fernfeld der stationären Phase: dicht am Rumpf (unter einer halben Länge) stimmt die Form nur grob; Bug- und Heckwelle direkt am Rumpf zeichnet die See als Schaumsaum der Rumpfmaske.
- Die Spur nimmt den nächsten Abschnitt; in engen Kurven gibt es an der Innenseite Kanten.
- Die Höhe ist an Beobachtungen angepasst [G], nicht aus der Rumpfform gerechnet; der Schwell am Strand wird nicht über den Grund gerechnet (das macht `surf`, sobald die Brandung in der Szene ist).
- Bugspray und Spritzwasser fehlen.

## Seit welcher Phase

- **Timmendorf Phase 1:** Hülle nach `muster`.
- **Timmendorf Phase 8:** Kelvin-Muster aus Bug und Heck nach der stationären Phase, Spur in Kurven, Schaum, flaches Wasser, Schwell am Ufer, Kacheln auf dem Prozessor und `gl/WakeGl` (Compute) als Eingang für `ocean`.
